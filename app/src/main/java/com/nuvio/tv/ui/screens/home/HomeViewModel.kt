package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.core.player.StreamAutoPlayPolicy
import com.nuvio.tv.core.recommendations.TvRecommendationManager
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbCatalogService
import com.nuvio.tv.core.tmdb.TmdbPlayableCatalogLoader
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.data.xtream.XtreamPlaybackService
import com.nuvio.tv.data.xtream.XtreamCatalogState
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.ContinueWatchingSortMode
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.catalogRowStableKey
import com.nuvio.tv.domain.model.TmdbSettings
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext internal val appContext: Context,
    internal val watchProgressRepository: WatchProgressRepository,
    internal val libraryRepository: LibraryRepository,
    internal val collectionsDataStore: CollectionsDataStore,
    internal val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    internal val playerSettingsDataStore: PlayerSettingsDataStore,
    internal val tmdbSettingsDataStore: TmdbSettingsDataStore,
    internal val tmdbService: TmdbService,
    internal val tmdbMetadataService: TmdbMetadataService,
    internal val tmdbCatalogService: TmdbCatalogService,
    private val tmdbPlayableCatalogLoader: TmdbPlayableCatalogLoader,
    internal val trailerService: TrailerService,
    private val xtreamPlaybackService: XtreamPlaybackService,
    internal val watchedItemsPreferences: WatchedItemsPreferences,
    internal val watchedSeriesStateHolder: com.nuvio.tv.data.local.WatchedSeriesStateHolder,
    internal val cwEnrichmentCache: ContinueWatchingEnrichmentCache,
    internal val profileManager: com.nuvio.tv.core.profile.ProfileManager,
    internal val tvRecommendationManager: TvRecommendationManager
) : ViewModel() {
    companion object {
        internal const val TAG = "HomeViewModel"
        internal const val STARTUP_GRACE_PERIOD_MS = 1_500L
        internal const val CONTINUE_WATCHING_ENRICHMENT_GRACE_PERIOD_MS = 1_000L
        private const val CONTINUE_WATCHING_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_RECENT_PROGRESS_ITEMS = 300
        private const val MAX_NEXT_UP_LOOKUPS = 24
        private const val MAX_NEXT_UP_CONCURRENCY = 4
        private const val MAX_CATALOG_LOAD_CONCURRENCY = 3
        private val INITIAL_TMDB_CATALOG_IDS = setOf(
            "trending-movies",
            "trending-series",
            "popular-movies",
            "popular-series",
            "recent-releases",
            "new-tv-episodes",
        )
        private const val TMDB_ADDON_ID = "tmdb"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
        internal const val EXTERNAL_META_PREFETCH_FOCUS_DEBOUNCE_MS = 220L
        internal const val EXTERNAL_META_PREFETCH_ADJACENT_DEBOUNCE_MS = 120L
    }

    internal val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    internal val _movieWatchedStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val movieWatchedStatus: StateFlow<Map<String, Boolean>> = _movieWatchedStatus.asStateFlow()

    // Pending batch of watched status updates — debounced before emission.
    internal val _pendingWatchedBatch = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** True once the CW pipeline has completed its first emission (items or empty). */
    internal val _initialCwResolved = MutableStateFlow(false)
    val initialCwResolved: StateFlow<Boolean> = _initialCwResolved.asStateFlow()
    val effectiveAutoplayEnabled = playerSettingsDataStore.playerSettings
        .map(StreamAutoPlayPolicy::isEffectivelyEnabled)
        .distinctUntilChanged()
    internal val _fullCatalogRows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val fullCatalogRows: StateFlow<List<CatalogRow>> = _fullCatalogRows.asStateFlow()

    private val _focusState = MutableStateFlow(HomeScreenFocusState())
    val focusState: StateFlow<HomeScreenFocusState> = _focusState.asStateFlow()

    private val _gridFocusState = MutableStateFlow(HomeScreenFocusState())
    val gridFocusState: StateFlow<HomeScreenFocusState> = _gridFocusState.asStateFlow()

    private val _scrollToTopTrigger = MutableStateFlow(0)
    val scrollToTopTrigger: StateFlow<Int> = _scrollToTopTrigger.asStateFlow()

    internal val _currentLocaleTag = MutableStateFlow(LocaleCache.localeTag)

    fun notifyLocaleChanged() {
        val tag = LocaleCache.localeTag
        if (_currentLocaleTag.value != tag) {
            _currentLocaleTag.value = tag
        }
    }

    fun requestScrollToTop() {
        clearFocusState()
        _gridFocusState.value = HomeScreenFocusState()
        _scrollToTopTrigger.value++
    }

    internal val _loadingCatalogs = MutableStateFlow<Set<String>>(emptySet())
    val loadingCatalogs: StateFlow<Set<String>> = _loadingCatalogs.asStateFlow()

    internal val _enrichingItemId = MutableStateFlow<String?>(null)
    val enrichingItemId: StateFlow<String?> = _enrichingItemId.asStateFlow()
    internal fun setEnrichingItemId(id: String?) { _enrichingItemId.value = id }

    internal val _lastEnrichedPreview = MutableStateFlow<MetaPreview?>(null)
    val lastEnrichedPreview: StateFlow<MetaPreview?> = _lastEnrichedPreview.asStateFlow()

    internal val _enrichedPreviews = MutableStateFlow<Map<String, MetaPreview>>(emptyMap())
    val enrichedPreviews: StateFlow<Map<String, MetaPreview>> = _enrichedPreviews.asStateFlow()

    /** Items for which enrichment was attempted but produced no enriched data. */
    internal val _failedEnrichmentIds = MutableStateFlow<Set<String>>(emptySet())
    val failedEnrichmentIds: StateFlow<Set<String>> = _failedEnrichmentIds.asStateFlow()

    internal val catalogStateLock = Any()
    internal val catalogsMap = linkedMapOf<String, CatalogRow>()
    internal val catalogItemKeyIndex = mutableMapOf<String, MutableSet<String>>()
    internal val catalogOrder = mutableListOf<String>()
    internal var collectionsCache: List<Collection> = emptyList()
    internal var currentHeroCatalogKeys: List<String> = emptyList()
    internal var catalogUpdateJob: Job? = null
    internal var hasRenderedFirstCatalog = false
    internal val catalogLoadSemaphore = Semaphore(MAX_CATALOG_LOAD_CONCURRENCY)
    internal var pendingCatalogLoads = 0
    internal val activeCatalogLoadJobs = mutableSetOf<Job>()
    internal var catalogLoadGeneration: Long = 0L
    internal var catalogsLoadInProgress: Boolean = false
    internal data class TruncatedRowCacheEntry(
        val sourceRow: CatalogRow,
        val truncatedRow: CatalogRow
    )
    internal val truncatedRowCache = mutableMapOf<String, TruncatedRowCacheEntry>()
    internal val trailerPreviewLoadingIds = mutableSetOf<String>()
    internal val trailerPreviewNegativeCache = mutableSetOf<String>()
    internal val trailerPreviewUrlsState = mutableStateMapOf<String, String>()
    internal val trailerPreviewAudioUrlsState = mutableStateMapOf<String, String>()
    internal var activeTrailerPreviewItemId: String? = null
    internal var trailerPreviewRequestVersion: Long = 0L
    internal var trailerPreviewJob: Job? = null
    internal var currentTmdbSettings: TmdbSettings = TmdbSettings()
    internal var heroEnrichmentJob: Job? = null
    internal var lastHeroEnrichmentSignature: String? = null
    internal var lastHeroEnrichedItems: List<MetaPreview> = emptyList()
    internal var heroItemOrder: List<String> = emptyList()
    internal val modernCarouselRowBuildCache = ModernCarouselRowBuildCache()
    internal val prefetchedTmdbIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal val cwMetaCache = Collections.synchronizedMap(mutableMapOf<String, CwMetaSummary?>())
    internal val cwMetaNegativeCacheTimestamps = ConcurrentHashMap<String, Long>()
    /** Ultra-light cache for badge evaluation: contentId → set of aired (season, episode) pairs. */
    internal val cwBadgeEpisodeCache = Collections.synchronizedMap(mutableMapOf<String, Set<Pair<Int, Int>>?>())
    /** Per-series earliest upcoming season release date (epochMs) for smart TTL scheduling. */
    internal val cwBadgeNextSeasonMs = ConcurrentHashMap<String, Long>()
    /** Snapshot of watchedShowEpisodes keys from the last badge evaluation cycle. */
    @Volatile
    internal var cwLastBadgeEpisodeKeys: Set<String> = emptySet()
    internal val cwTmdbIdCache = Collections.synchronizedMap(mutableMapOf<String, String?>())
    internal val cwNextUpResolutionCache = Collections.synchronizedMap(mutableMapOf<String, NextUpResolution?>())
    internal val cwNextUpNegativeCacheTimestamps = ConcurrentHashMap<String, Long>()
    internal val discoveredOlderNextUpItems = Collections.synchronizedList(mutableListOf<ContinueWatchingItem.NextUp>())
    internal val cwLastProcessedNextUpContentIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal val cwEnrichedNextUpOverlay = ConcurrentHashMap<String, NextUpInfo>()
    /** In-memory cache of enriched InProgress items per contentId+episode key. */
    internal val cwEnrichedInProgressOverlay = ConcurrentHashMap<String, ContinueWatchingItem.InProgress>()
    /** Bumped to force the CW pipeline to re-run (e.g. after cache clear). */
    internal val cwPipelineRefreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Tracks the active CW pipeline coroutine so it can be cancelled on profile switch. */
    internal var cwPipelineJob: Job? = null
    internal val fullyWatchedSeriesIds get() = watchedSeriesStateHolder
    internal var tmdbEnrichFocusJob: Job? = null
    private var availabilityPrefetchJob: Job? = null
    private val availabilityPrefetchedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal var pendingTmdbEnrichItemId: String? = null
    /** Item that was focused during startup grace period — will be enriched once grace ends. */
    internal var deferredEnrichItem: MetaPreview? = null
    internal var adjacentItemPrefetchJob: Job? = null
    internal var pendingAdjacentPrefetchItemId: String? = null
    internal val movieWatchedObserverJobs = mutableMapOf<String, Job>()
    internal var movieWatchedBatchJob: Job? = null
    internal var lastMovieWatchedItemKeys: Set<String> = emptySet()
    internal var seriesWatchedObserverJob: Job? = null
    @Volatile
    internal var continueWatchingSortMode: ContinueWatchingSortMode = ContinueWatchingSortMode.DEFAULT
    internal val startupStartedAtMs: Long = SystemClock.elapsedRealtime()
    @Volatile
    internal var startupGracePeriodActive: Boolean = true

    // Lazy catalog loading
    internal val lazyLoadRequestedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val tmdbCatalogRows = linkedMapOf<String, CatalogRow>()
    private val tmdbPendingCatalogIds = mutableSetOf<String>()
    private val tmdbFailedCatalogIds = mutableSetOf<String>()
    private var hasLoggedFirstTmdbRow = false
    private var hasLoggedInitialTmdbGroup = false
    val trailerPreviewUrls: Map<String, String>
        get() = trailerPreviewUrlsState
    val trailerPreviewAudioUrls: Map<String, String>
        get() = trailerPreviewAudioUrlsState

    init {
        // Accumulates individual watched status changes and flushes them as a single
        // update after 150ms of inactivity, preventing N separate recompositions.
        viewModelScope.launch {
            _pendingWatchedBatch
                .debounce(150L)
                .collect { batch ->
                    if (batch.isNotEmpty()) {
                        val snapshot = _pendingWatchedBatch.value
                        _pendingWatchedBatch.value = emptyMap()
                        if (snapshot.isNotEmpty()) {
                            _movieWatchedStatus.update { current -> current + snapshot }
                        }
                    }
                }
        }

        viewModelScope.launch {
            profileManager.activeProfileReady.first { it }
            observeLayoutPreferences()
            observeModernHomePresentation()
            loadContinueWatching()
            watchedSeriesStateHolder.loadFromDisk()
            observeContinueWatchingSortMode()
            observeBlurUnwatchedEpisodes()
            observeTmdbCatalogAvailability()
            loadLumeCatalogs()

            viewModelScope.launch {
                combine(
                    _uiState.map { it.continueWatchingItems }.distinctUntilChanged(),
                    TvRecommendationManager.isPlaybackActive
                ) { items, isPlaying ->
                    Pair(items, isPlaying)
                }.collect { (items, isPlaying) ->
                    if (!isPlaying) {
                        runCatching { tvRecommendationManager.updateWatchNextFromCwItems(items) }
                    }
                }
            }

            // Clear CW state when profile changes so items don't leak between profiles.
            var previousProfileId = profileManager.activeProfileId.value
            profileManager.activeProfileId.collect { newId ->
                if (newId != previousProfileId) {
                    previousProfileId = newId
                    // Cancel old pipeline — prevents racing writes from stale coroutines.
                    cwPipelineJob?.cancel()
                    cwPipelineJob = null
                    // Clear all in-memory CW caches so data from the previous
                    // profile doesn't leak into the new one.
                    cwMetaCache.clear()
                    cwMetaNegativeCacheTimestamps.clear()
                    cwBadgeEpisodeCache.clear()
                    cwBadgeNextSeasonMs.clear()
                    cwTmdbIdCache.clear()
                    cwNextUpResolutionCache.clear()
                    cwNextUpNegativeCacheTimestamps.clear()
                    discoveredOlderNextUpItems.clear()
                    cwLastProcessedNextUpContentIds.clear()
                    cwEnrichedNextUpOverlay.clear()
                    cwEnrichedInProgressOverlay.clear()
                    cwLastBadgeEpisodeKeys = emptySet()
                    _uiState.update {
                        it.copy(layoutPreferencesReady = false)
                    }
                    clearFocusState()
                    _gridFocusState.value = HomeScreenFocusState()
                    // Reset so the new profile's pipeline signals first completion correctly.
                    _initialCwResolved.value = false
                    loadContinueWatching()
                    // Clear watched badges so they don't leak between profiles.
                    watchedSeriesStateHolder.update(emptySet())
                    _movieWatchedStatus.value = emptyMap()
                    _pendingWatchedBatch.value = emptyMap()
                    _uiState.update { it.copy(movieWatchedStatus = emptyMap()) }
                }
            }
        }
        viewModelScope.launch {
            delay(STARTUP_GRACE_PERIOD_MS)
            startupGracePeriodActive = false
            // Trigger enrichment for the initial focused item once grace ends.
            deferredEnrichItem?.let { item ->
                deferredEnrichItem = null
                onItemFocusPipeline(item)
            }
        }

        // Observe manual cache clear from Advanced settings.
        viewModelScope.launch {
            var lastSeen = cwEnrichmentCache.cacheCleared.value
            cwEnrichmentCache.cacheCleared.collect { version ->
                if (version != lastSeen) {
                    lastSeen = version
                    clearAllCwInMemoryCaches()
                }
            }
        }
    }

    private fun clearAllCwInMemoryCaches() {
        cwMetaCache.clear()
        cwMetaNegativeCacheTimestamps.clear()
        cwBadgeEpisodeCache.clear()
        cwBadgeNextSeasonMs.clear()
        cwTmdbIdCache.clear()
        cwNextUpResolutionCache.clear()
        cwNextUpNegativeCacheTimestamps.clear()
        discoveredOlderNextUpItems.clear()
        cwLastProcessedNextUpContentIds.clear()
        cwEnrichedNextUpOverlay.clear()
        cwEnrichedInProgressOverlay.clear()
        cwLastBadgeEpisodeKeys = emptySet()
        watchedSeriesStateHolder.clearValidationState()
        _uiState.update { it.copy(continueWatchingItems = emptyList()) }
        // Bump trigger so the pipeline's collectLatest restarts with fresh state.
        cwPipelineRefreshTrigger.value++
    }

    internal fun remainingStartupGraceMs(nowMs: Long = SystemClock.elapsedRealtime()): Long {
        if (!startupGracePeriodActive) return 0L
        return (STARTUP_GRACE_PERIOD_MS - (nowMs - startupStartedAtMs)).coerceAtLeast(0L)
    }

    internal fun remainingContinueWatchingEnrichmentGraceMs(
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Long {
        return (CONTINUE_WATCHING_ENRICHMENT_GRACE_PERIOD_MS - (nowMs - startupStartedAtMs))
            .coerceAtLeast(0L)
    }

    private fun observeLayoutPreferences() = observeLayoutPreferencesPipeline()

    private fun observeModernHomePresentation() = observeModernHomePresentationPipeline()

    private fun observeContinueWatchingSortMode() {
        viewModelScope.launch {
            var initial = true
            layoutPreferenceDataStore.continueWatchingSortMode
                .distinctUntilChanged()
                .collect { mode ->
                    continueWatchingSortMode = mode
                    if (initial) {
                        initial = false
                        return@collect
                    }
                    // Clear caches so the new sort is applied immediately on next pipeline run
                    clearAllCwInMemoryCaches()
                }
        }
    }

    private fun observeBlurUnwatchedEpisodes() {
        viewModelScope.launch {
            layoutPreferenceDataStore.blurContinueWatchingNextUp
                .distinctUntilChanged()
                .collect { enabled ->
                    _uiState.update { it.copy(blurUnwatchedEpisodes = enabled) }
                }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.useEpisodeThumbnailsInCw
                .distinctUntilChanged()
                .collect { enabled ->
                    _uiState.update { it.copy(useEpisodeThumbnailsInCw = enabled) }
                }
        }
        // When "next up from furthest episode" changes, clear CW caches and retrigger pipeline
        viewModelScope.launch {
            var initial = true
            layoutPreferenceDataStore.nextUpFromFurthestEpisode
                .distinctUntilChanged()
                .collect {
                    if (initial) {
                        initial = false
                        return@collect
                    }
                    clearAllCwInMemoryCaches()
                }
        }
    }

    fun requestTrailerPreview(item: MetaPreview) = requestTrailerPreviewPipeline(item)

    fun requestTrailerPreview(
        itemId: String,
        title: String,
        releaseInfo: String?,
        apiType: String
    ) = requestTrailerPreviewPipeline(
        itemId = itemId,
        title = title,
        releaseInfo = releaseInfo,
        apiType = apiType
    )

    fun onItemFocus(item: MetaPreview) {
        onItemFocusPipeline(item)
        scheduleAvailabilityPrefetch(item)
    }

    private fun scheduleAvailabilityPrefetch(item: MetaPreview) {
        val tmdbId = item.id.takeIf { it.startsWith("tmdb:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?: return
        if (item.id in availabilityPrefetchedIds) return
        availabilityPrefetchJob?.cancel()
        availabilityPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(450L)
            if (!availabilityPrefetchedIds.add(item.id)) return@launch
            runCatching { xtreamPlaybackService.prefetchAvailability(tmdbId, item.type) }
        }
    }

    fun preloadAdjacentItem(item: MetaPreview) = preloadAdjacentItemPipeline(item)

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.OnItemClick -> navigateToDetail(event.itemId, event.itemType)
            is HomeEvent.OnLoadMoreCatalog -> loadMoreLumeCatalog(event.catalogId)
            is HomeEvent.OnRemoveContinueWatching -> removeContinueWatching(
                contentId = event.contentId,
                season = event.season,
                episode = event.episode,
                isNextUp = event.isNextUp
            )
            HomeEvent.OnRetry -> loadLumeCatalogs()
        }
    }

    private fun loadContinueWatching() {
        // Immediately restore last known CW from disk cache for instant display.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cachedInProgress = runCatching { cwEnrichmentCache.getInProgressSnapshot() }.getOrDefault(emptyList())
            val cachedNextUp = runCatching { cwEnrichmentCache.getNextUpSnapshot() }.getOrDefault(emptyList())
            if (cachedInProgress.isEmpty() && cachedNextUp.isEmpty()) return@launch
            val dismissedNextUp = layoutPreferenceDataStore.dismissedNextUpKeys.first()
            // Render cached items immediately — the pipeline will replace these
            // with live data once it completes.
            val inProgressItems = cachedInProgress
                .filter { !watchProgressRepository.isDroppedShow(it.contentId) }
                .map { cached ->
                    ContinueWatchingItem.InProgress(
                        progress = com.nuvio.tv.domain.model.WatchProgress(
                            contentId = cached.contentId,
                            contentType = cached.contentType,
                            name = cached.name,
                            poster = cached.poster,
                            backdrop = cached.backdrop,
                            logo = cached.logo,
                            videoId = cached.videoId,
                            season = cached.season,
                            episode = cached.episode,
                            episodeTitle = cached.episodeTitle,
                            position = cached.position,
                            duration = cached.duration,
                            lastWatched = cached.lastWatched,
                            progressPercent = cached.progressPercent
                        ),
                        episodeThumbnail = cached.episodeThumbnail,
                        episodeDescription = cached.episodeDescription,
                        episodeImdbRating = cached.episodeImdbRating,
                        genres = cached.genres,
                        releaseInfo = cached.releaseInfo
                    )
                }
            val nextUpItems = cachedNextUp
                .filter { !watchProgressRepository.isDroppedShow(it.contentId) }
                .filter { nextUpDismissKey(it.contentId, it.seedSeason, it.seedEpisode) !in dismissedNextUp }
                .map { cached ->
                ContinueWatchingItem.NextUp(
                    info = NextUpInfo(
                        contentId = cached.contentId,
                        contentType = cached.contentType,
                        name = cached.name,
                        poster = cached.poster,
                        backdrop = cached.backdrop,
                        logo = cached.logo,
                        videoId = cached.videoId,
                        season = cached.season,
                        episode = cached.episode,
                        episodeTitle = cached.episodeTitle,
                        episodeDescription = cached.episodeDescription,
                        thumbnail = cached.thumbnail,
                        released = cached.released,
                        hasAired = cached.hasAired,
                        airDateLabel = cached.airDateLabel,
                        lastWatched = cached.lastWatched,
                        imdbRating = cached.imdbRating,
                        genres = cached.genres,
                        releaseInfo = cached.releaseInfo,
                        sortTimestamp = cached.sortTimestamp,
                        releaseTimestamp = cached.releaseTimestamp,
                        isReleaseAlert = cached.isReleaseAlert,
                        isNewSeasonRelease = cached.isNewSeasonRelease,
                        seedSeason = cached.seedSeason,
                        seedEpisode = cached.seedEpisode
                    )
                )
            }
            val items = mergeContinueWatchingItems(
                inProgressItems = inProgressItems,
                nextUpItems = nextUpItems,
                mode = layoutPreferenceDataStore.continueWatchingSortMode.first()
            )
            if (items.isNotEmpty()) {
                _uiState.update { it.copy(continueWatchingItems = items) }
                _initialCwResolved.value = true
            }
        }
        loadContinueWatchingPipeline()
    }

    private fun removeContinueWatching(
        contentId: String,
        season: Int? = null,
        episode: Int? = null,
        isNextUp: Boolean = false
    ) = removeContinueWatchingPipeline(
        contentId = contentId,
        season = season,
        episode = episode,
        isNextUp = isNextUp
    )

    private fun loadLumeCatalogs() {
        viewModelScope.launch {
            synchronized(catalogStateLock) {
                tmdbCatalogRows.clear()
                tmdbFailedCatalogIds.clear()
                tmdbPendingCatalogIds.clear()
                tmdbPendingCatalogIds.addAll(tmdbCatalogService.homeCatalogDefinitions.map { it.id })
            }
            lazyLoadRequestedKeys.removeAll { it.startsWith("${TMDB_ADDON_ID}_") }
            publishTmdbHome(isInitialLoading = true)

            kotlinx.coroutines.coroutineScope {
                tmdbCatalogService.homeCatalogDefinitions
                    .filter { it.id in INITIAL_TMDB_CATALOG_IDS }
                    .map { definition ->
                        async { loadTmdbCatalog(definition.id) }
                    }
                    .forEach { it.await() }
            }
            if (_uiState.value.catalogRows.isEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Nao foi possivel carregar o catalogo inicial do TMDB.",
                    )
                }
            }
        }
    }

    private fun observeTmdbCatalogAvailability() {
        viewModelScope.launch {
            tmdbPlayableCatalogLoader.catalogState
                .map { state -> state is XtreamCatalogState.Ready }
                .distinctUntilChanged()
                .collect { isReady ->
                    if (!isReady) return@collect
                    val loadedCatalogIds = synchronized(catalogStateLock) {
                        tmdbCatalogRows.keys.toList()
                    }
                    if (loadedCatalogIds.isEmpty()) return@collect
                    android.util.Log.i(
                        TAG,
                        "refilter_tmdb_catalogs count=${loadedCatalogIds.size}",
                    )
                    kotlinx.coroutines.coroutineScope {
                        loadedCatalogIds
                            .chunked(MAX_CATALOG_LOAD_CONCURRENCY)
                            .forEach { batch ->
                                batch
                                    .map { catalogId -> async { loadTmdbCatalog(catalogId) } }
                                    .forEach { it.await() }
                            }
                    }
                }
        }
    }

    private suspend fun loadTmdbCatalog(catalogId: String) {
        val startedAt = SystemClock.elapsedRealtime()
        val row = runCatching { tmdbPlayableCatalogLoader.loadInitial(catalogId, "pt-BR") }.getOrNull()
        android.util.Log.i(
            TAG,
            "tmdb_catalog id=$catalogId duration_ms=${SystemClock.elapsedRealtime() - startedAt} items=${row?.items?.size ?: 0}",
        )
        synchronized(catalogStateLock) {
            tmdbPendingCatalogIds.remove(catalogId)
            if (row != null && row.items.isNotEmpty()) {
                tmdbCatalogRows[catalogId] = row
                tmdbFailedCatalogIds.remove(catalogId)
            } else {
                tmdbFailedCatalogIds.add(catalogId)
            }
        }
        publishTmdbHome(isInitialLoading = false)
    }

    private fun publishTmdbHome(isInitialLoading: Boolean) {
        val (rows, homeRows) = synchronized(catalogStateLock) {
            val orderedRows = tmdbCatalogService.homeCatalogDefinitions.mapNotNull { tmdbCatalogRows[it.id] }
            val orderedHomeRows = tmdbCatalogService.homeCatalogDefinitions.mapNotNull { definition ->
                tmdbCatalogRows[definition.id]?.let(HomeRow::Catalog)
                    ?: definition.takeIf { it.id in tmdbPendingCatalogIds }?.let {
                        val apiType = it.contentType.toApiString()
                        val legacyKey = "${TMDB_ADDON_ID}_${apiType}_${it.id}"
                        HomeRow.PlaceholderCatalog(
                            catalogKey = legacyKey,
                            stableCatalogKey = catalogRowStableKey(TMDB_ADDON_ID, TMDB_BASE_URL, apiType, it.id),
                            addonId = TMDB_ADDON_ID,
                            addonName = "TMDB",
                            addonBaseUrl = TMDB_BASE_URL,
                            catalogId = it.id,
                            catalogName = it.title,
                            apiType = apiType,
                            displayTitle = it.title,
                        )
                    }
            }
            orderedRows to orderedHomeRows
        }
        _fullCatalogRows.value = rows
        _uiState.update { state ->
            state.copy(
                catalogRows = rows,
                homeRows = homeRows,
                heroItems = rows.firstOrNull()?.items.orEmpty().take(12),
                isLoading = isInitialLoading && rows.isEmpty(),
                error = null,
            )
        }
        if (rows.isNotEmpty() && !hasLoggedFirstTmdbRow) {
            hasLoggedFirstTmdbRow = true
            android.util.Log.i(TAG, "first_useful_home_ms=${SystemClock.elapsedRealtime() - startupStartedAtMs}")
        }
        if (!hasLoggedInitialTmdbGroup && INITIAL_TMDB_CATALOG_IDS.all { id -> rows.any { it.catalogId == id } }) {
            hasLoggedInitialTmdbGroup = true
            android.util.Log.i(TAG, "initial_tmdb_group_ms=${SystemClock.elapsedRealtime() - startupStartedAtMs}")
        }
    }

    private fun loadMoreLumeCatalog(catalogId: String) {
        val current = _uiState.value.catalogRows.firstOrNull { it.catalogId == catalogId } ?: return
        if (current.isLoading || !current.hasMore) return
        synchronized(catalogStateLock) {
            tmdbCatalogRows[catalogId] = current.copy(isLoading = true)
        }
        publishTmdbHome(isInitialLoading = false)
        viewModelScope.launch {
            runCatching { tmdbPlayableCatalogLoader.loadMore(current, "pt-BR") }
                .onSuccess { page ->
                    val merged = page ?: current.copy(isLoading = false, hasMore = false)
                    synchronized(catalogStateLock) {
                        tmdbCatalogRows[catalogId] = merged
                    }
                    publishTmdbHome(isInitialLoading = false)
                }
                .onFailure {
                    synchronized(catalogStateLock) {
                        tmdbCatalogRows[catalogId] = current.copy(isLoading = false)
                    }
                    publishTmdbHome(isInitialLoading = false)
                }
        }
    }

    internal fun scheduleUpdateCatalogRows() {
        catalogUpdateJob?.cancel()
        catalogUpdateJob = viewModelScope.launch {
            val debounceMs = when {
                // First render: use a moderate debounce so near-simultaneous
                // catalog arrivals are batched into a single heavy update pass.
                !hasRenderedFirstCatalog && hasAnyCatalogRows() -> {
                    hasRenderedFirstCatalog = true
                    150L
                }
                // During bulk loading, batch aggressively — placeholders are
                // already visible so the user won't notice the delay.
                pendingCatalogLoads > 8 -> 300L
                pendingCatalogLoads > 3 -> 250L
                pendingCatalogLoads > 0 -> 200L
                else -> 80L
            }
            delay(debounceMs)
            updateCatalogRows()
        }
    }

    /**
     * Called from the UI when a placeholder catalog row becomes visible.
     */
    fun requestLazyCatalogLoad(catalogKey: String) {
        val tmdbDefinition = tmdbCatalogService.homeCatalogDefinitions.firstOrNull { definition ->
            catalogKey == "${TMDB_ADDON_ID}_${definition.contentType.toApiString()}_${definition.id}"
        } ?: return
        val shouldLoad = synchronized(catalogStateLock) {
            tmdbDefinition.id in tmdbPendingCatalogIds && lazyLoadRequestedKeys.add(catalogKey)
        }
        if (shouldLoad) {
            viewModelScope.launch { loadTmdbCatalog(tmdbDefinition.id) }
        }
    }

    /**
     * Load all pending lazy catalogs at once. Used when switching to GRID layout
     * which needs all catalogs available upfront.
     */
    internal fun loadAllPendingLazyCatalogs() {
        val pendingTmdb = synchronized(catalogStateLock) {
            tmdbCatalogService.homeCatalogDefinitions.filter { it.id in tmdbPendingCatalogIds }
        }
        pendingTmdb.forEach { definition ->
            val key = "${TMDB_ADDON_ID}_${definition.contentType.toApiString()}_${definition.id}"
            if (lazyLoadRequestedKeys.add(key)) {
                viewModelScope.launch { loadTmdbCatalog(definition.id) }
            }
        }
    }

    private suspend fun updateCatalogRows() = updateCatalogRowsPipeline()

    internal var posterStatusReconcileJob: Job? = null

    private fun schedulePosterStatusReconcile(rows: List<CatalogRow>) =
        schedulePosterStatusReconcilePipeline(rows)

    private fun reconcilePosterStatusObservers(rows: List<CatalogRow>) =
        reconcilePosterStatusObserversPipeline(rows)

    private fun navigateToDetail(itemId: String, itemType: String) {
        _uiState.update { it.copy(selectedItemId = itemId) }
    }

    private suspend fun enrichHeroItems(
        items: List<MetaPreview>,
        settings: TmdbSettings
    ): List<MetaPreview> = enrichHeroItemsPipeline(items, settings)

    private fun replaceGridHeroItems(
        gridItems: List<GridItem>,
        heroItems: List<MetaPreview>
    ): List<GridItem> = replaceGridHeroItemsPipeline(gridItems, heroItems)

    private fun heroEnrichmentSignature(items: List<MetaPreview>, settings: TmdbSettings): String =
        heroEnrichmentSignaturePipeline(items, settings)

    /**
     * Saves the current focus and scroll state for restoration when returning to this screen.
     */
    // When true, the next saveFocusState call is suppressed and the flag
    // is reset.  Used during layout switches to prevent the outgoing
    // layout's onDispose from poisoning the incoming layout's focus state.
    internal var suppressFocusSave: Boolean = false

    fun saveFocusState(
        verticalScrollIndex: Int,
        verticalScrollOffset: Int,
        focusedRowKey: String?,
        focusedItemKeyByRow: Map<String, String>,
        catalogRowScrollStates: Map<String, Int>,
        focusedRowIndex: Int = 0,
        focusedItemIndex: Int = 0
    ) {
        if (suppressFocusSave) {
            suppressFocusSave = false
            return
        }
        val nextState = _focusState.value.copy(
            verticalScrollIndex = verticalScrollIndex,
            verticalScrollOffset = verticalScrollOffset,
            focusedRowKey = focusedRowKey,
            focusedItemKeyByRow = focusedItemKeyByRow,
            catalogRowScrollStates = catalogRowScrollStates,
            focusedRowIndex = focusedRowIndex,
            focusedItemIndex = focusedItemIndex,
            hasSavedFocus = true
        )
        if (_focusState.value == nextState) return
        _focusState.value = nextState
    }

    /**
     * Updates the stable focus target for a specific row.
     */
    fun updateFocusedItemKey(rowKey: String, itemKey: String) {
        _focusState.update { state ->
            val nextMap = state.focusedItemKeyByRow.toMutableMap()
            if (nextMap[rowKey] == itemKey) return@update state
            nextMap[rowKey] = itemKey
            state.copy(focusedItemKeyByRow = nextMap)
        }
    }

    /**
     * Updates the currently focused row key.
     */
    fun updateFocusedRowKey(rowKey: String?) {
        _focusState.update { state ->
            if (state.focusedRowKey == rowKey) state else state.copy(focusedRowKey = rowKey)
        }
    }

    /**
     * Clears the saved focus state.
     */
    fun clearFocusState() {
        _focusState.value = HomeScreenFocusState()
    }

    /**
     * Saves the grid layout focus and scroll state.
     */
    fun saveGridFocusState(
        verticalScrollIndex: Int,
        verticalScrollOffset: Int,
        focusedRowIndex: Int = 0,
        focusedItemIndex: Int = 0,
        focusedItemKey: String? = null
    ) {
        _gridFocusState.value = HomeScreenFocusState(
            verticalScrollIndex = verticalScrollIndex,
            verticalScrollOffset = verticalScrollOffset,
            focusedRowIndex = focusedRowIndex,
            focusedItemIndex = focusedItemIndex,
            focusedItemKey = focusedItemKey,
            hasSavedFocus = true
        )
    }

    override fun onCleared() {
        posterStatusReconcileJob?.cancel()
        movieWatchedBatchJob?.cancel()
        seriesWatchedObserverJob?.cancel()
        cancelInFlightCatalogLoads()
        movieWatchedObserverJobs.values.forEach { it.cancel() }
        movieWatchedObserverJobs.clear()
        super.onCleared()
    }
}
