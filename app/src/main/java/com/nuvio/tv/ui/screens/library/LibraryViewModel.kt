package com.nuvio.tv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.cloud.CloudLibraryFile
import com.nuvio.tv.core.cloud.CloudLibraryItem
import com.nuvio.tv.core.cloud.CloudLibraryItemType
import com.nuvio.tv.core.cloud.CloudLibraryPlaybackInfo
import com.nuvio.tv.core.cloud.CloudLibraryPlaybackResult
import com.nuvio.tv.core.cloud.CloudLibraryRepository
import com.nuvio.tv.core.cloud.CloudLibraryUiState
import com.nuvio.tv.core.debrid.DebridProviderCapability
import com.nuvio.tv.core.debrid.DebridProviders
import com.nuvio.tv.core.debrid.supports
import com.nuvio.tv.data.local.DebridSettingsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.data.xtream.CatalogAvailabilityTracker
import com.nuvio.tv.data.xtream.XtreamCatalogAvailabilityService
import com.nuvio.tv.domain.repository.LibraryRepository
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nuvio.tv.R
import java.util.Locale
import javax.inject.Inject

data class LibraryTypeTab(
    val key: String,
    val label: String
) {
    companion object {
        const val ALL_KEY = "__all__"
        val All = LibraryTypeTab(key = ALL_KEY, label = "")
    }
}

enum class LibrarySortOption(
    val key: String,
    val labelResId: Int
) {
    ADDED_DESC("added_desc", R.string.library_sort_added_desc),
    ADDED_ASC("added_asc", R.string.library_sort_added_asc),
    TITLE_ASC("title_asc", R.string.library_sort_title_asc),
    TITLE_DESC("title_desc", R.string.library_sort_title_desc)
}

data class FilterOption(
    val key: String,
    val label: String,
    val count: Int
)

data class LibraryUiState(
    val allItems: List<LibraryEntry> = emptyList(),
    val visibleItems: List<LibraryEntry> = emptyList(),
    val cloudLibrary: CloudLibraryUiState = CloudLibraryUiState(),
    val visibleCloudItems: List<CloudLibraryItem> = emptyList(),
    val availableCloudProviders: List<FilterOption> = emptyList(),
    val availableCloudTypes: List<FilterOption> = emptyList(),
    val selectedCloudProviderId: String? = null,
    val selectedCloudType: CloudLibraryItemType? = null,
    val resolvingCloudFileKey: String? = null,
    val cloudLibrarySettingsVersion: Long = 0L,
    val availableTypeTabs: List<LibraryTypeTab> = emptyList(),
    val availableSortOptions: List<LibrarySortOption> = emptyList(),
    val selectedTypeTab: LibraryTypeTab? = null,
    val selectedSortOption: LibrarySortOption = LibrarySortOption.ADDED_DESC,
    val sortSelectionVersion: Long = 0L,
    val availableGenres: List<FilterOption> = emptyList(),
    val availableYears: List<FilterOption> = emptyList(),
    val selectedGenre: String? = null,
    val selectedYear: String? = null,
    val posterCardWidthDp: Int = 126,
    val posterCardCornerRadiusDp: Int = 12,
    val isLoading: Boolean = true,
    val transientMessage: String? = null
)

private val SAVED_SORT_OPTIONS = listOf(
    LibrarySortOption.ADDED_DESC,
    LibrarySortOption.ADDED_ASC,
    LibrarySortOption.TITLE_ASC,
    LibrarySortOption.TITLE_DESC
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val cloudLibraryRepository: CloudLibraryRepository,
    private val debridSettingsDataStore: DebridSettingsDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val libraryPreferences: LibraryPreferences,
    private val watchProgressRepository: com.nuvio.tv.domain.repository.WatchProgressRepository,
    private val watchedSeriesStateHolder: com.nuvio.tv.data.local.WatchedSeriesStateHolder,
    xtreamCatalogAvailabilityService: XtreamCatalogAvailabilityService,
    val posterOptions: com.nuvio.tv.ui.components.posteroptions.PosterOptionsController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private val availabilityTracker = CatalogAvailabilityTracker(
        scope = viewModelScope,
        service = xtreamCatalogAvailabilityService,
    )
    val catalogAvailability = availabilityTracker.availability

    private val _watchedMovieIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedMovieIds: StateFlow<Set<String>> = _watchedMovieIds.asStateFlow()
    val watchedSeriesIds: StateFlow<Set<String>> = watchedSeriesStateHolder.fullyWatchedSeriesIds

    private var messageClearJob: Job? = null
    private var cloudRefreshJob: Job? = null

    init {
        posterOptions.bind(viewModelScope)
        observeLayoutPreferences()
        observeLibraryData()
        observeCloudLibrarySettings()
        viewModelScope.launch {
            uiState
                .map { state -> state.visibleItems.map(LibraryEntry::toMetaPreview) }
                .distinctUntilChanged()
                .collectLatest(availabilityTracker::submit)
        }
        viewModelScope.launch {
            watchProgressRepository.observeWatchedMovieIds()
                .collect { ids -> _watchedMovieIds.value = ids }
        }
    }

    fun onSelectTypeTab(tab: LibraryTypeTab) {
        _uiState.update { current ->
            val updated = current.copy(selectedTypeTab = tab)
            updated.withVisibleItems()
        }
    }

    fun onSelectGenre(key: String?) {
        _uiState.update { current ->
            val updated = current.copy(selectedGenre = key)
            updated.withVisibleItems()
        }
    }

    fun onSelectYear(key: String?) {
        _uiState.update { current ->
            val updated = current.copy(selectedYear = key)
            updated.withVisibleItems()
        }
    }

    fun onSelectSortOption(option: LibrarySortOption) {
        _uiState.update { current ->
            val nextVersion = if (current.selectedSortOption != option) {
                current.sortSelectionVersion + 1L
            } else {
                current.sortSelectionVersion
            }
            val updated = current.copy(
                selectedSortOption = option,
                sortSelectionVersion = nextVersion
            )
            updated.withVisibleItems()
        }
        viewModelScope.launch { libraryPreferences.setSortOption(option.key) }
    }

    fun ensureCloudLibraryLoaded() {
        val current = _uiState.value.cloudLibrary
        if (current.isLoaded || current.isRefreshing) return
        refreshCloudLibrary()
    }

    fun refreshCloudLibrary() {
        val current = _uiState.value.cloudLibrary
        if (current.isRefreshing) return
        cloudRefreshJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    cloudLibrary = state.cloudLibrary.copy(
                        isRefreshing = true,
                        providers = state.cloudLibrary.providers.map { provider -> provider.copy(isLoading = true, errorMessage = null) }
                    )
                )
            }
            runCatching {
                cloudLibraryRepository.refresh()
            }.onSuccess { refreshed ->
                _uiState.update { state ->
                    state.copy(cloudLibrary = refreshed).withVisibleCloudItems()
                }
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                setError(error.message ?: context.getString(R.string.cloud_library_play_failed))
                _uiState.update { state ->
                    state.copy(cloudLibrary = state.cloudLibrary.copy(isLoaded = true, isRefreshing = false)).withVisibleCloudItems()
                }
            }
        }
    }

    fun onSelectCloudProvider(providerId: String?) {
        _uiState.update { current ->
            current.copy(selectedCloudProviderId = providerId).withVisibleCloudItems()
        }
    }

    fun onSelectCloudType(type: CloudLibraryItemType?) {
        _uiState.update { current ->
            current.copy(selectedCloudType = type).withVisibleCloudItems()
        }
    }

    fun onCloudItemHasNoPlayableFiles() {
        setError(context.getString(R.string.cloud_library_no_playable_files))
    }

    fun resolveCloudPlayback(
        item: CloudLibraryItem,
        file: CloudLibraryFile,
        onResolved: (CloudLibraryPlaybackInfo) -> Unit
    ) {
        val resolveKey = "${item.stableKey}:${file.stableKey}"
        if (_uiState.value.resolvingCloudFileKey != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(resolvingCloudFileKey = resolveKey) }
            val result = cloudLibraryRepository.resolvePlayback(item, file)
            _uiState.update { it.copy(resolvingCloudFileKey = null) }
            when (result) {
                is CloudLibraryPlaybackResult.Success -> {
                    onResolved(
                        CloudLibraryPlaybackInfo(
                            item = item,
                            file = file,
                            url = result.url,
                            filename = result.filename ?: file.name.takeIf { it.isNotBlank() },
                            videoSizeBytes = result.videoSizeBytes ?: file.sizeBytes
                        )
                    )
                }
                CloudLibraryPlaybackResult.MissingCredentials -> {
                    setError(context.getString(R.string.cloud_library_connect_message))
                }
                CloudLibraryPlaybackResult.NotPlayable -> {
                    setError(context.getString(R.string.cloud_library_no_playable_files))
                }
                is CloudLibraryPlaybackResult.Failed -> {
                    setError(result.message ?: context.getString(R.string.cloud_library_play_failed))
                }
            }
        }
    }

    fun onClearTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    private fun observeLibraryData() {
        viewModelScope.launch {
            combine(
                libraryRepository.libraryItems,
                libraryPreferences.sortOption
            ) { items, persistedSortKey ->
                items to persistedSortKey
            }.collectLatest { (items, persistedSortKey) ->
                _uiState.update { current ->
                    val nextSelectedType = current.selectedTypeTab
                        ?: LibraryTypeTab.All.copy(label = context.getString(R.string.library_type_all))
                    val persistedSort = persistedSortKey?.let { key ->
                        LibrarySortOption.entries.find { it.key == key }
                    }
                    val nextSelectedSort = (persistedSort ?: current.selectedSortOption)
                        .takeIf { it in SAVED_SORT_OPTIONS }
                        ?: LibrarySortOption.ADDED_DESC
                    val updated = current.copy(
                        allItems = items,
                        availableSortOptions = SAVED_SORT_OPTIONS,
                        selectedTypeTab = nextSelectedType,
                        selectedSortOption = nextSelectedSort,
                        isLoading = false
                    )
                    updated.withVisibleItems().withVisibleCloudItems()
                }
            }
        }
    }

    private fun observeLayoutPreferences() {
        viewModelScope.launch {
            combine(
                layoutPreferenceDataStore.posterCardWidthDp,
                layoutPreferenceDataStore.posterCardCornerRadiusDp
            ) { widthDp, cornerRadiusDp ->
                widthDp to cornerRadiusDp
            }.collectLatest { (widthDp, cornerRadiusDp) ->
                _uiState.update { current ->
                    if (current.posterCardWidthDp == widthDp &&
                        current.posterCardCornerRadiusDp == cornerRadiusDp
                    ) {
                        current
                    } else {
                        current.copy(
                            posterCardWidthDp = widthDp,
                            posterCardCornerRadiusDp = cornerRadiusDp
                        )
                    }
                }
            }
        }
    }

    private fun observeCloudLibrarySettings() {
        viewModelScope.launch {
            debridSettingsDataStore.settings
                .map { settings ->
                    CloudLibrarySettingsSnapshot(
                        enabled = settings.cloudLibraryEnabled,
                        connectionKeys = DebridProviders.configuredServices(settings)
                            .filter { credential -> credential.provider.supports(DebridProviderCapability.CloudLibrary) }
                            .map { credential -> "${credential.provider.id}:${credential.apiKey}" }
                    )
                }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    if (!snapshot.enabled) {
                        cloudRefreshJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                cloudLibrary = CloudLibraryUiState(isLoaded = true, isEnabled = false),
                                visibleCloudItems = emptyList(),
                                availableCloudProviders = emptyList(),
                                availableCloudTypes = emptyList(),
                                selectedCloudProviderId = null,
                                selectedCloudType = null,
                                resolvingCloudFileKey = null,
                                cloudLibrarySettingsVersion = state.cloudLibrarySettingsVersion + 1L
                            )
                        }
                    } else if (snapshot.connectionKeys.isEmpty()) {
                        cloudRefreshJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                cloudLibrary = CloudLibraryUiState(isLoaded = true, isEnabled = true),
                                visibleCloudItems = emptyList(),
                                availableCloudProviders = emptyList(),
                                availableCloudTypes = emptyList(),
                                selectedCloudProviderId = null,
                                selectedCloudType = null,
                                resolvingCloudFileKey = null,
                                cloudLibrarySettingsVersion = state.cloudLibrarySettingsVersion + 1L
                            )
                        }
                    } else {
                        cloudRefreshJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                cloudLibrary = CloudLibraryUiState(isLoaded = false, isEnabled = true),
                                visibleCloudItems = emptyList(),
                                availableCloudProviders = emptyList(),
                                availableCloudTypes = emptyList(),
                                selectedCloudProviderId = null,
                                selectedCloudType = null,
                                resolvingCloudFileKey = null,
                                cloudLibrarySettingsVersion = state.cloudLibrarySettingsVersion + 1L
                            )
                        }
                    }
                }
        }
    }

    private data class CloudLibrarySettingsSnapshot(
        val enabled: Boolean,
        val connectionKeys: List<String>
    )

    private fun setError(message: String) {
        _uiState.update { it.copy(transientMessage = message) }
        messageClearJob?.cancel()
        messageClearJob = viewModelScope.launch {
            delay(2800)
            _uiState.update { it.copy(transientMessage = null) }
        }
    }

    private fun setTransientMessage(message: String) {
        _uiState.update { it.copy(transientMessage = message) }
        messageClearJob?.cancel()
        messageClearJob = viewModelScope.launch {
            delay(2200)
            _uiState.update { it.copy(transientMessage = null) }
        }
    }

    private fun prettifyTypeLabel(key: String): String {
        return key
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                }
            }
            .ifBlank { context.getString(R.string.type_unknown) }
    }

    private val yearRegex = Regex("""\b(19|20)\d{2}\b""")

    private fun LibraryEntry.extractYear(): String? =
        releaseInfo?.let { yearRegex.find(it)?.value }

    private fun LibraryUiState.withVisibleItems(): LibraryUiState {
        // Step 1: Type filter
        val selectedTypeKey = selectedTypeTab?.key
        val typeFiltered = allItems.filter { entry ->
            selectedTypeKey == null ||
                selectedTypeKey == LibraryTypeTab.ALL_KEY ||
                entry.type.trim().lowercase(Locale.ROOT) == selectedTypeKey
        }

        // Step 2: Genre filter
        val genreFiltered = if (selectedGenre != null) {
            typeFiltered.filter { entry ->
                entry.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            }
        } else {
            typeFiltered
        }

        // Step 3: Year filter
        val yearFiltered = if (selectedYear != null) {
            genreFiltered.filter { entry -> entry.extractYear() == selectedYear }
        } else {
            genreFiltered
        }

        // Faceted counts — each filter counts items matching all OTHER active filters

        // Genre counts: from typeFiltered (after type), applying year filter but NOT genre filter
        val itemsForGenreCounts = if (selectedYear != null) {
            typeFiltered.filter { it.extractYear() == selectedYear }
        } else {
            typeFiltered
        }
        val genreCounts = mutableMapOf<String, Int>()
        itemsForGenreCounts.forEach { entry ->
            entry.genres.forEach { genre ->
                val normalized = genre.trim()
                if (normalized.isNotBlank()) {
                    genreCounts[normalized] = (genreCounts[normalized] ?: 0) + 1
                }
            }
        }
        val genreOptions = genreCounts.entries
            .sortedBy { it.key.lowercase(Locale.ROOT) }
            .map { (genre, count) -> FilterOption(key = genre, label = genre, count = count) }

        // Year counts: from typeFiltered (after type), applying genre filter but NOT year filter
        val itemsForYearCounts = if (selectedGenre != null) {
            typeFiltered.filter { entry ->
                entry.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            }
        } else {
            typeFiltered
        }
        val yearCounts = mutableMapOf<String, Int>()
        itemsForYearCounts.forEach { entry ->
            val year = entry.extractYear() ?: return@forEach
            yearCounts[year] = (yearCounts[year] ?: 0) + 1
        }
        val yearOptions = yearCounts.entries
            .sortedByDescending { it.key }
            .map { (year, count) -> FilterOption(key = year, label = year, count = count) }

        // Type tab counts: from typeFiltered, applying genre+year filters
        val itemsForTypeCounts = typeFiltered.filter { entry ->
            val genreMatch = selectedGenre == null || entry.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            val yearMatch = selectedYear == null || entry.extractYear() == selectedYear
            genreMatch && yearMatch
        }

        // Step 4: Sort
        val sorted = when (selectedSortOption) {
            LibrarySortOption.ADDED_DESC -> yearFiltered.sortedWith(
                compareByDescending<LibraryEntry> { it.listedAt }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id } }
                    .thenBy { it.id }
            )
            LibrarySortOption.ADDED_ASC -> yearFiltered.sortedWith(
                compareBy<LibraryEntry> { it.listedAt }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id } }
                    .thenBy { it.id }
            )
            LibrarySortOption.TITLE_ASC -> yearFiltered.sortedWith(
                compareBy<LibraryEntry> { titleSortKey(it.name.ifBlank { it.id }) }
                    .thenBy { it.id }
            )
            LibrarySortOption.TITLE_DESC -> yearFiltered.sortedWith(
                compareByDescending<LibraryEntry> { titleSortKey(it.name.ifBlank { it.id }) }
                    .thenBy { it.id }
            )
        }

        // Rebuild type tabs with counts
        val typeTabsWithCounts = buildTypeTabsWithCounts(allItems, itemsForTypeCounts)

        // Validate selections — clear if no longer valid
        val validGenre = selectedGenre?.takeIf { g -> genreOptions.any { it.key.equals(g, ignoreCase = true) } }
        val validYear = selectedYear?.takeIf { y -> yearOptions.any { it.key == y } }

        return copy(
            visibleItems = sorted,
            availableTypeTabs = typeTabsWithCounts,
            availableGenres = genreOptions,
            availableYears = yearOptions,
            selectedGenre = validGenre,
            selectedYear = validYear
        )
    }

    private fun LibraryUiState.withVisibleCloudItems(): LibraryUiState {
        val allCloudItems = cloudLibrary.items
        val providerFiltered = if (selectedCloudProviderId != null) {
            allCloudItems.filter { it.providerId == selectedCloudProviderId }
        } else {
            allCloudItems
        }
        val typeFiltered = if (selectedCloudType != null) {
            providerFiltered.filter { it.type == selectedCloudType }
        } else {
            providerFiltered
        }
        val visible = typeFiltered
        val providerCounts = allCloudItems
            .groupBy { it.providerId to it.providerName }
            .map { (provider, items) -> FilterOption(key = provider.first, label = provider.second, count = items.size) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
        val typeCounts = providerFiltered
            .groupBy { it.type }
            .map { (type, items) -> FilterOption(key = type.name, label = cloudTypeLabel(type), count = items.size) }
            .sortedBy { option -> CloudLibraryItemType.valueOf(option.key).ordinal }
        val validProvider = selectedCloudProviderId?.takeIf { providerId -> providerCounts.any { it.key == providerId } }
        val validType = selectedCloudType?.takeIf { type -> typeCounts.any { it.key == type.name } }
        return copy(
            visibleCloudItems = visible,
            availableCloudProviders = providerCounts,
            availableCloudTypes = typeCounts,
            selectedCloudProviderId = validProvider,
            selectedCloudType = validType
        )
    }

    private fun cloudTypeLabel(type: CloudLibraryItemType): String =
        when (type) {
            CloudLibraryItemType.Torrent -> context.getString(R.string.cloud_library_type_torrents)
            CloudLibraryItemType.Usenet -> context.getString(R.string.cloud_library_type_usenet)
            CloudLibraryItemType.WebDownload -> context.getString(R.string.cloud_library_type_web)
            CloudLibraryItemType.File -> context.getString(R.string.cloud_library_type_files)
        }

    private fun buildTypeTabsWithCounts(
        allTypeItems: List<LibraryEntry>,
        filteredItems: List<LibraryEntry>
    ): List<LibraryTypeTab> {
        val byKey = linkedMapOf<String, String>()
        allTypeItems.forEach { entry ->
            val key = entry.type.trim().ifBlank { "unknown" }.lowercase(Locale.ROOT)
            if (!byKey.containsKey(key)) {
                byKey[key] = prettifyTypeLabel(key)
            }
        }
        val countByType = mutableMapOf<String, Int>()
        filteredItems.forEach { entry ->
            val key = entry.type.trim().ifBlank { "unknown" }.lowercase(Locale.ROOT)
            countByType[key] = (countByType[key] ?: 0) + 1
        }
        val allCount = filteredItems.size
        val allTab = LibraryTypeTab(key = LibraryTypeTab.ALL_KEY, label = "${context.getString(R.string.library_type_all)} ($allCount)")
        return listOf(allTab) + byKey.map { (key, label) ->
            LibraryTypeTab(key = key, label = "$label (${countByType[key] ?: 0})")
        }
    }
}

// Strip leading English articles ("The", "A", "An") when sorting by title, so
// "The Walking Dead" sorts under W and not T. Matches how streaming services
// and most media libraries order titles alphabetically.
private val LEADING_ARTICLE_REGEX = Regex("^(the|an|a)\\s+", RegexOption.IGNORE_CASE)

private fun titleSortKey(title: String): String =
    title.trim().replace(LEADING_ARTICLE_REGEX, "").lowercase(Locale.ROOT)
