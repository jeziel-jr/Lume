package com.nuvio.tv.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbCatalogService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.xtream.CatalogAvailabilityTracker
import com.nuvio.tv.data.xtream.XtreamCatalogAvailabilityService
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.SearchHistoryDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.model.mergeCatalogPage
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.core.util.isUnreleased
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val searchHistoryDataStore: SearchHistoryDataStore,
    private val watchProgressRepository: com.nuvio.tv.domain.repository.WatchProgressRepository,
    private val watchedSeriesStateHolder: com.nuvio.tv.data.local.WatchedSeriesStateHolder,
    val posterOptions: com.nuvio.tv.ui.components.posteroptions.PosterOptionsController,
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val tmdbCatalogService: TmdbCatalogService,
    private val tmdbService: TmdbService,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val xtreamCatalogAvailabilityService: XtreamCatalogAvailabilityService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Saved focus state for restoring scroll/focus position after returning from details. */
    var savedFocusRowKey: String? = null
    var savedFocusItemIndex: Int = -1
    var savedRowScrollPositions: Map<String, Pair<Int, Int>> = emptyMap()
    var hasSavedSearchFocus: Boolean = false

    private val _watchedMovieIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedMovieIds: StateFlow<Set<String>> = _watchedMovieIds.asStateFlow()
    val watchedSeriesIds: StateFlow<Set<String>> = watchedSeriesStateHolder.fullyWatchedSeriesIds

    private var activeSearchJobs: List<Job> = emptyList()
    private var discoverJob: Job? = null
    private var suggestionJob: Job? = null
    private val availabilityTracker = CatalogAvailabilityTracker(
        scope = viewModelScope,
        service = xtreamCatalogAvailabilityService,
    )
    private var revealBatchAfterNextDiscoverFetch = false
    private var hideUnreleasedContent = false

    private companion object {
        const val DISCOVER_INITIAL_LIMIT = 30
        const val DISCOVER_SHOW_MORE_BATCH = 30
        const val SUGGESTION_DEBOUNCE_MS = 150L
        const val MAX_SUGGESTIONS = 8
        const val MAX_RECENT_SEARCHES = 8
    }

    init {
        posterOptions.bind(viewModelScope)
        viewModelScope.launch {
            watchProgressRepository.observeWatchedMovieIds()
                .collect { ids -> _watchedMovieIds.value = ids }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.discoverLocation.distinctUntilChanged().collectLatest { location ->
                _uiState.update { it.copy(discoverLocation = location) }
                if (location == DiscoverLocation.OFF) {
                    discoverJob?.cancel()
                    discoverJob = null
                    revealBatchAfterNextDiscoverFetch = false
                    _uiState.update {
                        it.copy(
                            discoverInitialized = false,
                            discoverLoading = false,
                            discoverLoadingMore = false,
                            discoverCatalogs = emptyList(),
                            selectedDiscoverType = "movie",
                            selectedDiscoverCatalogKey = null,
                            selectedDiscoverGenre = null,
                            discoverResults = emptyList(),
                            pendingDiscoverResults = emptyList(),
                            discoverHasMore = true,
                            discoverPage = 1
                        )
                    }
                }
            }
        }
        // Combine all layout preference flows into a single collector to reduce coroutine overhead
        viewModelScope.launch {
            combine(
                layoutPreferenceDataStore.posterCardWidthDp,
                layoutPreferenceDataStore.posterLabelsEnabled,
                layoutPreferenceDataStore.catalogAddonNameEnabled,
                layoutPreferenceDataStore.posterCardHeightDp,
                layoutPreferenceDataStore.posterCardCornerRadiusDp
            ) { widthDp, labelsEnabled, addonNameEnabled, heightDp, cornerRadiusDp ->
                LayoutPrefs(widthDp, labelsEnabled, addonNameEnabled, heightDp, cornerRadiusDp)
            }.collectLatest { prefs ->
                _uiState.update {
                    it.copy(
                        posterCardWidthDp = prefs.widthDp,
                        posterLabelsEnabled = prefs.labelsEnabled,
                        catalogAddonNameEnabled = prefs.addonNameEnabled,
                        posterCardHeightDp = prefs.heightDp,
                        posterCardCornerRadiusDp = prefs.cornerRadiusDp
                    )
                }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.catalogTypeSuffixEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(catalogTypeSuffixEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.hideUnreleasedContent.collectLatest { enabled ->
                hideUnreleasedContent = enabled
            }
        }
        viewModelScope.launch {
            searchHistoryDataStore.recentSearches.collectLatest { recent ->
                _uiState.update { it.copy(recentSearches = recent.take(MAX_RECENT_SEARCHES)) }
            }
        }
        viewModelScope.launch {
            uiState
                .map { state ->
                    state.catalogRows.flatMap(CatalogRow::items) +
                        state.discoverResults +
                        state.pendingDiscoverResults
                }
                .distinctUntilChanged()
                .collectLatest(availabilityTracker::submit)
        }
        viewModelScope.launch {
            availabilityTracker.availability.collectLatest { availability ->
                _uiState.update { it.copy(catalogAvailability = availability) }
            }
        }
    }

    private data class LayoutPrefs(
        val widthDp: Int,
        val labelsEnabled: Boolean,
        val addonNameEnabled: Boolean,
        val heightDp: Int,
        val cornerRadiusDp: Int
    )

    fun ensureDiscoverLoaded() {
        val state = _uiState.value
        if (state.discoverLocation == DiscoverLocation.OFF) return
        if (state.discoverInitialized || state.discoverLoading) return
        viewModelScope.launch { loadDiscoverCatalogs() }
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> onQueryChanged(event.query)
            SearchEvent.SubmitSearch -> submitSearch()
            SearchEvent.ClearRecentSearches -> clearRecentSearches()
            is SearchEvent.LoadMoreCatalog -> loadMoreCatalogItems(
                catalogId = event.catalogId,
                addonId = event.addonId,
                type = event.type
            )
            is SearchEvent.SelectDiscoverType -> selectDiscoverType(event.type)
            is SearchEvent.SelectDiscoverCatalog -> selectDiscoverCatalog(event.catalogKey)
            is SearchEvent.SelectDiscoverGenre -> selectDiscoverGenre(event.genre)
            SearchEvent.LoadNextDiscoverResults -> loadNextDiscoverResults()
            SearchEvent.Retry -> performSearch(uiState.value.submittedQuery.ifBlank { uiState.value.query })
        }
    }

    private fun onQueryChanged(query: String) {
        _uiState.update {
            val trimmedInput = query.trim()
            val submitted = it.submittedQuery.trim()
            it.copy(
                query = query,
                error = null,
                isSearching = false,
                catalogRows = if (trimmedInput == submitted) it.catalogRows else emptyList(),
                catalogAvailability = if (trimmedInput == submitted) it.catalogAvailability else emptyMap(),
            )
        }

        // Search is explicit on submit only; stop any in-flight requests while editing.
        activeSearchJobs.forEach { it.cancel() }
        activeSearchJobs = emptyList()

        fetchSuggestions(query.trim())
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()

        if (query.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        // Don't show suggestions if the query already matches the submitted search
        if (query == _uiState.value.submittedQuery.trim() && _uiState.value.catalogRows.isNotEmpty()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        suggestionJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SUGGESTION_DEBOUNCE_MS)
            val suggestions = runCatching { tmdbCatalogService.search(query, "pt-BR") }
                .getOrDefault(emptyList())
                .flatMap { it.items }
                .map { it.name }
                .distinct()
                .take(MAX_SUGGESTIONS)
            if (_uiState.value.query.trim() == query) {
                _uiState.update { it.copy(suggestions = suggestions) }
            }
        }
    }

    private fun submitSearch() {
        performSearch(_uiState.value.query)
    }

    private fun clearRecentSearches() {
        viewModelScope.launch {
            searchHistoryDataStore.clearRecentSearches()
        }
    }

    private fun performSearch(rawQuery: String) {
        val query = rawQuery.trim()
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                submittedQuery = query,
                query = rawQuery,
                suggestions = emptyList()
            )
        }

        if (query.length >= 2) {
            viewModelScope.launch {
                searchHistoryDataStore.saveRecentSearch(query, MAX_RECENT_SEARCHES)
            }
        }

        // Cancel any in-flight work from the previous query.
        activeSearchJobs.forEach { it.cancel() }
        activeSearchJobs = emptyList()

        if (query.length < 2) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    error = null,
                    catalogRows = emptyList(),
                    catalogAvailability = emptyMap(),
                )
            }
            ensureDiscoverLoaded()
            return
        }

        val tmdbJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    error = null,
                    catalogRows = emptyList(),
                    catalogAvailability = emptyMap(),
                )
            }
            runCatching { tmdbCatalogService.search(query, "pt-BR") }
                .onSuccess { rows ->
                    if (uiState.value.submittedQuery.trim() == query) {
                        _uiState.update {
                            it.copy(isSearching = false, catalogRows = rows, error = null)
                        }
                        classifySearchRows(query, rows)
                    }
                }
                .onFailure {
                    if (uiState.value.submittedQuery.trim() == query) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                catalogRows = emptyList(),
                                catalogAvailability = emptyMap(),
                                error = context.getString(R.string.search_error_failed)
                            )
                        }
                    }
                }
        }
        activeSearchJobs = listOf(tmdbJob)
    }

    private fun classifySearchRows(query: String, rows: List<CatalogRow>) {
        if (query.isBlank() || rows.isEmpty()) {
            availabilityTracker.submit(
                _uiState.value.discoverResults + _uiState.value.pendingDiscoverResults
            )
            return
        }
        availabilityTracker.submit(
            rows.flatMap(CatalogRow::items) +
                _uiState.value.discoverResults +
                _uiState.value.pendingDiscoverResults
        )
    }

    private fun loadMoreCatalogItems(catalogId: String, addonId: String, type: String) {
        val state = _uiState.value
        val currentRow = state.catalogRows.firstOrNull { row ->
            row.addonId == addonId && row.apiType == type && row.catalogId == catalogId
        } ?: return

        if (currentRow.isLoading || !currentRow.hasMore) {
            return
        }

        val query = state.submittedQuery.trim()
        if (query.isBlank()) {
            return
        }

        _uiState.update { s ->
            s.copy(
                catalogRows = s.catalogRows.map { row ->
                    if (row.addonId == addonId && row.apiType == type && row.catalogId == catalogId) {
                        row.copy(isLoading = true)
                    } else {
                        row
                    }
                }
            )
        }

        viewModelScope.launch {
            val nextPage = currentRow.currentPage + 1
            runCatching { tmdbCatalogService.search(query, "pt-BR", page = nextPage) }
                .onSuccess { pages ->
                    if (uiState.value.submittedQuery.trim() != query) {
                        _uiState.update { s ->
                            s.copy(
                                catalogRows = s.catalogRows.map { row ->
                                    if (row.addonId == addonId && row.apiType == type && row.catalogId == catalogId) {
                                        row.copy(isLoading = false)
                                    } else {
                                        row
                                    }
                                }
                            )
                        }
                        return@onSuccess
                    }
                    val pageRow = pages.firstOrNull {
                        it.addonId == addonId && it.apiType == type && it.catalogId == catalogId
                    }
                    if (pageRow == null || pageRow.items.isEmpty()) {
                        _uiState.update { s ->
                            s.copy(
                                catalogRows = s.catalogRows.map { row ->
                                    if (row.addonId == addonId && row.apiType == type && row.catalogId == catalogId) {
                                        row.copy(isLoading = false, hasMore = false)
                                    } else {
                                        row
                                    }
                                }
                            )
                        }
                        return@onSuccess
                    }
                    _uiState.update { s ->
                        s.copy(
                            catalogRows = s.catalogRows.map { row ->
                                if (row.addonId == addonId && row.apiType == type && row.catalogId == catalogId) {
                                    row.mergeCatalogPage(pageRow).copy(isLoading = false)
                                } else {
                                    row
                                }
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update { s ->
                        s.copy(
                            catalogRows = s.catalogRows.map { row ->
                                if (row.addonId == addonId && row.apiType == type && row.catalogId == catalogId) {
                                    row.copy(isLoading = false)
                                } else {
                                    row
                                }
                            }
                        )
                    }
                }
        }
    }

    private suspend fun loadDiscoverCatalogs() {
        if (_uiState.value.discoverLocation == DiscoverLocation.OFF) return
        _uiState.update { it.copy(discoverLoading = true) }
        val addons = try {
            addonRepository.getInstalledAddons().first().enabledAddons()
        } catch (_: Exception) {
            _uiState.update { it.copy(discoverInitialized = true, discoverLoading = false) }
            return
        }

        val discoverCatalogs = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog ->
                    val requiresSearch = catalog.supportsExtra("search") &&
                        catalog.extra.any { it.name.equals("search", ignoreCase = true) && it.isRequired }
                    !requiresSearch
                }
                .map { catalog ->
                    val genres = catalog.extra
                        .firstOrNull { it.name.equals("genre", ignoreCase = true) }
                        ?.options
                        .orEmpty()
                    DiscoverCatalog(
                        key = "${addon.id}_${catalog.apiType}_${catalog.id}",
                        addonId = addon.id,
                        addonName = addon.displayName,
                        addonBaseUrl = addon.baseUrl,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        type = catalog.apiType,
                        genres = genres,
                        supportsSkip = catalog.supportsExtra("skip"),
                        skipStep = catalog.skipStep()
                    )
                }
        }

        val availableTypes = discoverCatalogs.map { it.type }.distinct()
        val currentType = _uiState.value.selectedDiscoverType
        val selectedType = if (currentType in availableTypes) currentType else availableTypes.firstOrNull() ?: "movie"
        val selectedCatalog = pickDiscoverCatalog(
            catalogs = discoverCatalogs,
            selectedType = selectedType,
            preferredKey = _uiState.value.selectedDiscoverCatalogKey
        )

        _uiState.update {
            it.copy(
                discoverCatalogs = discoverCatalogs,
                selectedDiscoverType = selectedType,
                selectedDiscoverCatalogKey = selectedCatalog?.key,
                selectedDiscoverGenre = null,
                discoverInitialized = true,
                discoverLoading = false,
                discoverResults = emptyList(),
                pendingDiscoverResults = emptyList(),
                discoverHasMore = true,
                discoverPage = 1
            )
        }
        fetchDiscoverContent(reset = true)
    }

    private fun selectDiscoverType(type: String) {
        val catalogs = _uiState.value.discoverCatalogs
        val selectedCatalog = pickDiscoverCatalog(
            catalogs = catalogs,
            selectedType = type,
            preferredKey = _uiState.value.selectedDiscoverCatalogKey
        )
        _uiState.update {
            it.copy(
                selectedDiscoverType = type,
                selectedDiscoverCatalogKey = selectedCatalog?.key,
                selectedDiscoverGenre = null,
                discoverResults = emptyList(),
                pendingDiscoverResults = emptyList(),
                discoverPage = 1,
                discoverHasMore = true
            )
        }
        fetchDiscoverContent(reset = true)
    }

    private fun selectDiscoverCatalog(catalogKey: String) {
        val catalog = _uiState.value.discoverCatalogs.firstOrNull { it.key == catalogKey } ?: return
        _uiState.update {
            it.copy(
                selectedDiscoverCatalogKey = catalog.key,
                selectedDiscoverType = catalog.type,
                selectedDiscoverGenre = null,
                discoverResults = emptyList(),
                pendingDiscoverResults = emptyList(),
                discoverPage = 1,
                discoverHasMore = true
            )
        }
        fetchDiscoverContent(reset = true)
    }

    private fun selectDiscoverGenre(genre: String?) {
        _uiState.update {
            it.copy(
                selectedDiscoverGenre = genre,
                discoverResults = emptyList(),
                pendingDiscoverResults = emptyList(),
                discoverPage = 1,
                discoverHasMore = true
            )
        }
        fetchDiscoverContent(reset = true)
    }

    private fun loadNextDiscoverResults() {
        if (_uiState.value.pendingDiscoverResults.isNotEmpty()) {
            showMoreDiscoverResults()
        } else {
            revealBatchAfterNextDiscoverFetch = true
            loadMoreDiscoverResults()
        }
    }

    private fun showMoreDiscoverResults() {
        val state = _uiState.value
        val pending = state.pendingDiscoverResults
        if (pending.isEmpty()) return
        if (state.discoverLoadingMore) return
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            _uiState.update { it.copy(discoverLoadingMore = true) }
            val nextBatch = localizeDiscoverItems(pending.take(DISCOVER_SHOW_MORE_BATCH))
            val remaining = pending.drop(DISCOVER_SHOW_MORE_BATCH)
            _uiState.update {
                it.copy(
                    discoverResults = it.discoverResults + nextBatch,
                    pendingDiscoverResults = remaining,
                    discoverLoadingMore = false,
                )
            }
        }
    }

    private fun loadMoreDiscoverResults() {
        val state = _uiState.value
        if (state.query.trim().isNotEmpty()) return
        if (!state.discoverHasMore || state.discoverLoadingMore || state.pendingDiscoverResults.isNotEmpty()) return
        fetchDiscoverContent(reset = false)
    }

    private fun fetchDiscoverContent(reset: Boolean) {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            val state = _uiState.value
            if (state.query.trim().isNotEmpty()) return@launch
            val selectedCatalog = state.discoverCatalogs.firstOrNull { it.key == state.selectedDiscoverCatalogKey }
                ?: return@launch

            if (reset) {
                revealBatchAfterNextDiscoverFetch = false
                _uiState.update {
                    it.copy(
                        discoverLoading = true,
                        discoverResults = emptyList(),
                        pendingDiscoverResults = emptyList(),
                        discoverPage = 1,
                        discoverHasMore = true
                    )
                }
            } else {
                _uiState.update { it.copy(discoverLoadingMore = true) }
            }

            val currentPage = if (reset) 1 else state.discoverPage + 1
            val skip = if (currentPage <= 1) 0 else (currentPage - 1) * selectedCatalog.skipStep
            val visibleCountBeforeRequest = state.discoverResults.size
            val extraArgs = buildMap<String, String> {
                state.selectedDiscoverGenre?.takeIf { it.isNotBlank() }?.let { put("genre", it) }
            }

            catalogRepository.getCatalog(
                addonBaseUrl = selectedCatalog.addonBaseUrl,
                addonId = selectedCatalog.addonId,
                addonName = selectedCatalog.addonName,
                catalogId = selectedCatalog.catalogId,
                catalogName = selectedCatalog.catalogName,
                type = selectedCatalog.type,
                skip = skip,
                skipStep = selectedCatalog.skipStep,
                extraArgs = extraArgs,
                supportsSkip = selectedCatalog.supportsSkip
            ).collect { result ->
                if (_uiState.value.discoverLocation == DiscoverLocation.OFF) return@collect
                when (result) {
                    is NetworkResult.Success -> {
                        val incoming = result.data.items
                        val existing = if (reset) {
                            emptyList()
                        } else {
                            _uiState.value.discoverResults + _uiState.value.pendingDiscoverResults
                        }
                        val existingKeys = existing.asSequence()
                            .map(MetaPreview::discoverIdentityKey)
                            .toSet()
                        val hasNewUniqueIncoming = incoming.any { item ->
                            item.discoverIdentityKey() !in existingKeys
                        }
                        val merged = if (reset) incoming else (existing + incoming)
                        val rawDeduped = merged.distinctBy(MetaPreview::discoverIdentityKey)
                        val deduped = if (hideUnreleasedContent) {
                            val today = LocalDate.now()
                            rawDeduped.filterNot { it.isUnreleased(today) }
                        } else {
                            rawDeduped
                        }
                        val shouldRevealBatch = !reset && revealBatchAfterNextDiscoverFetch
                        val visibleLimit = if (reset) {
                            DISCOVER_INITIAL_LIMIT
                        } else if (shouldRevealBatch) {
                            (visibleCountBeforeRequest + DISCOVER_SHOW_MORE_BATCH)
                                .coerceAtLeast(DISCOVER_INITIAL_LIMIT)
                        } else {
                            visibleCountBeforeRequest.coerceAtLeast(DISCOVER_INITIAL_LIMIT)
                        }
                        val visible = localizeDiscoverItems(deduped.take(visibleLimit))
                        val pending = deduped.drop(visibleLimit)
                        val shouldStopPagination = !reset && !hasNewUniqueIncoming
                        _uiState.update {
                            it.copy(
                                discoverLoading = false,
                                discoverLoadingMore = false,
                                discoverResults = visible,
                                pendingDiscoverResults = pending,
                                discoverHasMore = if (shouldStopPagination) false else result.data.hasMore,
                                discoverPage = if (shouldStopPagination) it.discoverPage else currentPage
                            )
                        }
                        revealBatchAfterNextDiscoverFetch = false
                    }
                    is NetworkResult.Error -> {
                        revealBatchAfterNextDiscoverFetch = false
                        _uiState.update {
                            it.copy(
                                discoverLoading = false,
                                discoverLoadingMore = false,
                                discoverHasMore = false
                            )
                        }
                    }
                    NetworkResult.Loading -> Unit
                }
            }
        }
    }

    private suspend fun localizeDiscoverItems(items: List<MetaPreview>): List<MetaPreview> =
        coroutineScope {
            val language = tmdbSettingsDataStore.settings.first().language.ifBlank { "pt-BR" }
            items.map { item ->
                async {
                    if (!item.id.startsWith("tt", ignoreCase = true)) return@async item
                    val preview = tmdbService.resolveLocalizedPreview(
                        videoId = item.id,
                        mediaType = item.apiType,
                        language = language,
                    ) ?: return@async item
                    item.withLocalizedTmdbPreview(preview)
                }
            }.awaitAll()
        }

    private fun pickDiscoverCatalog(
        catalogs: List<DiscoverCatalog>,
        selectedType: String,
        preferredKey: String?
    ): DiscoverCatalog? {
        val filtered = catalogs.filter { it.type == selectedType }
        return filtered.firstOrNull { it.key == preferredKey } ?: filtered.firstOrNull()
    }
}
