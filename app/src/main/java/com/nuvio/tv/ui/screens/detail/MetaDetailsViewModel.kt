package com.nuvio.tv.ui.screens.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.player.StreamAutoPlayPolicy
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaTrailer
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.TmdbSettings
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.core.util.isUnreleased
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.R
import com.nuvio.tv.data.xtream.XtreamAvailability
import com.nuvio.tv.data.xtream.XtreamPlaybackService
import com.nuvio.tv.data.xtream.CatalogAvailabilityTracker
import com.nuvio.tv.data.xtream.XtreamCatalogAvailabilityService
import com.nuvio.tv.data.xtream.resolveTmdbPlaybackId
import com.nuvio.tv.core.build.AppFeaturePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

private const val TAG = "MetaDetailsViewModel"

@HiltViewModel
class MetaDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val watchedItemsPreferences: WatchedItemsPreferences,
    private val trailerService: TrailerService,
    private val trailerSettingsDataStore: TrailerSettingsDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val playerSettingsDataStore: PlayerSettingsDataStore,
    private val xtreamPlaybackService: XtreamPlaybackService,
    xtreamCatalogAvailabilityService: XtreamCatalogAvailabilityService,
    private val watchedSeriesStateHolder: com.nuvio.tv.data.local.WatchedSeriesStateHolder,
    val posterOptions: com.nuvio.tv.ui.components.posteroptions.PosterOptionsController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val itemId: String = savedStateHandle["itemId"] ?: ""
    private val itemType: String = savedStateHandle["itemType"] ?: ""

    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()
    private val availabilityTracker = CatalogAvailabilityTracker(
        scope = viewModelScope,
        service = xtreamCatalogAvailabilityService,
    )
    val catalogAvailability = availabilityTracker.availability

    private val localizedContext: Context
        get() {
            val tag = LocaleCache.localeTag.takeIf { it != LocaleCache.UNSET && it.isNotEmpty() }
                ?: return context
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    val effectiveAutoplayEnabled = playerSettingsDataStore.playerSettings
        .map(StreamAutoPlayPolicy::isEffectivelyEnabled)
        .distinctUntilChanged()
    private var resolvedPlaybackTmdbId: Int? = itemId
        .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.substringBefore(':')
        ?.toIntOrNull()
    val isTmdbPlayback: Boolean
        get() = resolvedPlaybackTmdbId != null || itemId.startsWith("tt", ignoreCase = true)

    fun playbackVideoId(fallback: String, season: Int? = null, episode: Int? = null): String {
        return StreamAutoPlayPolicy.canonicalTmdbVideoId(
            resolvedPlaybackTmdbId,
            fallback,
            season,
            episode,
        )
    }

    private var idleTimerJob: Job? = null
    private var trailerFetchJob: Job? = null
    private var moreLikeThisJob: Job? = null
    private var collectionJob: Job? = null

    val lastFocusedEpisodeIdBySeason = androidx.compose.runtime.mutableStateMapOf<Int, String>()
    private var nextToWatchJob: Job? = null
    private var playbackAvailabilityJob: Job? = null

    private var trailerDelayMs = 7000L
    private var trailerAutoplayEnabled = false
    private var trailerHasPlayed = false
    private var suppressSeasonAutoSwitch = false

    private var isPlayButtonFocused = false
    private var hideUnreleasedContent = false

    /** Content ID used for watch-progress and watched-items lookups.
     *  Starts as the navigation [itemId] (which may be "tmdb:123") and is
     *  updated to [Meta.id] once meta loads (typically an IMDB ID like "tt0396375").
     *  This ensures progress is read from the same key it was written under. */
    private val _effectiveContentId = MutableStateFlow(itemId)

    init {
        posterOptions.bind(viewModelScope)
        viewModelScope.launch {
            uiState
                .map { state -> state.moreLikeThis + state.collection }
                .distinctUntilChanged()
                .collectLatest(availabilityTracker::submit)
        }
        observeMetaViewSettings()
        observeTrailerAutoplaySettings()
        observeLibraryState()
        observeWatchProgress()
        observeWatchedEpisodes()
        observeMovieWatched()
        observeBlurUnwatchedEpisodes()
        observeShowFullReleaseDate()
        observeHideUnreleasedContent()
        loadMeta()
    }

    private fun observeHideUnreleasedContent() {
        viewModelScope.launch {
            layoutPreferenceDataStore.hideUnreleasedContent
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    hideUnreleasedContent = enabled
                }
        }
    }

    private fun observeMetaViewSettings() {
        viewModelScope.launch {
            layoutPreferenceDataStore.detailPageTrailerButtonEnabled
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    _uiState.update { state ->
                        if (state.trailerButtonEnabled == enabled) {
                            state
                        } else {
                            state.copy(trailerButtonEnabled = enabled)
                        }
                    }
                }
        }
    }

    private fun setTrailerPlaybackState(
        isPlaying: Boolean,
        showControls: Boolean,
        hideLogo: Boolean
    ) {
        _uiState.update { state ->
            if (state.isTrailerPlaying == isPlaying &&
                state.showTrailerControls == showControls &&
                state.hideLogoDuringTrailer == hideLogo
            ) {
                state
            } else {
                state.copy(
                    isTrailerPlaying = isPlaying,
                    showTrailerControls = showControls,
                    hideLogoDuringTrailer = hideLogo
                )
            }
        }
    }

    private fun updateNextToWatch(nextToWatch: NextToWatch) {
        _uiState.update { state ->
            if (state.nextToWatch == nextToWatch) return@update state
            val nextSeason = nextToWatch.nextSeason
            val meta = state.meta
            val shouldSwitchSeason = !suppressSeasonAutoSwitch &&
                nextSeason != null &&
                nextSeason != state.selectedSeason &&
                meta != null &&
                state.seasons.contains(nextSeason)
            if (shouldSwitchSeason) {
                state.copy(
                    nextToWatch = nextToWatch,
                    selectedSeason = nextSeason,
                    episodesForSeason = getEpisodesForSeason(meta.videos, nextSeason)
                )
            } else {
                state.copy(nextToWatch = nextToWatch)
            }
        }
    }

    private fun observeTrailerAutoplaySettings() {
        viewModelScope.launch {
            trailerSettingsDataStore.settings.collectLatest { settings ->
                trailerAutoplayEnabled = settings.enabled
                trailerDelayMs = settings.delaySeconds * 1000L
                if (!settings.enabled) {
                    idleTimerJob?.cancel()
                }
            }
        }
    }

    fun onEvent(event: MetaDetailsEvent) {
        when (event) {
            is MetaDetailsEvent.OnSeasonSelected -> selectSeason(event.season)
            is MetaDetailsEvent.OnEpisodeClick -> { /* Navigate to stream */ }
            MetaDetailsEvent.OnPlayClick -> { /* Start playback */ }
            MetaDetailsEvent.OnToggleLibrary -> toggleLibrary()
            MetaDetailsEvent.OnRetry -> loadMeta()
            MetaDetailsEvent.OnBackPress -> { /* Handle in screen */ }
            MetaDetailsEvent.OnUserInteraction -> handleUserInteraction()
            MetaDetailsEvent.OnPlayButtonFocused -> handlePlayButtonFocused()
            MetaDetailsEvent.OnTrailerButtonClick -> handleTrailerButtonClick()
            MetaDetailsEvent.OnTrailerEnded -> handleTrailerEnded()
            is MetaDetailsEvent.OnSharedTrailerSelected -> handleSharedTrailerSelected(event.trailer)
            MetaDetailsEvent.OnDismissSharedTrailer -> dismissSharedTrailerOverlay()
            MetaDetailsEvent.OnRetrySharedTrailer -> retrySharedTrailer()
            MetaDetailsEvent.OnToggleMovieWatched -> toggleMovieWatched()
            is MetaDetailsEvent.OnToggleEpisodeWatched -> toggleEpisodeWatched(event.video)
            is MetaDetailsEvent.OnMarkSeasonWatched -> markSeasonWatched(event.season)
            is MetaDetailsEvent.OnMarkSeasonUnwatched -> markSeasonUnwatched(event.season)
            is MetaDetailsEvent.OnMarkPreviousEpisodesWatched -> markPreviousEpisodesWatched(event.video)
            is MetaDetailsEvent.OnMarkPreviousSeasonsWatched -> markPreviousSeasonsWatched(event.season)
            MetaDetailsEvent.OnLibraryLongPress -> openListPicker()
            is MetaDetailsEvent.OnPickerMembershipToggled -> togglePickerMembership(event.listKey)
            MetaDetailsEvent.OnPickerSave -> savePickerMembership()
            MetaDetailsEvent.OnPickerDismiss -> dismissListPicker()
            MetaDetailsEvent.OnClearMessage -> clearMessage()
            MetaDetailsEvent.OnLifecyclePause -> handleLifecyclePause()
        }
    }

    private fun observeLibraryState() {
        viewModelScope.launch {
            libraryRepository.sourceMode
                .distinctUntilChanged()
                .collectLatest { sourceMode ->
                    _uiState.update { state ->
                        if (state.librarySourceMode == sourceMode) {
                            state
                        } else {
                            state.copy(librarySourceMode = sourceMode)
                        }
                    }
                }
        }

        // Observe library/watchlist on the *same* (id, type) pair that
        // `toggleLibrary` writes via `meta.toLibraryEntryInput()`. Falling back
        // to navigation (itemId, itemType) until meta loads keeps the button
        // responsive but pre-meta (when toggle is unavailable anyway).
        val canonicalKey = _uiState
            .map { state ->
                val id = state.meta?.id?.takeIf { it.isNotBlank() } ?: itemId
                val type = state.meta?.apiType?.takeIf { it.isNotBlank() } ?: itemType
                id to type
            }
            .distinctUntilChanged()

        viewModelScope.launch {
            canonicalKey
                .flatMapLatest { (id, type) -> libraryRepository.isInLibrary(itemId = id, itemType = type) }
                .distinctUntilChanged()
                .collectLatest { inLibrary ->
                    _uiState.update { state ->
                        if (state.isInLibrary == inLibrary) state else state.copy(isInLibrary = inLibrary)
                    }
                }
        }

        viewModelScope.launch {
            canonicalKey
                .flatMapLatest { (id, type) -> libraryRepository.isInWatchlist(itemId = id, itemType = type) }
                .distinctUntilChanged()
                .collectLatest { inWatchlist ->
                    _uiState.update { state ->
                        if (state.isInWatchlist == inWatchlist) state else state.copy(isInWatchlist = inWatchlist)
                    }
                }
        }
    }

    private fun observeWatchProgress() {
        if (itemType.lowercase() == "movie") return
        viewModelScope.launch {
            _effectiveContentId.flatMapLatest { cid ->
                if (itemType.equals("other", ignoreCase = true)) {
                    // For "other" type, videos lack season/episode.
                    // Build progress map by matching video IDs to their
                    // position in the meta video list.
                    watchProgressRepository.allProgress.map { allProgress ->
                        val meta = _uiState.value.meta
                        val videos = meta?.videos ?: emptyList()
                        val progressByVideoId = allProgress
                            .filter { it.contentId == cid }
                            .associateBy { it.videoId }
                        val result = mutableMapOf<Pair<Int, Int>, WatchProgress>()
                        videos.forEachIndexed { index, video ->
                            val progress = progressByVideoId[video.id]
                            if (progress != null) {
                                // Use synthetic season=1, episode=index+1 as key
                                result[1 to (index + 1)] = progress
                            }
                        }
                        result as Map<Pair<Int, Int>, WatchProgress>
                    }
                } else {
                    watchProgressRepository.getAllEpisodeProgress(cid)
                }
            }
                .distinctUntilChanged()
                .collectLatest { progressMap ->
                _uiState.update { state ->
                    if (state.episodeProgressMap == progressMap) {
                        state
                    } else {
                        state.copy(episodeProgressMap = progressMap)
                    }
                }
                // Recalculate next to watch when progress changes
                reevaluateSeriesWatchedBadge()
                calculateNextToWatch()
            }
        }
    }

    private fun observeWatchedEpisodes() {
        if (itemType.lowercase() == "movie") return
        viewModelScope.launch {
            _effectiveContentId.flatMapLatest { cid ->
                watchedItemsPreferences.getWatchedEpisodesForContent(cid)
            }
                .distinctUntilChanged()
                .collectLatest { watchedSet ->
                _uiState.update { state ->
                    if (state.watchedEpisodes == watchedSet) {
                        state
                    } else {
                        state.copy(watchedEpisodes = watchedSet)
                    }
                }
                reevaluateSeriesWatchedBadge()
                calculateNextToWatch()
            }
        }
        // Re-calculate next-to-watch when "furthest episode" preference changes
        viewModelScope.launch {
            layoutPreferenceDataStore.nextUpFromFurthestEpisode
                .distinctUntilChanged()
                .collectLatest {
                    calculateNextToWatch()
                }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeMovieWatched() {
        if (itemType.lowercase() != "movie") return
        viewModelScope.launch {
            _effectiveContentId.flatMapLatest { cid ->
                _uiState.map { it.meta?.imdbId?.takeIf { id -> id != cid && id.isNotBlank() } }
                    .distinctUntilChanged()
                    .flatMapLatest { videoId ->
                        watchProgressRepository.isWatched(cid, videoId = videoId)
                    }
            }
                .distinctUntilChanged()
                .collectLatest { watched ->
                _uiState.update { state ->
                    if (state.isMovieWatched == watched) state else state.copy(isMovieWatched = watched)
                }
            }
        }
    }

    private fun observeBlurUnwatchedEpisodes() {
        viewModelScope.launch {
            layoutPreferenceDataStore.blurUnwatchedEpisodes
                .distinctUntilChanged()
                .collectLatest { enabled ->
                _uiState.update { state ->
                    if (state.blurUnwatchedEpisodes == enabled) state else state.copy(blurUnwatchedEpisodes = enabled)
                }
            }
        }
    }

    private fun observeShowFullReleaseDate() {
        viewModelScope.launch {
            layoutPreferenceDataStore.showFullReleaseDate
                .distinctUntilChanged()
                .collectLatest { enabled ->
                _uiState.update { state ->
                    if (state.showFullReleaseDate == enabled) state else state.copy(showFullReleaseDate = enabled)
                }
            }
        }
    }

    private fun loadMeta() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    tmdbRating = null,
                    moreLikeThis = emptyList(),
                    moreLikeThisSource = null,
                    collection = emptyList(),
                    collectionName = null,
                    isSharedTrailerOverlayVisible = false,
                    isSharedTrailerLoading = false,
                    sharedTrailerUrl = null,
                    sharedTrailerAudioUrl = null,
                    sharedTrailerErrorMessage = null,
                    selectedSharedTrailer = null
                )
            }

            if (!tryApplyTmdbMeta()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_generic)
                    )
                }
            }
        }
    }

    private suspend fun tryApplyTmdbMeta(): Boolean {
        val type = ContentType.fromString(itemType)
        val tmdbIdString = if (itemId.startsWith("tmdb:", ignoreCase = true)) {
            itemId.substringAfter(':').substringBefore(':')
        } else {
            // Resolve IMDb ("tt...") or plain IDs to a TMDB id for enrichment.
            tmdbService.ensureTmdbId(itemId, type.toApiString())
        }
        val tmdbId = tmdbIdString
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?: return false
        val settings = tmdbSettingsDataStore.settings.first()
        val enrichment = tmdbMetadataService.fetchEnrichment(
            tmdbId = tmdbId.toString(),
            contentType = type,
            language = settings.language
        ) ?: return false
        val videos = if (type == ContentType.SERIES || type == ContentType.TV) {
            tmdbMetadataService.fetchSeriesVideos(tmdbId.toString(), settings.language)
        } else {
            emptyList()
        }
        val meta = Meta(
            id = itemId,
            type = type,
            rawType = itemType,
            name = enrichment.localizedTitle ?: enrichment.originalTitle
                ?: context.getString(R.string.detail_tmdb_fallback_title, tmdbId),
            poster = enrichment.poster,
            posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
            background = enrichment.backdrop,
            logo = enrichment.logo,
            description = enrichment.description,
            releaseInfo = enrichment.releaseInfo,
            status = enrichment.status,
            imdbRating = enrichment.rating?.toFloat(),
            genres = enrichment.genres,
            runtime = enrichment.runtimeMinutes?.toString(),
            director = enrichment.director,
            writer = enrichment.writer,
            cast = enrichment.castMembers.map { it.name },
            castMembers = enrichment.castMembers,
            videos = videos,
            productionCompanies = enrichment.productionCompanies,
            networks = enrichment.networks,
            ageRating = enrichment.ageRating,
            country = enrichment.countries?.joinToString(", "),
            awards = null,
            language = enrichment.language,
            links = emptyList(),
            // Honor the "Disable Trailers in TMDB Enrichment" toggle even on
            // this synthetic meta. The main enrichment merge at the bottom of
            // applyMetaWithEnrichment already gates on settings.useTrailers;
            // without the same gate here this path would smuggle TMDB trailers
            // in unconditionally.
            trailers = if (settings.useTrailers) enrichment.trailers else emptyList()
        )
        applyMetaWithEnrichment(meta)
        return true
    }

    private fun applyMeta(meta: Meta) {
        // Update the effective content ID so watch-progress observers pick up
        // the canonical ID (e.g. IMDB "tt0396375") instead of the navigation ID
        // (which may be "tmdb:13836").  Don't downgrade from an IMDB ID to a
        // less canonical one (e.g. tmdb:).
        if (meta.id.isNotBlank() && meta.id != itemId) {
            val currentIsImdb = _effectiveContentId.value.startsWith("tt")
            val newIsImdb = meta.id.startsWith("tt")
            if (!currentIsImdb || newIsImdb) {
                _effectiveContentId.value = meta.id
            }
        }

        val seasons = meta.videos
            .mapNotNull { it.season }
            .distinct()
            .sorted()
            .ifEmpty {
                // For "other" type content videos lack season/episode numbers.  
                // Treat them as a single virtual season so the episodes UI can display them.
                if (meta.videos.isNotEmpty()) listOf(1) else emptyList()
            }

        val defaultEpisodeSeason = findPreferredDefaultEpisode(meta)?.season
        // Prefer the default-episode hint, otherwise first regular season (> 0), fallback to season 0 (specials)
        val selectedSeason = defaultEpisodeSeason
            ?.takeIf { it in seasons }
            ?: seasons.firstOrNull { it > 0 }
            ?: seasons.firstOrNull()
            ?: 1
        val episodesForSeason = getEpisodesForSeason(meta.videos, selectedSeason)

        _uiState.update {
            // If nextToWatch already set a season (from pre-computed remap), prefer it
            // over the default season selection.
            val effectiveSeason = it.nextToWatch?.nextSeason
                ?.takeIf { s -> s in seasons }
                ?: selectedSeason
            val effectiveEpisodes = if (effectiveSeason != selectedSeason) {
                getEpisodesForSeason(meta.videos, effectiveSeason)
            } else {
                episodesForSeason
            }
            it.copy(
                isLoading = false,
                meta = meta,
                seasons = seasons,
                selectedSeason = effectiveSeason,
                episodesForSeason = effectiveEpisodes,
                error = null
            )
        }

        // Calculate next to watch after meta is loaded
        reevaluateSeriesWatchedBadge()
        calculateNextToWatch()
        loadPlaybackAvailability(meta)

        // Start fetching trailer after meta is loaded
        fetchTrailerUrl()
    }

    fun retryPlaybackAvailability() {
        _uiState.value.meta?.let(::loadPlaybackAvailability)
    }

    private fun loadPlaybackAvailability(meta: Meta) {
        playbackAvailabilityJob?.cancel()
        val isSeries = meta.type == ContentType.SERIES || meta.type == ContentType.TV
        _uiState.update { state ->
            state.copy(
                moviePlaybackAvailability = PlaybackAvailabilityState.CHECKING,
                episodePlaybackAvailability = if (isSeries) {
                    meta.videos.mapNotNull { video ->
                        val season = video.season
                        val episode = video.episode
                        if (season == null || episode == null) null
                        else (season to episode) to PlaybackAvailabilityState.CHECKING
                    }.toMap()
                } else {
                    emptyMap()
                }
            )
        }

        playbackAvailabilityJob = viewModelScope.launch {
            val lookupType = resolveTmdbContentType(meta).toApiString()
            val tmdbId = resolvedPlaybackTmdbId ?: resolveTmdbPlaybackId(
                candidates = listOf(meta.id, itemId),
                mediaType = lookupType,
                lookup = tmdbService::ensureTmdbId,
            )
            if (tmdbId == null) {
                _uiState.update { state ->
                    if (state.meta?.id != meta.id) {
                        state
                    } else {
                        state.copy(
                            moviePlaybackAvailability = PlaybackAvailabilityState.ERROR,
                            episodePlaybackAvailability = if (isSeries) {
                                meta.videos.mapNotNull { video ->
                                    val season = video.season
                                    val episode = video.episode
                                    if (season == null || episode == null) null
                                    else (season to episode) to PlaybackAvailabilityState.ERROR
                                }.toMap()
                            } else {
                                emptyMap()
                            },
                        )
                    }
                }
                return@launch
            }
            resolvedPlaybackTmdbId = tmdbId
            if (isSeries) {
                val result = xtreamPlaybackService.seriesAvailability(tmdbId)
                val availability = meta.videos.mapNotNull { video ->
                    val season = video.season
                    val episode = video.episode
                    if (season == null || episode == null) return@mapNotNull null
                    val key = season to episode
                    val state = when {
                        result.status == XtreamAvailability.ERROR -> PlaybackAvailabilityState.ERROR
                        result.status == XtreamAvailability.UNAVAILABLE -> PlaybackAvailabilityState.UNAVAILABLE
                        key in result.availableEpisodes -> PlaybackAvailabilityState.AVAILABLE
                        result.complete -> PlaybackAvailabilityState.UNAVAILABLE
                        else -> PlaybackAvailabilityState.ERROR
                    }
                    key to state
                }.toMap()
                _uiState.update { state ->
                    if (state.meta?.id != meta.id) state
                    else state.copy(episodePlaybackAvailability = availability)
                }
            } else {
                val availability = when (xtreamPlaybackService.movieAvailability(tmdbId)) {
                    XtreamAvailability.AVAILABLE -> PlaybackAvailabilityState.AVAILABLE
                    XtreamAvailability.UNAVAILABLE -> PlaybackAvailabilityState.UNAVAILABLE
                    XtreamAvailability.ERROR -> PlaybackAvailabilityState.ERROR
                }
                _uiState.update { state ->
                    if (state.meta?.id != meta.id) state
                    else state.copy(moviePlaybackAvailability = availability)
                }
            }
        }
    }

    private suspend fun applyMetaWithEnrichment(meta: Meta) {
        // Fire all independent async jobs immediately — they run in parallel.
        loadMoreLikeThisAsync(meta)
        val enriched = enrichMeta(meta)

        // Pre-compute nextToWatch before applyMeta so the PlayButton text is stable
        // from the first composition — prevents focus invalidation from late recomposition.
        val progressMap = watchProgressRepository
            .getAllEpisodeProgress(_effectiveContentId.value)
            .first()
        val watchedEpisodes = watchedItemsPreferences
            .getWatchedEpisodesForContent(_effectiveContentId.value)
            .first()
        val precomputedNextToWatch = computeNextToWatch(enriched, progressMap, watchedEpisodes)
        updateNextToWatch(precomputedNextToWatch)

        applyMeta(enriched)
    }

    private fun loadMoreLikeThisAsync(meta: Meta) {
        moreLikeThisJob?.cancel()
        moreLikeThisJob = viewModelScope.launch {
            val settings = tmdbSettingsDataStore.settings.first()
            if (!shouldLoadMoreLikeThis(settings)) {
                _uiState.update { it.copy(moreLikeThis = emptyList(), moreLikeThisSource = null) }
                return@launch
            }

            val tmdbContentType = resolveTmdbContentType(meta)
            val tmdbLookupType = tmdbContentType.toApiString()
            val tmdbId = tmdbService.ensureTmdbId(meta.id, tmdbLookupType)
                ?: tmdbService.ensureTmdbId(itemId, itemType)
            if (tmdbId.isNullOrBlank()) {
                _uiState.update { it.copy(moreLikeThis = emptyList(), moreLikeThisSource = null) }
                return@launch
            }

            val recommendations = runCatching {
                tmdbMetadataService.fetchMoreLikeThis(
                    tmdbId = tmdbId,
                    contentType = tmdbContentType,
                    language = settings.language
                )
            }.getOrElse {
                Log.w(TAG, "Failed to load More like this for ${meta.id}: ${it.message}")
                emptyList()
            }

            val filteredRecommendations = if (hideUnreleasedContent) {
                val today = LocalDate.now()
                recommendations.filterNot { it.isUnreleased(today) }
            } else {
                recommendations
            }

            _uiState.update { state ->
                if (state.meta == null || state.meta.id == meta.id) {
                    state.copy(
                        moreLikeThis = filteredRecommendations,
                        moreLikeThisSource = MoreLikeThisSource.TMDB
                            .takeIf { filteredRecommendations.isNotEmpty() }
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun shouldLoadMoreLikeThis(settings: TmdbSettings): Boolean {
        return settings.enabled && settings.useMoreLikeThis
    }

    private fun loadCollectionAsync(collectionId: Int, collectionName: String?, settings: TmdbSettings) {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            if (!settings.enabled || !settings.useCollections) {
                _uiState.update { it.copy(collection = emptyList(), collectionName = null) }
                return@launch
            }

            val items = runCatching {
                tmdbMetadataService.fetchMovieCollection(
                    collectionId = collectionId,
                    language = settings.language
                )
            }.getOrElse {
                Log.w(TAG, "Failed to load collection $collectionId: ${it.message}")
                emptyList()
            }

            val filteredItems = if (hideUnreleasedContent) {
                val today = LocalDate.now()
                items.filterNot { it.isUnreleased(today) }
            } else {
                items
            }

            _uiState.update { state ->
                state.copy(collection = filteredItems, collectionName = collectionName)
            }
        }
    }

    private suspend fun enrichMeta(meta: Meta): Meta {
        val settings = tmdbSettingsDataStore.settings.first()
        if (!settings.enabled) return meta

        val tmdbContentType = resolveTmdbContentType(meta)
        val tmdbLookupType = tmdbContentType.toApiString()
        val tmdbId = tmdbService.ensureTmdbId(meta.id, tmdbLookupType)
            ?: tmdbService.ensureTmdbId(itemId, itemType)
            ?: return meta

        val isSeries = meta.apiType in listOf("series", "tv")
        val needsEpisodes = settings.useEpisodes && isSeries

        // Fetch main enrichment and episode enrichment in parallel.
        val (enrichment, episodeMap) = coroutineScope {
            val main = async(Dispatchers.IO) {
                tmdbMetadataService.fetchEnrichment(
                    tmdbId = tmdbId,
                    contentType = tmdbContentType,
                    language = settings.language
                )
            }
            val episodes = if (needsEpisodes) {
                async(Dispatchers.IO) {
                    val seasonNumbers = meta.videos.mapNotNull { it.season }.distinct()
                    tmdbMetadataService.fetchEpisodeEnrichment(
                        tmdbId = tmdbId,
                        seasonNumbers = seasonNumbers,
                        language = settings.language
                    )
                }
            } else null
            main.await() to episodes?.await()
        }

        var updated = meta

        if (enrichment != null && settings.useArtwork) {
            updated = updated.copy(
                background = enrichment.backdrop ?: updated.background,
                logo = enrichment.logo ?: updated.logo
            )
        }

        if (enrichment != null && settings.useBasicInfo) {
            updated = updated.copy(
                name = enrichment.localizedTitle ?: updated.name,
                description = enrichment.description ?: updated.description
            )
            if (enrichment.genres.isNotEmpty()) {
                updated = updated.copy(genres = enrichment.genres)
            }
        }

        // Store TMDB rating separately so it can be shown with its own icon on the details screen.
        if (enrichment?.rating != null && settings.useBasicInfo) {
            _uiState.update { it.copy(tmdbRating = enrichment.rating.toFloat()) }
        }

        if (enrichment != null && settings.useDetails) {
            updated = updated.copy(
                runtime = enrichment.runtimeMinutes?.toString() ?: updated.runtime,
                status = enrichment.status ?: updated.status,
                ageRating = enrichment.ageRating ?: updated.ageRating,
                country = enrichment.countries?.joinToString(", ") ?: updated.country,
                language = enrichment.language ?: updated.language
            )
        }

        if (enrichment != null && settings.useReleaseDates) {
            updated = updated.copy(
                releaseInfo = enrichment.releaseInfo ?: updated.releaseInfo
            )
        }

        if (enrichment != null && settings.useCredits) {
            val peopleCredits = buildList {
                addAll(enrichment.directorMembers)
                addAll(enrichment.writerMembers)
                addAll(enrichment.castMembers)
            }
                .filter { it.name.isNotBlank() }
                .distinctBy { it.tmdbId ?: (it.name.lowercase() + "|" + (it.character ?: "")) }

            if (peopleCredits.isNotEmpty()) {
                updated = updated.copy(
                    castMembers = peopleCredits,
                    cast = enrichment.castMembers.takeIf { it.isNotEmpty() }?.map { it.name } ?: updated.cast
                )
            }
            updated = updated.copy(
                director = if (enrichment.director.isNotEmpty()) enrichment.director else updated.director,
                writer = if (enrichment.writer.isNotEmpty()) enrichment.writer else updated.writer
            )
        }

        if (enrichment != null && settings.useProductions && enrichment.productionCompanies.isNotEmpty()) {
            updated = updated.copy(productionCompanies = enrichment.productionCompanies)
        }

        if (enrichment != null && settings.useNetworks && enrichment.networks.isNotEmpty()) {
            updated = updated.copy(networks = enrichment.networks)
        }

        if (enrichment != null && settings.useTrailers && enrichment.trailers.isNotEmpty()) {
            val mergedTrailers = mergeTrailers(
                existing = updated.trailers,
                incoming = enrichment.trailers
            )
            if (mergedTrailers.isNotEmpty()) {
                updated = updated.copy(
                    trailers = mergedTrailers,
                    trailerYtIds = mergedTrailers.mapNotNull { it.ytId }.distinct()
                )
            }
        }

        if (!episodeMap.isNullOrEmpty()) {
            updated = updated.copy(
                videos = meta.videos.map { video ->
                    val key = if (video.season != null && video.episode != null) video.season to video.episode else null
                    val ep = key?.let { episodeMap[it] }
                    video.copy(
                        title = ep?.title ?: video.title,
                        overview = ep?.overview ?: video.overview,
                        released = if (settings.useReleaseDates) ep?.airDate ?: video.released else video.released,
                        thumbnail = ep?.thumbnail ?: video.thumbnail,
                        runtime = ep?.runtimeMinutes
                    )
                }
            )
        }

        if (enrichment?.collectionId != null) {
            loadCollectionAsync(enrichment.collectionId, enrichment.collectionName, settings)
        }

        return updated
    }

    private fun resolveTmdbContentType(meta: Meta): ContentType {
        val fromRoute = parseApiTypeToContentType(itemType)
        if (fromRoute != null) return fromRoute

        val fromMetaApi = parseApiTypeToContentType(meta.apiType)
        if (fromMetaApi != null) return fromMetaApi

        return when (meta.type) {
            ContentType.SERIES, ContentType.TV -> ContentType.SERIES
            ContentType.MOVIE -> ContentType.MOVIE
            else -> ContentType.MOVIE
        }
    }

    private fun parseApiTypeToContentType(apiType: String?): ContentType? {
        val normalized = apiType?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "movie", "film" -> ContentType.MOVIE
            "series", "tv", "show", "tvshow" -> ContentType.SERIES
            else -> null
        }
    }

    private fun selectSeason(season: Int) {
        val meta = _uiState.value.meta ?: return
        val episodes = getEpisodesForSeason(meta.videos, season)
        _uiState.update {
            it.copy(
                selectedSeason = season,
                episodesForSeason = episodes
            )
        }
    }

    private fun getEpisodesForSeason(videos: List<Video>, season: Int): List<Video> {
        val filtered = videos.filter { it.season == season }
        if (filtered.isNotEmpty()) return filtered.sortedBy { it.episode }
        // Fallback: if no videos match the season (e.g. "other" type with
        // null seasons), return all videos with synthetic season/episode
        // numbers so the episode UI can track watched state.
        if (videos.isNotEmpty() && videos.all { it.season == null }) {
            return videos.mapIndexed { index, video ->
                video.copy(season = 1, episode = index + 1)
            }
        }
        return emptyList()
    }

    private fun reevaluateSeriesWatchedBadge() {
        val contentId = _effectiveContentId.value
        val meta = _uiState.value.meta ?: return
        val isSeries = meta.apiType.equals("series", ignoreCase = true) ||
            meta.apiType.equals("tv", ignoreCase = true)
        if (!isSeries) return

        val episodes = meta.watchableEpisodes()
        if (episodes.isEmpty()) return

        val watchedEpisodes = _uiState.value.watchedEpisodes
        val progressMap = _uiState.value.episodeProgressMap

        val allWatched = episodes.all { video ->
            val key = video.season!! to video.episode!!
            key in watchedEpisodes || progressMap[key]?.isCompleted() == true
        }

        val current = watchedSeriesStateHolder.fullyWatchedSeriesIds.value
        val allIds = buildSet {
            add(contentId)
            meta.id.takeIf { it.isNotBlank() && it != contentId }?.let { add(it) }
            itemId.takeIf { it.isNotBlank() && it != contentId }?.let { add(it) }
        }
        val updated = if (allWatched) current + allIds else current - allIds
        if (updated != current) {
            watchedSeriesStateHolder.updateWithValidation(updated, allIds)
        }
    }

    private fun calculateNextToWatch() {
        val meta = _uiState.value.meta ?: return
        val progressMap = _uiState.value.episodeProgressMap
        val watchedEpisodes = _uiState.value.watchedEpisodes
        nextToWatchJob?.cancel()

        nextToWatchJob = viewModelScope.launch {
            val nextToWatch = computeNextToWatch(meta, progressMap, watchedEpisodes)
            updateNextToWatch(nextToWatch)
        }
    }

    private suspend fun computeNextToWatch(
        meta: Meta,
        progressMap: Map<Pair<Int, Int>, WatchProgress> = emptyMap(),
        watchedEpisodes: Set<Pair<Int, Int>> = emptySet()
    ): NextToWatch {
        val isSeries = meta.apiType in listOf("series", "tv")

        if (!isSeries) {
            val progress = watchProgressRepository.getProgress(_effectiveContentId.value).first()
            return if (progress != null && shouldResumeProgress(progress)) {
                NextToWatch(
                    watchProgress = progress,
                    isResume = true,
                    nextVideoId = meta.id,
                    nextSeason = null,
                    nextEpisode = null,
                    displayText = localizedContext.getString(R.string.detail_btn_resume)
                )
            } else {
                NextToWatch(
                    watchProgress = null,
                    isResume = false,
                    nextVideoId = meta.id,
                    nextSeason = null,
                    nextEpisode = null,
                    displayText = localizedContext.getString(R.string.detail_btn_play)
                )
            }
        }

        val allEpisodes = meta.videos
            .filter { it.season != null && it.episode != null }
            .filter { it.available != false }
            .sortedWith(compareBy({ it.season }, { it.episode }))

        if (allEpisodes.isEmpty()) {
            return NextToWatch(
                watchProgress = null,
                isResume = false,
                nextVideoId = meta.id,
                nextSeason = null,
                nextEpisode = null,
                displayText = localizedContext.getString(R.string.detail_btn_play)
            )
        }

        val nonSpecialEpisodes = allEpisodes.filter { (it.season ?: 0) > 0 }
        val episodePool = if (nonSpecialEpisodes.isNotEmpty()) nonSpecialEpisodes else allEpisodes
        val useFurthestEpisode = layoutPreferenceDataStore.nextUpFromFurthestEpisode.first()
        val latestSeriesProgress = if (useFurthestEpisode) {
            // When using furthest episode mode, consider both progressMap entries
            // AND watchedEpisodes (batch marks) to find the furthest watched episode.
            val furthestFromProgress = progressMap.values
                .filter { it.isCompleted() || shouldResumeProgress(it) }
                .maxWithOrNull(
                    compareBy<WatchProgress> { it.season ?: 0 }
                        .thenBy { it.episode ?: 0 }
                )
            val furthestFromWatched = watchedEpisodes
                .maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
                ?.let { (s, e) ->
                    // Only use watchedEpisodes entry if it's further than progressMap
                    val progressFurthest = furthestFromProgress?.let { (it.season ?: 0) to (it.episode ?: 0) }
                    if (progressFurthest == null || s > progressFurthest.first || (s == progressFurthest.first && e > progressFurthest.second)) {
                        episodePool.firstOrNull { it.season == s && it.episode == e }?.let { video ->
                            WatchProgress(
                                contentId = _effectiveContentId.value,
                                contentType = "series",
                                name = "",
                                poster = null, backdrop = null, logo = null,
                                videoId = video.id,
                                season = video.season,
                                episode = video.episode,
                                episodeTitle = video.title,
                                position = 1L, duration = 1L,
                                lastWatched = System.currentTimeMillis(),
                                progressPercent = 100f
                            )
                        }
                    } else null
                }
            furthestFromWatched ?: furthestFromProgress
        } else {
            progressMap.values
                .sortedWith(
                    compareByDescending<WatchProgress> { it.lastWatched }
                        .thenByDescending { it.season ?: 0 }
                        .thenByDescending { it.episode ?: 0 }
                )
                .firstOrNull()
        }
        val effectiveLatestProgress = latestSeriesProgress ?: run {
            if (watchedEpisodes.isEmpty()) null
            else {
                val watchedWithTimestamps = watchedItemsPreferences
                    .getWatchedEpisodesWithTimestamps(_effectiveContentId.value)
                    .first()
                val highest = watchedWithTimestamps.entries
                    .maxWithOrNull(compareBy<Map.Entry<Pair<Int, Int>, Long>> { it.value }
                        .thenBy { it.key.first }
                        .thenBy { it.key.second })
                highest?.let { (key, watchedAt) ->
                    val (s, e) = key
                    episodePool.firstOrNull { it.season == s && it.episode == e }?.let { video ->
                        WatchProgress(
                            contentId = _effectiveContentId.value,
                            contentType = "series",
                            name = "",
                            poster = null, backdrop = null, logo = null,
                            videoId = video.id,
                            season = video.season,
                            episode = video.episode,
                            episodeTitle = video.title,
                            position = 1L, duration = 1L,
                            lastWatched = watchedAt,
                            progressPercent = 100f
                        )
                    }
                }
            }
        }
        val defaultEpisode = findPreferredDefaultEpisode(meta)?.takeIf { preferred ->
            episodePool.any { it.id == preferred.id }
        }

        return buildNextToWatchFromLatestProgress(
            latestProgress = effectiveLatestProgress,
            episodes = episodePool,
            fallbackProgressMap = progressMap,
            watchedEpisodes = watchedEpisodes,
            metaId = meta.id,
            defaultEpisode = defaultEpisode,
            isRewatchMode = !useFurthestEpisode
        )
    }

    private fun buildNextToWatchFromLatestProgress(
        latestProgress: WatchProgress?,
        episodes: List<Video>,
        fallbackProgressMap: Map<Pair<Int, Int>, WatchProgress>,
        watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
        metaId: String,
        defaultEpisode: Video? = null,
        isRewatchMode: Boolean = false
    ): NextToWatch {
        if (episodes.isEmpty()) {
            return NextToWatch(
                watchProgress = null,
                isResume = false,
                nextVideoId = metaId,
                nextSeason = null,
                nextEpisode = null,
                displayText = localizedContext.getString(R.string.detail_btn_play)
            )
        }

        if (latestProgress?.season != null && latestProgress.episode != null) {
            val season = latestProgress.season
            val episode = latestProgress.episode
            val matchedIndex = episodes.indexOfFirst { it.season == season && it.episode == episode }

            if (shouldResumeProgress(latestProgress)) {
                val matchedEpisode = if (matchedIndex >= 0) episodes[matchedIndex] else null
                return NextToWatch(
                    watchProgress = latestProgress,
                    isResume = true,
                    nextVideoId = matchedEpisode?.id ?: latestProgress.videoId,
                    nextSeason = season,
                    nextEpisode = episode,
                    displayText = localizedContext.getString(R.string.detail_btn_resume_episode, season, episode)
                )
            }

            if (latestProgress.isCompleted() && matchedIndex >= 0) {
                if (isRewatchMode) {
                    // In rewatch mode, simply take the next episode regardless of watched state
                    val next = episodes.getOrNull(matchedIndex + 1)
                    if (next != null) {
                        return NextToWatch(
                            watchProgress = null,
                            isResume = false,
                            nextVideoId = next.id,
                            nextSeason = next.season,
                            nextEpisode = next.episode,
                            displayText = localizedContext.getString(R.string.detail_btn_next_episode, next.season, next.episode)
                        )
                    }
                } else {
                    // Normal mode: skip already watched episodes
                    val nextUnwatched = episodes.subList(matchedIndex + 1, episodes.size)
                        .firstOrNull { candidate ->
                            val s = candidate.season ?: return@firstOrNull true
                            val e = candidate.episode ?: return@firstOrNull true
                            val progress = fallbackProgressMap[s to e]
                            val isWatched = progress?.isCompleted() == true || (s to e) in watchedEpisodes
                            !isWatched
                        }
                    if (nextUnwatched != null) {
                        return NextToWatch(
                            watchProgress = null,
                            isResume = false,
                            nextVideoId = nextUnwatched.id,
                            nextSeason = nextUnwatched.season,
                            nextEpisode = nextUnwatched.episode,
                            displayText = localizedContext.getString(R.string.detail_btn_next_episode, nextUnwatched.season, nextUnwatched.episode)
                        )
                    }
                }
            }
        }

        var resumeEpisode: Video? = null
        var resumeProgress: WatchProgress? = null
        var nextUnwatchedEpisode: Video? = null

        for (episode in episodes) {
            val season = episode.season ?: continue
            val ep = episode.episode ?: continue
            val progress = fallbackProgressMap[season to ep]

            if (progress != null) {
                if (shouldResumeProgress(progress)) {
                    resumeEpisode = episode
                    resumeProgress = progress
                    break
                } else if (progress.isCompleted()) {
                    continue
                }
            }
            // Check watchedEpisodes — covers both batch marks and episodes
            // that haven't propagated to episodeProgressMap yet.
            if (progress == null && (season to ep) in watchedEpisodes) {
                continue
            }
            if (progress == null) {
                if (nextUnwatchedEpisode == null) {
                    nextUnwatchedEpisode = episode
                }
                if (resumeEpisode == null) {
                    break
                }
            }
        }

        return when {
            resumeEpisode != null && resumeProgress != null -> {
                NextToWatch(
                    watchProgress = resumeProgress,
                    isResume = true,
                    nextVideoId = resumeEpisode.id,
                    nextSeason = resumeEpisode.season,
                    nextEpisode = resumeEpisode.episode,
                    displayText = localizedContext.getString(R.string.detail_btn_resume_episode, resumeEpisode.season, resumeEpisode.episode)
                )
            }
            nextUnwatchedEpisode != null -> {
                val hasWatchedSomething = fallbackProgressMap.isNotEmpty()
                val preferredEpisode = if (hasWatchedSomething) nextUnwatchedEpisode else (defaultEpisode ?: nextUnwatchedEpisode)
                val s = preferredEpisode.season
                val e = preferredEpisode.episode
                NextToWatch(
                    watchProgress = null,
                    isResume = false,
                    nextVideoId = preferredEpisode.id,
                    nextSeason = s,
                    nextEpisode = e,
                    displayText = if (hasWatchedSomething) {
                        localizedContext.getString(R.string.detail_btn_next_episode, s, e)
                    } else {
                        localizedContext.getString(R.string.detail_btn_play_episode, s, e)
                    }
                )
            }
            else -> {
                val firstEpisode = episodes.firstOrNull()
                NextToWatch(
                    watchProgress = null,
                    isResume = false,
                    nextVideoId = firstEpisode?.id ?: metaId,
                    nextSeason = firstEpisode?.season,
                    nextEpisode = firstEpisode?.episode,
                    displayText = if (firstEpisode != null) {
                        localizedContext.getString(R.string.detail_btn_play_episode, firstEpisode.season, firstEpisode.episode)
                    } else {
                        localizedContext.getString(R.string.detail_btn_play)
                    }
                )
            }
        }
    }

    private fun findPreferredDefaultEpisode(meta: Meta): Video? {
        val defaultVideoId = meta.behaviorHints?.defaultVideoId ?: return null
        return meta.videos.firstOrNull { it.id == defaultVideoId && it.available != false }
    }

    private fun shouldResumeProgress(progress: WatchProgress): Boolean {
        if (progress.isCompleted()) return false
        if (progress.progressPercentage >= 0.02f) return true

        val hasStartedPlayback = progress.position > 0L ||
            progress.progressPercent?.let { it > 0f } == true
        return hasStartedPlayback
    }

    private fun toggleLibrary() {
        val meta = _uiState.value.meta ?: return
        viewModelScope.launch {
            val input = meta.toLibraryEntryInput()
            val wasInWatchlist = _uiState.value.isInWatchlist
            val wasInLibrary = _uiState.value.isInLibrary
            runCatching {
                libraryRepository.toggleDefault(input)
                val message = if (wasInLibrary || wasInWatchlist) {
                    localizedContext.getString(R.string.detail_removed_from_library)
                } else {
                    localizedContext.getString(R.string.detail_added_to_library)
                }
                showMessage(message)
            }.onFailure { error ->
                showMessage(
                    message = error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_update_library_failed),
                    isError = true
                )
            }
        }
    }

    private fun openListPicker() {
        val meta = _uiState.value.meta ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pickerPending = true, pickerError = null) }
            runCatching {
                val snapshot = libraryRepository.getMembershipSnapshot(meta.toLibraryEntryInput())
                _uiState.update {
                    it.copy(
                        showListPicker = true,
                        pickerMembership = snapshot.listMembership,
                        pickerPending = false,
                        pickerError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        pickerPending = false,
                        pickerError = error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_load_lists_failed),
                        showListPicker = false
                    )
                }
                showMessage(error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_load_lists_failed), isError = true)
            }
        }
    }

    private fun togglePickerMembership(listKey: String) {
        val current = _uiState.value.pickerMembership[listKey] == true
        _uiState.update {
            it.copy(
                pickerMembership = it.pickerMembership.toMutableMap().apply {
                    this[listKey] = !current
                },
                pickerError = null
            )
        }
    }

    private fun savePickerMembership() {
        if (_uiState.value.pickerPending) return
        val meta = _uiState.value.meta ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(pickerPending = true, pickerError = null) }
            runCatching {
                libraryRepository.applyMembershipChanges(
                    item = meta.toLibraryEntryInput(),
                    changes = ListMembershipChanges(
                        desiredMembership = _uiState.value.pickerMembership
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        pickerPending = false,
                        showListPicker = false,
                        pickerError = null
                    )
                }
                showMessage(localizedContext.getString(R.string.detail_lists_updated))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        pickerPending = false,
                        pickerError = error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_update_lists_failed)
                    )
                }
                showMessage(error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_update_lists_failed), isError = true)
            }
        }
    }

    private fun dismissListPicker() {
        _uiState.update {
            it.copy(
                showListPicker = false,
                pickerPending = false,
                pickerError = null
            )
        }
    }

    private fun toggleMovieWatched() {
        val meta = _uiState.value.meta ?: return
        if (meta.apiType != "movie") return
        if (_uiState.value.isMovieWatchedPending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMovieWatchedPending = true) }
            runCatching {
                if (_uiState.value.isMovieWatched) {
                    watchProgressRepository.removeFromHistory(_effectiveContentId.value, videoId = resolveFallbackVideoId())
                    showMessage(localizedContext.getString(R.string.detail_movie_marked_unwatched))
                } else {
                    watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(meta))
                    showMessage(localizedContext.getString(R.string.detail_movie_marked_watched))
                }
            }.onFailure { error ->
                showMessage(
                    message = error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_update_watched_failed),
                    isError = true
                )
            }
            _uiState.update { it.copy(isMovieWatchedPending = false) }
        }
    }

    private fun toggleEpisodeWatched(video: Video) {
        val meta = _uiState.value.meta ?: return
        val season = video.season ?: return
        val episode = video.episode ?: return
        val pendingKey = episodePendingKey(video)
        if (_uiState.value.episodeWatchedPendingKeys.contains(pendingKey)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKey)
            }

            val isWatched = _uiState.value.episodeProgressMap[season to episode]?.isCompleted() == true
                || _uiState.value.watchedEpisodes.contains(season to episode)
            runCatching {
                if (isWatched) {
                    watchProgressRepository.removeFromHistory(_effectiveContentId.value, videoId = video.id, season = season, episode = episode)
                    showMessage(localizedContext.getString(R.string.detail_episode_marked_unwatched))
                } else {
                    watchProgressRepository.markAsCompleted(buildCompletedEpisodeProgress(meta, video))
                    showMessage(localizedContext.getString(R.string.detail_episode_marked_watched))
                }
            }.onFailure { error ->
                showMessage(
                    message = error.message ?: context.getString(com.nuvio.tv.R.string.detail_error_update_episode_watched_failed),
                    isError = true
                )
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKey)
            }
        }
    }

    fun isSeasonFullyWatched(season: Int): Boolean {
        val state = _uiState.value
        val meta = state.meta ?: return false
        // For "other" type, use episodesForSeason which has synthetic season/episode.
        // For regular series, use meta.videos to support checking any season.
        val episodes = if (meta.apiType.equals("other", ignoreCase = true)) {
            state.episodesForSeason.filter { it.season == season && it.episode != null }
        } else {
            meta.videos.filter { it.season == season && it.episode != null }
        }
        if (episodes.isEmpty()) return false
        return episodes.all { video ->
            val s = video.season ?: return@all false
            val e = video.episode ?: return@all false
            state.episodeProgressMap[s to e]?.isCompleted() == true
                || state.watchedEpisodes.contains(s to e)
        }
    }

    private fun markSeasonWatched(season: Int) {
        val meta = _uiState.value.meta ?: return
        suppressSeasonAutoSwitch = true
        viewModelScope.launch {
            val episodes = if (meta.apiType.equals("other", ignoreCase = true)) {
                _uiState.value.episodesForSeason.filter { it.season == season && it.episode != null }
            } else {
                meta.videos.filter { it.season == season && it.episode != null }
            }
            val unwatched = episodes.filter { video ->
                val s = video.season!!
                val e = video.episode!!
                val isWatched = _uiState.value.episodeProgressMap[s to e]?.isCompleted() == true
                    || _uiState.value.watchedEpisodes.contains(s to e)
                !isWatched
            }
            if (unwatched.isEmpty()) {
                showMessage(localizedContext.getString(R.string.detail_all_episodes_watched))
                return@launch
            }

            val pendingKeys = unwatched.map { episodePendingKey(it) }.toSet()
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKeys)
            }

            runCatching {
                val progressList = unwatched.map { buildCompletedEpisodeProgress(meta, it) }
                watchProgressRepository.markAsCompletedBatch(progressList)
            }.onFailure { error ->
                Log.w(TAG, "Failed to batch mark season $season as watched: ${error.message}")
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKeys)
            }
            showMessage(localizedContext.getString(R.string.detail_marked_episodes_watched, unwatched.size))
        }
    }

    private fun markSeasonUnwatched(season: Int) {
        val meta = _uiState.value.meta ?: return
        suppressSeasonAutoSwitch = true
        viewModelScope.launch {
            val episodes = if (meta.apiType.equals("other", ignoreCase = true)) {
                _uiState.value.episodesForSeason.filter { it.season == season && it.episode != null }
            } else {
                meta.videos.filter { it.season == season && it.episode != null }
            }
            val watched = episodes.filter { video ->
                val s = video.season!!
                val e = video.episode!!
                _uiState.value.episodeProgressMap[s to e]?.isCompleted() == true
                    || _uiState.value.watchedEpisodes.contains(s to e)
            }
            if (watched.isEmpty()) {
                showMessage(localizedContext.getString(R.string.detail_no_watched_episodes))
                return@launch
            }

            val pendingKeys = watched.map { episodePendingKey(it) }.toSet()
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKeys)
            }

            runCatching {
                val episodePairs = watched.map { it.season!! to it.episode!! }
                watchProgressRepository.removeFromHistoryBatch(
                    contentId = _effectiveContentId.value,
                    videoId = resolveFallbackVideoId(),
                    episodes = episodePairs
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to batch unmark season $season: ${error.message}")
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKeys)
            }
            showMessage(localizedContext.getString(R.string.detail_marked_episodes_unwatched, watched.size))
        }
    }

    private fun markPreviousEpisodesWatched(video: Video) {
        val meta = _uiState.value.meta ?: return
        val targetSeason = video.season ?: return
        val targetEpisode = video.episode ?: return

        viewModelScope.launch {
            val previous = if (meta.apiType.equals("other", ignoreCase = true)) {
                _uiState.value.episodesForSeason.filter { v ->
                    v.season == targetSeason && v.episode != null && v.episode < targetEpisode
                }
            } else {
                meta.videos.filter { v ->
                    v.season == targetSeason && v.episode != null && v.episode < targetEpisode
                }
            }
            val unwatched = previous.filter { v ->
                val s = v.season!!
                val e = v.episode!!
                val isWatched = _uiState.value.episodeProgressMap[s to e]?.isCompleted() == true
                    || _uiState.value.watchedEpisodes.contains(s to e)
                !isWatched
            }
            if (unwatched.isEmpty()) {
                showMessage(localizedContext.getString(R.string.detail_all_previous_watched))
                return@launch
            }

            val pendingKeys = unwatched.map { episodePendingKey(it) }.toSet()
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKeys)
            }

            runCatching {
                val progressList = unwatched.map { buildCompletedEpisodeProgress(meta, it) }
                watchProgressRepository.markAsCompletedBatch(progressList)
            }.onFailure { error ->
                Log.w(TAG, "Failed to batch mark previous episodes as watched: ${error.message}")
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKeys)
            }
            showMessage(localizedContext.getString(R.string.detail_marked_previous_watched, unwatched.size))
        }
    }

    private fun markPreviousSeasonsWatched(targetSeason: Int) {
        val meta = _uiState.value.meta ?: return
        suppressSeasonAutoSwitch = true
        viewModelScope.launch {
            val episodes = if (meta.apiType.equals("other", ignoreCase = true)) {
                _uiState.value.episodesForSeason.filter { it.season != null && it.season < targetSeason && it.season > 0 && it.episode != null }
            } else {
                meta.videos.filter { it.season != null && it.season < targetSeason && it.season > 0 && it.episode != null }
            }
            val unwatched = episodes.filter { video ->
                val s = video.season!!
                val e = video.episode!!
                val isWatched = _uiState.value.episodeProgressMap[s to e]?.isCompleted() == true
                    || _uiState.value.watchedEpisodes.contains(s to e)
                !isWatched
            }
            if (unwatched.isEmpty()) {
                showMessage(localizedContext.getString(R.string.detail_all_previous_seasons_watched))
                return@launch
            }

            val pendingKeys = unwatched.map { episodePendingKey(it) }.toSet()
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKeys)
            }

            runCatching {
                val progressList = unwatched.map { buildCompletedEpisodeProgress(meta, it) }
                watchProgressRepository.markAsCompletedBatch(progressList)
            }.onFailure { error ->
                Log.w(TAG, "Failed to batch mark previous seasons as watched: ${error.message}")
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKeys)
            }
            showMessage(localizedContext.getString(R.string.detail_marked_episodes_watched, unwatched.size))
        }
    }

    private fun resolveFallbackVideoId(): String? {
        val meta = _uiState.value.meta ?: return null
        return meta.imdbId?.takeIf { it != itemId && it.isNotBlank() }
    }

    private fun buildCompletedMovieProgress(meta: Meta): WatchProgress {
        return WatchProgress(
            contentId = _effectiveContentId.value,
            contentType = meta.apiType,
            name = meta.name,
            poster = meta.poster,
            backdrop = meta.backdropUrl,
            logo = meta.logo,
            videoId = meta.id,
            season = null,
            episode = null,
            episodeTitle = null,
            position = 1L,
            duration = 1L,
            lastWatched = System.currentTimeMillis(),
            progressPercent = 100f
        )
    }

    private fun buildCompletedEpisodeProgress(meta: Meta, video: Video): WatchProgress {
        val runtimeMs = video.runtime?.toLong()?.times(60_000L) ?: 1L
        return WatchProgress(
            contentId = _effectiveContentId.value,
            contentType = meta.apiType,
            name = meta.name,
            poster = meta.poster,
            backdrop = video.thumbnail ?: meta.backdropUrl,
            logo = meta.logo,
            videoId = video.id,
            season = video.season,
            episode = video.episode,
            episodeTitle = video.title,
            position = runtimeMs,
            duration = runtimeMs,
            lastWatched = System.currentTimeMillis(),
            progressPercent = 100f
        )
    }

    private fun episodePendingKey(video: Video): String {
        return "${video.id}:${video.season ?: -1}:${video.episode ?: -1}"
    }

    private fun showMessage(message: String, isError: Boolean = false) {
        _uiState.update { state ->
            if (state.userMessage == message && state.userMessageIsError == isError) {
                state
            } else {
                state.copy(
                    userMessage = message,
                    userMessageIsError = isError
                )
            }
        }
    }

    private fun clearMessage() {
        _uiState.update { state ->
            if (state.userMessage == null && !state.userMessageIsError) {
                state
            } else {
                state.copy(userMessage = null, userMessageIsError = false)
            }
        }
    }

    private fun Meta.toLibraryEntryInput(): LibraryEntryInput {
        val year = Regex("(\\d{4})").find(releaseInfo ?: "")
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val parsedId = id.trim().substringBefore(':')
        val imdbId = parsedId.takeIf { it.startsWith("tt", ignoreCase = true) }
        val tmdbId = when {
            id.startsWith("tmdb:", ignoreCase = true) -> id
                .substringAfter(':')
                .substringBefore(':')
                .toIntOrNull()
            else -> parsedId.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.toIntOrNull()
        }
        return LibraryEntryInput(
            itemId = id,
            itemType = apiType,
            title = name,
            year = year,
            imdbId = imdbId,
            tmdbId = tmdbId,
            poster = poster,
            posterShape = posterShape,
            background = background,
            logo = logo,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            genres = genres
        )
    }

    fun getNextEpisodeInfo(): String? {
        val nextToWatch = _uiState.value.nextToWatch
        return nextToWatch?.displayText
    }

    // --- Trailer ---

    private fun fetchTrailerUrl() {
        val meta = _uiState.value.meta ?: return

        trailerFetchJob?.cancel()
        trailerFetchJob = viewModelScope.launch {
            _uiState.update { state ->
                if (state.isTrailerLoading) state else state.copy(isTrailerLoading = true)
            }

            val year = meta.releaseInfo?.let { info ->
                if (info.isBlank()) null
                else Regex("""\b(19|20)\d{2}\b""").find(info)?.value
            }

            val tmdbId = try {
                tmdbService.ensureTmdbId(meta.id, meta.apiType)
            } catch (_: Exception) {
                null
            }

            val source = if (AppFeaturePolicy.inAppTrailerPlaybackEnabled) {
                trailerService.getTrailerPlaybackSource(
                    title = meta.name,
                    year = year,
                    tmdbId = tmdbId,
                    type = meta.apiType
                ) ?: meta.trailerYtIds.firstOrNull()?.let { ytId ->
                    trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                        youtubeUrl = "https://www.youtube.com/watch?v=$ytId",
                        title = meta.name,
                        year = year
                    )
                }
            } else {
                val externalUrl = if (AppFeaturePolicy.externalTrailerPlaybackEnabled) {
                    trailerService.getExternalTrailerUrl(
                        tmdbId = tmdbId,
                        type = meta.apiType
                    ) ?: meta.trailerYtIds.firstOrNull()?.let { ytId ->
                        "https://www.youtube.com/watch?v=$ytId"
                    }
                } else {
                    null
                }
                externalUrl?.let { com.nuvio.tv.data.trailer.TrailerPlaybackSource(videoUrl = it) }
            }
            val url = source?.videoUrl
            val audioUrl = source?.audioUrl

            _uiState.update { state ->
                if (state.trailerUrl == url &&
                    state.trailerAudioUrl == audioUrl &&
                    !state.isTrailerLoading
                ) {
                    state
                } else {
                    state.copy(
                        trailerUrl = url,
                        trailerAudioUrl = audioUrl,
                        isTrailerLoading = false
                    )
                }
            }

            if (url != null && isPlayButtonFocused && AppFeaturePolicy.inAppTrailerPlaybackEnabled) {
                startIdleTimer()
            }
        }
    }

    private fun startIdleTimer() {
        idleTimerJob?.cancel()
        if (!AppFeaturePolicy.inAppTrailerPlaybackEnabled) return

        val state = _uiState.value
        if (state.trailerUrl == null || state.isTrailerPlaying) return
        if (!trailerAutoplayEnabled) return
        if (trailerHasPlayed) return
        if (!isPlayButtonFocused) return

        idleTimerJob = viewModelScope.launch {
            delay(trailerDelayMs)
            setTrailerPlaybackState(
                isPlaying = true,
                showControls = false,
                hideLogo = false
            )
        }
    }

    private fun handlePlayButtonFocused() {
        if (isPlayButtonFocused) return
        isPlayButtonFocused = true
        startIdleTimer()
    }

    private fun handleUserInteraction() {
        val state = _uiState.value
        val shouldStopAutoTrailer = state.isTrailerPlaying && !state.showTrailerControls
        val hasActiveIdleTimer = idleTimerJob?.isActive == true
        if (!isPlayButtonFocused && !hasActiveIdleTimer && !shouldStopAutoTrailer) {
            return
        }

        idleTimerJob?.cancel()
        isPlayButtonFocused = false

        if (shouldStopAutoTrailer) {
            trailerHasPlayed = true
            setTrailerPlaybackState(
                isPlaying = false,
                showControls = false,
                hideLogo = false
            )
        }
    }

    private fun handleLifecyclePause() {
        idleTimerJob?.cancel()
        isPlayButtonFocused = false
        dismissSharedTrailerOverlay()
        val state = _uiState.value
        if (state.isTrailerPlaying && !state.showTrailerControls) {
            trailerHasPlayed = true
            setTrailerPlaybackState(isPlaying = false, showControls = false, hideLogo = false)
        }
    }

    private fun handleTrailerButtonClick() {
        val state = _uiState.value
        if (state.trailerUrl.isNullOrBlank()) return
        if (!AppFeaturePolicy.inAppTrailerPlaybackEnabled) {
            openExternalTrailer(state.trailerUrl)
            return
        }
        idleTimerJob?.cancel()
        isPlayButtonFocused = false
        setTrailerPlaybackState(
            isPlaying = true,
            showControls = true,
            hideLogo = true
        )
    }

    private fun handleTrailerEnded() {
        trailerHasPlayed = true
        isPlayButtonFocused = false
        setTrailerPlaybackState(
            isPlaying = false,
            showControls = false,
            hideLogo = false
        )
    }

    private fun handleSharedTrailerSelected(trailer: MetaTrailer) {
        val ytId = trailer.ytId?.trim().orEmpty()
        if (ytId.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    isSharedTrailerOverlayVisible = true,
                    isSharedTrailerLoading = false,
                    sharedTrailerUrl = null,
                    sharedTrailerAudioUrl = null,
                    sharedTrailerErrorMessage = localizedContext.getString(R.string.detail_trailer_error),
                    selectedSharedTrailer = trailer
                )
            }
            return
        }

        if (!AppFeaturePolicy.inAppTrailerPlaybackEnabled && AppFeaturePolicy.externalTrailerPlaybackEnabled) {
            openExternalTrailer("https://www.youtube.com/watch?v=$ytId")
            return
        }

        idleTimerJob?.cancel()
        isPlayButtonFocused = false
        if (_uiState.value.isTrailerPlaying) {
            setTrailerPlaybackState(
                isPlaying = false,
                showControls = false,
                hideLogo = false
            )
        }

        _uiState.update { state ->
            state.copy(
                isSharedTrailerOverlayVisible = true,
                isSharedTrailerLoading = true,
                sharedTrailerUrl = null,
                sharedTrailerAudioUrl = null,
                sharedTrailerErrorMessage = null,
                selectedSharedTrailer = trailer
            )
        }

        viewModelScope.launch {
            val meta = _uiState.value.meta
            val year = meta?.releaseInfo?.let { info ->
                if (info.isBlank()) null else Regex("""\b(19|20)\d{2}\b""").find(info)?.value
            }
            val source = trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                youtubeUrl = "https://www.youtube.com/watch?v=$ytId",
                title = meta?.name,
                year = year
            )

            _uiState.update { state ->
                if (state.selectedSharedTrailer?.ytId != trailer.ytId) {
                    state
                } else {
                    state.copy(
                        isSharedTrailerOverlayVisible = true,
                        isSharedTrailerLoading = false,
                        sharedTrailerUrl = source?.videoUrl,
                        sharedTrailerAudioUrl = source?.audioUrl,
                        sharedTrailerErrorMessage = if (source == null) {
                            localizedContext.getString(R.string.detail_trailer_error)
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    private fun openExternalTrailer(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun retrySharedTrailer() {
        _uiState.value.selectedSharedTrailer?.let(::handleSharedTrailerSelected)
    }

    private fun dismissSharedTrailerOverlay() {
        _uiState.update { state ->
            state.copy(
                isSharedTrailerOverlayVisible = false,
                isSharedTrailerLoading = false,
                sharedTrailerUrl = null,
                sharedTrailerAudioUrl = null,
                sharedTrailerErrorMessage = null
            )
        }
    }

    private fun mergeTrailers(existing: List<MetaTrailer>, incoming: List<MetaTrailer>): List<MetaTrailer> {
        if (existing.isEmpty()) return incoming.distinctBy { it.ytId ?: it.name ?: it.type ?: "" }
        if (incoming.isEmpty()) return existing

        val merged = LinkedHashMap<String, MetaTrailer>()

        fun keyOf(trailer: MetaTrailer): String {
            val yt = trailer.ytId?.trim().orEmpty()
            if (yt.isNotBlank()) return "yt:$yt"
            val fallback = listOf(trailer.name, trailer.type, trailer.lang)
                .joinToString("|") { it?.trim()?.lowercase().orEmpty() }
            return "meta:$fallback"
        }

        existing.forEach { trailer -> merged.putIfAbsent(keyOf(trailer), trailer) }
        incoming.forEach { trailer -> merged.putIfAbsent(keyOf(trailer), trailer) }
        return merged.values.toList()
    }

    override fun onCleared() {
        super.onCleared()
        idleTimerJob?.cancel()
        trailerFetchJob?.cancel()
        nextToWatchJob?.cancel()
    }
}
