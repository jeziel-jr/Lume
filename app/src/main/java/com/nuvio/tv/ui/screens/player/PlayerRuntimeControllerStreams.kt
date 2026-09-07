package com.nuvio.tv.ui.screens.player

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Hard ceiling for next-episode stream search to prevent hanging forever. */
private const val NEXT_EPISODE_HARD_TIMEOUT_MS = 120_000L

/**
 * Schedules incremental badge matching for source streams in the background.
 * Only processes addon groups not yet badged, emits UI update every 5 streams.
 */
internal fun PlayerRuntimeController.scheduleEpisodeBadgeApplication() {
    episodeBadgeJob?.cancel()
    episodeBadgeJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
        val streams = _uiState.value.episodeAllStreams
        if (streams.isEmpty()) return@launch
        val group = com.nuvio.tv.domain.model.AddonStreams(addonName = "", addonLogo = null, streams = streams)
        val badged = streamBadgePresentation.apply(listOf(group))
        val badgedStreams = badged.flatMap { it.streams }
        if (badgedStreams == streams) return@launch
        _uiState.update { current ->
            val selectedAddon = current.episodeSelectedAddonFilter
            current.copy(
                episodeAllStreams = badgedStreams,
                episodeFilteredStreams = if (selectedAddon == null) badgedStreams else badgedStreams.filter { it.addonName == selectedAddon }
            )
        }
    }
}

internal fun PlayerRuntimeController.showEpisodesPanel() {
    _uiState.update {
        it.copy(
            showEpisodesPanel = true,
            showControls = true,
            showAudioOverlay = false,
            showSubtitleOverlay = false,
            showSubtitleStylePanel = false,
            showSpeedDialog = false,
            showMoreDialog = false
        )
    }

    val desiredSeason = currentSeason ?: _uiState.value.episodesSelectedSeason
    if (_uiState.value.episodesAll.isNotEmpty() && desiredSeason != null) {
        selectEpisodesSeason(desiredSeason)
    } else {
        loadEpisodesIfNeeded()
    }
}

private fun PlayerRuntimeController.applySelectedStreamState(
    stream: Stream,
    url: String,
    headers: Map<String, String>
) {
    val (cleanUrl, mergedHeaders) = PlayerMediaSourceFactory.extractUserInfoAuth(url, headers)
    currentStreamUrl = cleanUrl
    currentHeaders = mergedHeaders
    currentFilename = stream.behaviorHints?.filename ?: navigationArgs.filename
    currentStreamResponseHeaders = stream.behaviorHints?.proxyHeaders?.response.orEmpty()
    currentStreamMimeType = PlayerMediaSourceFactory.inferMimeType(
        url = cleanUrl,
        filename = currentFilename,
        responseHeaders = currentStreamResponseHeaders
    )
    applyStreamMetadata(stream)
}

/**
 * Apply stream metadata shared by every played stream.
 * Ensures binge-group, addon info, and video hints are always set regardless
 * of stream type — critical for next-episode binge matching.
 */
private fun PlayerRuntimeController.applyStreamMetadata(stream: Stream) {
    currentStreamBingeGroup = stream.behaviorHints?.bingeGroup
    currentVideoHash = stream.behaviorHints?.videoHash
    currentVideoSize = stream.behaviorHints?.videoSize
    currentAddonName = stream.addonName
    currentAddonLogo = stream.addonLogo
    currentStreamDescription = stream.description
    currentVideoCodec = null
    currentVideoWidth = null
    currentVideoHeight = null
    currentVideoBitrate = null

    // Persist binge group per content so subsequent episode plays
    // (from CW, Details, or next-episode) can reuse the same source group.
    val bg = stream.behaviorHints?.bingeGroup
    val cid = contentId
    if (cid != null) {
        scope.launch(kotlinx.coroutines.NonCancellable) {
            bingeGroupCacheDataStore.replace(cid, bg)
        }
    }
}

private fun PlayerRuntimeController.persistSelectedStreamForReuse(
    stream: Stream,
    url: String,
    headers: Map<String, String>
) {
    if (!streamReuseLastLinkEnabled) return

    val key = streamCacheKey ?: return
    val streamName = (stream.name?.takeIf { it.isNotBlank() } ?: stream.addonName)?.takeIf { it.isNotBlank() }
        ?: title

    scope.launch {
        streamLinkCacheDataStore.save(
            contentKey = key,
            url = url,
            streamName = streamName,
            headers = headers,
            filename = currentFilename,
            videoHash = currentVideoHash,
            videoSize = currentVideoSize,
            bingeGroup = stream.behaviorHints?.bingeGroup,
            contentLanguage = contentLanguage,
            year = year
        )
    }
}

private fun PlayerRuntimeController.openExternalStreamInBrowser(stream: Stream): Boolean {
    if (!stream.isExternal()) return false

    val externalUrl = stream.getStreamUrl()
    if (externalUrl.isNullOrBlank()) {
        _uiState.update {
            it.copy(episodeStreamsError = context.getString(com.nuvio.tv.R.string.player_stream_error_invalid_external_url))
        }
        return true
    }

    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching {
        context.startActivity(browserIntent)
    }.onSuccess {
        _uiState.update {
            it.copy(
                showEpisodesPanel = false,
                showEpisodeStreams = false,
                isLoadingEpisodeStreams = false,
                episodeStreamsError = null
            )
        }
    }.onFailure { error ->
        _uiState.update {
            it.copy(episodeStreamsError = error.message ?: context.getString(com.nuvio.tv.R.string.player_stream_error_open_external_link_failed))
        }
    }

    return true
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerRuntimeController.dismissEpisodesPanel() {
    episodeStreamsScope?.cancel()
    episodeStreamsScope = null
    episodeStreamsJob = null
    _uiState.update {
        it.copy(
            showEpisodesPanel = false,
            showEpisodeStreams = false,
            isLoadingEpisodeStreams = false
        )
    }
    scheduleHideControls()
}

internal fun PlayerRuntimeController.selectEpisodesSeason(season: Int) {
    val all = _uiState.value.episodesAll
    if (all.isEmpty()) return

    val seasons = _uiState.value.episodesAvailableSeasons
    if (seasons.isNotEmpty() && season !in seasons) return

    val episodesForSeason = all
        .filter { (it.season ?: -1) == season }
        .sortedWith(compareBy<Video> { it.episode ?: Int.MAX_VALUE }.thenBy { it.title })

    _uiState.update {
        it.copy(
            episodesSelectedSeason = season,
            episodes = episodesForSeason
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerRuntimeController.loadEpisodesIfNeeded() {
    val type = contentType
    val id = contentId
    if (type.isNullOrBlank() || id.isNullOrBlank()) return
    if (type !in listOf("series", "tv")) return
    if (_uiState.value.episodesAll.isNotEmpty() || _uiState.value.isLoadingEpisodes) return

    // Series episode lists come straight from TMDB (the addon meta layer that
    // previously supplied them has been removed). Use the startup prefetch when
    // it already arrived, otherwise fetch on demand.
    val cachedVideos = metaVideos
    if (cachedVideos.isNotEmpty()) {
        publishEpisodesFromSeriesVideos(cachedVideos)
        return
    }

    _uiState.update { it.copy(isLoadingEpisodes = true, episodesError = null) }
    scope.launch {
        val videos = fetchTmdbSeriesVideosForEpisodes()
        if (videos.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoadingEpisodes = false,
                    episodesError = it.episodesError
                        ?: context.getString(com.nuvio.tv.R.string.panel_failed_load_episodes)
                )
            }
            return@launch
        }
        if (metaVideos.isEmpty()) {
            metaVideos = videos
        }
        publishEpisodesFromSeriesVideos(videos)
    }
}

private suspend fun PlayerRuntimeController.fetchTmdbSeriesVideosForEpisodes(): List<Video> {
    val tmdbId = tmdbSeriesFallbackId(
        id = contentId,
        type = contentType,
        legacyVideos = emptyList(),
    ) ?: return emptyList()
    return try {
        val language = tmdbSettingsDataStore.settings.first().language
        tmdbMetadataService.fetchSeriesVideos(tmdbId = tmdbId, language = language)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }
}

private fun PlayerRuntimeController.publishEpisodesFromSeriesVideos(videos: List<Video>) {
    val allEpisodes = videos
        .sortedWith(
            compareBy<Video> { it.season ?: Int.MAX_VALUE }
                .thenBy { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.title }
        )

    val seasons = allEpisodes
        .mapNotNull { it.season }
        .distinct()
        .sorted()

    val preferredSeason = when {
        currentSeason != null && seasons.contains(currentSeason) -> currentSeason
        initialSeason != null && seasons.contains(initialSeason) -> initialSeason
        else -> seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
    }

    val selectedSeason = preferredSeason ?: 1
    val episodesForSeason = allEpisodes
        .filter { (it.season ?: -1) == selectedSeason }
        .sortedWith(compareBy<Video> { it.episode ?: Int.MAX_VALUE }.thenBy { it.title })

    _uiState.update {
        it.copy(
            isLoadingEpisodes = false,
            episodesAll = allEpisodes,
            episodesAvailableSeasons = seasons,
            episodesSelectedSeason = selectedSeason,
            episodes = episodesForSeason,
            episodesError = null
        )
    }
}

internal fun PlayerRuntimeController.loadStreamsForEpisode(video: Video) {
    loadStreamsForEpisode(video = video, forceRefresh = false)
}

internal fun PlayerRuntimeController.buildEpisodeRequestKey(type: String, video: Video): String {
    return "$type|${video.id}|${video.season ?: -1}|${video.episode ?: -1}"
}

internal fun PlayerRuntimeController.loadStreamsForEpisode(video: Video, forceRefresh: Boolean) {
    val type = contentType
    if (type.isNullOrBlank()) {
        _uiState.update { it.copy(episodeStreamsError = context.getString(com.nuvio.tv.R.string.player_stream_error_missing_content_type)) }
        return
    }

    val requestKey = buildEpisodeRequestKey(type = type, video = video)
    val state = _uiState.value
    val hasCachedPayload = state.episodeAllStreams.isNotEmpty() || state.episodeStreamsError != null
    if (!forceRefresh && requestKey == episodeStreamsCacheRequestKey && hasCachedPayload) {
        _uiState.update {
            it.copy(
                showEpisodeStreams = true,
                isLoadingEpisodeStreams = false,
                episodeStreamsForVideoId = video.id,
                episodeStreamsSeason = video.season,
                episodeStreamsEpisode = video.episode,
                episodeStreamsTitle = video.title
            )
        }
        return
    }

    val targetChanged = requestKey != episodeStreamsCacheRequestKey
    episodeStreamsScope?.cancel()
    episodeStreamsScope = null
    episodeStreamsJob = null
    val newScope = kotlinx.coroutines.CoroutineScope(scope.coroutineContext + kotlinx.coroutines.SupervisorJob())
    episodeStreamsScope = newScope
    episodeStreamsJob = newScope.launch {
        episodeStreamsCacheRequestKey = requestKey
        val previousAddonFilter = _uiState.value.episodeSelectedAddonFilter
        _uiState.update {
            it.copy(
                showEpisodeStreams = true,
                isLoadingEpisodeStreams = true,
                episodeStreamsError = null,
                episodeAllStreams = if (forceRefresh || targetChanged) emptyList() else it.episodeAllStreams,
                episodeSelectedAddonFilter = if (forceRefresh || targetChanged) null else it.episodeSelectedAddonFilter,
                episodeFilteredStreams = if (forceRefresh || targetChanged) emptyList() else it.episodeFilteredStreams,
                episodeAvailableAddons = if (forceRefresh || targetChanged) emptyList() else it.episodeAvailableAddons,
                episodeStreamsForVideoId = video.id,
                episodeStreamsSeason = video.season,
                episodeStreamsEpisode = video.episode,
                episodeStreamsTitle = video.title
            )
        }

        val installedAddonOrder = emptyList<String>()

        streamRepository.getStreamsFromAllAddons(
            type = type,
            videoId = video.id,
            season = video.season,
            episode = video.episode
        ).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val addonStreams = StreamAutoPlaySelector.orderAddonStreams(result.data, installedAddonOrder)
                    val allStreams = addonStreams.flatMap { it.streams }
                    val availableAddons = addonStreams.map { it.addonName }
                    val selectedAddon = previousAddonFilter?.takeIf { it in availableAddons }
                    val filteredStreams = if (selectedAddon == null) {
                        allStreams
                    } else {
                        allStreams.filter { it.addonName == selectedAddon }
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodeStreams = false,
                            episodeAllStreams = allStreams,
                            episodeSelectedAddonFilter = selectedAddon,
                            episodeFilteredStreams = filteredStreams,
                            episodeAvailableAddons = availableAddons,
                            episodeStreamsError = null
                        )
                    }
                    scheduleEpisodeBadgeApplication()
                }

                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodeStreams = false,
                            episodeStreamsError = result.message
                        )
                    }
                }

                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoadingEpisodeStreams = true) }
                }
            }
        }
    }
}

private fun List<Stream>.filterByAddon(addonName: String?): List<Stream> =
    if (addonName == null) {
        this
    } else {
        filter { it.addonName == addonName }
    }

internal fun PlayerRuntimeController.reloadEpisodeStreams() {
    val state = _uiState.value
    val targetVideoId = state.episodeStreamsForVideoId
    val targetVideo = sequenceOf(
        state.episodes.firstOrNull { it.id == targetVideoId },
        state.episodesAll.firstOrNull { it.id == targetVideoId },
        state.episodes.firstOrNull {
            it.season == state.episodeStreamsSeason && it.episode == state.episodeStreamsEpisode
        },
        state.episodesAll.firstOrNull {
            it.season == state.episodeStreamsSeason && it.episode == state.episodeStreamsEpisode
        }
    ).firstOrNull { it != null }

    if (targetVideo != null) {
        loadStreamsForEpisode(video = targetVideo, forceRefresh = true)
    }
}

internal fun PlayerRuntimeController.switchToEpisodeStream(
    stream: Stream,
    forcedTargetVideo: Video? = null,
    isAutoPlay: Boolean = false
) {
    if (openExternalStreamInBrowser(stream = stream)) {
        return
    }

    val url = stream.getStreamUrl()
    if (url.isNullOrBlank()) {
        _uiState.update { it.copy(episodeStreamsError = context.getString(com.nuvio.tv.R.string.player_stream_error_invalid_url)) }
        return
    }

    consecutiveAutoPlayCount = nextConsecutiveAutoPlayCount(
        currentCount = consecutiveAutoPlayCount,
        isAutoPlay = isAutoPlay,
    )

    nextEpisodeAutoPlayJob?.cancel()
    nextEpisodeAutoPlayJob = null
    stillWatchingPromptJob?.cancel()
    stillWatchingPromptJob = null

    flushPlaybackSnapshotForSwitchOrExit()

    // Pause current playback immediately so the old stream doesn't continue
    // playing audio/video in the background while the new episode is being prepared.
    _exoPlayer?.stop()

    val newHeaders = PlayerMediaSourceFactory.sanitizeHeaders(
        stream.behaviorHints?.proxyHeaders?.request
    )
    val targetVideo = forcedTargetVideo
        ?: _uiState.value.episodes.firstOrNull { it.id == _uiState.value.episodeStreamsForVideoId }

    currentStreamUrl = url
    currentHeaders = newHeaders
    currentStreamBingeGroup = stream.behaviorHints?.bingeGroup
    currentVideoHash = stream.behaviorHints?.videoHash
    currentVideoSize = stream.behaviorHints?.videoSize
    currentFilename = stream.behaviorHints?.filename
        ?: url.substringBefore('?').substringAfterLast('/', "")
            .takeIf { it.isNotBlank() && it.contains('.') }
    applySelectedStreamState(
        stream = stream,
        url = url,
        headers = newHeaders
    )
    persistedTrackPreference = null
    subtitleDisabledByPersistedPreference = false
    hasRetriedCurrentStreamAfter416 = false
    resetErrorRetryState()
    currentVideoId = targetVideo?.id ?: _uiState.value.episodeStreamsForVideoId ?: currentVideoId
    currentSeason = targetVideo?.season ?: _uiState.value.episodeStreamsSeason ?: currentSeason
    currentEpisode = targetVideo?.episode ?: _uiState.value.episodeStreamsEpisode ?: currentEpisode
    currentEpisodeTitle = targetVideo?.title ?: _uiState.value.episodeStreamsTitle ?: currentEpisodeTitle
    persistSelectedStreamForReuse(stream = stream, url = url, headers = newHeaders)
    lastSavedPosition = 0L

    _uiState.update {
        it.copy(
            isBuffering = true,
            error = null,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            currentVideoId = currentVideoId,
            currentEpisodeTitle = currentEpisodeTitle,
            currentStreamName = stream.name ?: stream.addonName,
            currentStreamUrl = url,
            currentStreamAddonName = stream.addonName,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            selectedAudioTrackIndex = -1,
            selectedSubtitleTrackIndex = -1,
            showEpisodesPanel = false,
            showEpisodeStreams = false,
            isLoadingEpisodeStreams = false,
            episodeStreamsError = null,

            activeSkipInterval = null,
            skipIntervalDismissed = false,
            postPlayMode = null,
            postPlayDismissedForCurrentEpisode = true,
            playbackEnded = false,
        )
    }
    showStreamSourceIndicator(stream)
    recomputeNextEpisode(resetVisibility = true)

    updateEpisodeDescription()

    skipIntervals = emptyList()
    skipIntroFetchedKey = null
    lastActiveSkipType = null
    autoSkippedIntervalKeys.clear()

    fetchSkipIntervals(contentId, currentSeason, currentEpisode)
    preparePlaybackBeforeStart(
        url = url,
        headers = newHeaders,
        loadSavedProgress = true
    )
}

internal fun PlayerRuntimeController.showEpisodeStreamPicker(video: Video, forceRefresh: Boolean = true) {
    _uiState.update {
        it.copy(
            showEpisodesPanel = true,
            showEpisodeStreams = true,
            showControls = true,
            showAudioOverlay = false,
            showSubtitleOverlay = false,
            showSubtitleStylePanel = false,
            showSpeedDialog = false,
            showMoreDialog = false,
            episodesSelectedSeason = video.season ?: it.episodesSelectedSeason
        )
    }
    loadEpisodesIfNeeded()
    loadStreamsForEpisode(video = video, forceRefresh = forceRefresh)
}

internal fun PlayerRuntimeController.playNextEpisode(userInitiated: Boolean = false) {
    val nextVideo = nextEpisodeVideo ?: return
    val type = contentType ?: return

    val state = _uiState.value
    val nextInfo = state.nextEpisode ?: return
    if (!nextInfo.hasAired) {
        return
    }
    val activeAutoPlay = state.postPlayMode as? PostPlayMode.AutoPlay
    if (activeAutoPlay != null &&
        (activeAutoPlay.searching || activeAutoPlay.countdownSec != null)
    ) {
        return
    }

    val episodeForMode = state.nextEpisode ?: nextInfo
    _uiState.update {
        it.copy(
            postPlayMode = PostPlayMode.AutoPlay(
                nextEpisode = episodeForMode,
                searching = true,
            ),
        )
    }

    nextEpisodeAutoPlayJob?.cancel()
    nextEpisodeAutoPlayJob = scope.launch {
        try {
            val playerSettings = playerSettingsDataStore.playerSettings.first()
            val shouldAutoSelectInManualMode =
                playerSettings.streamAutoPlayMode == StreamAutoPlayMode.MANUAL &&
                    (
                        playerSettings.streamAutoPlayNextEpisodeEnabled ||
                            playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode
                        )
            val bingeGroupOnlyManualMode =
                shouldAutoSelectInManualMode &&
                    !playerSettings.streamAutoPlayNextEpisodeEnabled &&
                    playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode
            if (playerSettings.streamAutoPlayMode == StreamAutoPlayMode.MANUAL && !shouldAutoSelectInManualMode) {
                _uiState.update {
                    it.copy(
                        postPlayMode = null,
                        postPlayDismissedForCurrentEpisode = true,
                    )
                }
                showEpisodeStreamPicker(video = nextVideo, forceRefresh = true)
                return@launch
            }

            val installedAddonOrder = emptyList<String>()
            val effectiveMode = if (shouldAutoSelectInManualMode) {
                StreamAutoPlayMode.FIRST_STREAM
            } else {
                playerSettings.streamAutoPlayMode
            }
            val effectiveSource = if (shouldAutoSelectInManualMode) {
                StreamAutoPlaySource.ALL_SOURCES
            } else {
                playerSettings.streamAutoPlaySource
            }
            val effectiveSelectedAddons = if (shouldAutoSelectInManualMode) {
                emptySet()
            } else {
                playerSettings.streamAutoPlaySelectedAddons
            }
            val effectiveSelectedPlugins = if (shouldAutoSelectInManualMode) {
                emptySet()
            } else {
                playerSettings.streamAutoPlaySelectedPlugins
            }
            val effectiveRegex = if (shouldAutoSelectInManualMode) {
                ""
            } else {
                playerSettings.streamAutoPlayRegex
            }
            var selectedStream: Stream? = null
            var lastSuccessData: List<AddonStreams>? = null
            var autoSelectTriggered = false
            var timeoutElapsed = false
            var lastError: NetworkResult.Error? = null
            // Completed as soon as a stream is selected or the addon search
            // finishes, so the waiting code below resumes without polling.
            val searchSettled = CompletableDeferred<Unit>()

            fun trySelectStream(data: List<AddonStreams>): Stream? {
                val orderedStreams = StreamAutoPlaySelector.orderAddonStreams(data, installedAddonOrder)
                val allStreams = orderedStreams.flatMap { it.streams }
                return StreamAutoPlaySelector.selectAutoPlayStream(
                    streams = allStreams,
                    mode = effectiveMode,
                    regexPattern = effectiveRegex,
                    source = effectiveSource,
                    installedAddonNames = installedAddonOrder.toSet(),
                    selectedAddons = effectiveSelectedAddons,
                    selectedPlugins = effectiveSelectedPlugins,
                    preferredBingeGroup = if (playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode) {
                        currentStreamBingeGroup
                    } else {
                        null
                    },
                    preferBingeGroupInSelection = playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode,
                    bingeGroupOnly = bingeGroupOnlyManualMode
                )
            }

            fun tryBingeGroupOnly(data: List<AddonStreams>): Stream? {
                if (currentStreamBingeGroup == null || !playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode) return null
                val orderedStreams = StreamAutoPlaySelector.orderAddonStreams(data, installedAddonOrder)
                val allStreams = orderedStreams.flatMap { it.streams }
                return StreamAutoPlaySelector.selectAutoPlayStream(
                    streams = allStreams,
                    mode = effectiveMode,
                    regexPattern = effectiveRegex,
                    source = effectiveSource,
                    installedAddonNames = installedAddonOrder.toSet(),
                    selectedAddons = effectiveSelectedAddons,
                    selectedPlugins = effectiveSelectedPlugins,
                    preferredBingeGroup = currentStreamBingeGroup,
                    preferBingeGroupInSelection = true,
                    bingeGroupOnly = true
                )
            }

            fun recordSelection(candidate: Stream) {
                autoSelectTriggered = true
                selectedStream = candidate
                searchSettled.complete(Unit)
            }

            val timeoutSeconds = playerSettings.streamAutoPlayTimeoutSeconds

            val innerJob = launch {
                streamRepository.getStreamsFromAllAddons(
                    type = type,
                    videoId = nextVideo.id,
                    season = nextVideo.season,
                    episode = nextVideo.episode
                ).collect { result ->
                    when (result) {
                        is NetworkResult.Success -> {
                            lastSuccessData = result.data
                            if (!autoSelectTriggered) {
                                val candidate = when {
                                    timeoutElapsed -> trySelectStream(result.data)
                                    playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode ->
                                        tryBingeGroupOnly(result.data)
                                    else -> null
                                }
                                if (candidate != null) recordSelection(candidate)
                            }
                        }
                        is NetworkResult.Error -> lastError = result
                        NetworkResult.Loading -> Unit
                    }
                }
                // Every addon has responded: take whatever matched, then settle so
                // the waiting code below resumes even if nothing was selected.
                if (!autoSelectTriggered) {
                    lastSuccessData?.let { data -> trySelectStream(data)?.let { recordSelection(it) } }
                }
                searchSettled.complete(Unit)
            }

            val timeoutMs = timeoutSeconds * 1_000L
            if (PlayerSettings.isBoundedTimeout(timeoutSeconds)) {
                // Wait for the timeout, resuming as soon as a stream is settled.
                withTimeoutOrNull(timeoutMs) { searchSettled.await() }
                timeoutElapsed = true
                if (!autoSelectTriggered) {
                    val data = lastSuccessData
                    if (data != null) {
                        // Streams arrived: full select once. If nothing matches,
                        // respect the timeout and stop (the caller shows the picker).
                        trySelectStream(data)?.let { recordSelection(it) }
                    } else {
                        // No addon responded yet: keep waiting for the first usable
                        // result, bounded so we never hang indefinitely.
                        withTimeoutOrNull(timeoutMs) { searchSettled.await() }
                        if (!autoSelectTriggered) {
                            lastSuccessData?.let { trySelectStream(it)?.let { s -> recordSelection(s) } }
                        }
                    }
                }
                innerJob.cancel()
            } else if (timeoutSeconds == 0) {
                timeoutElapsed = true
                withTimeoutOrNull(NEXT_EPISODE_HARD_TIMEOUT_MS) { searchSettled.await() }
                if (!autoSelectTriggered) {
                    lastSuccessData?.let { data -> trySelectStream(data)?.let { recordSelection(it) } }
                }
                innerJob.cancel()
            } else {
                withTimeoutOrNull(NEXT_EPISODE_HARD_TIMEOUT_MS) { searchSettled.await() }
                if (!autoSelectTriggered) {
                    lastSuccessData?.let { data -> trySelectStream(data)?.let { recordSelection(it) } }
                }
                innerJob.cancel()
            }

            // Only URL-backed (or browser-openable external) streams are playable.
            val streamToPlay = selectedStream?.takeIf { it.isExternal() || it.getStreamUrl() != null }
            if (streamToPlay != null) {
                val sourceName = (streamToPlay.name?.takeIf { it.isNotBlank() } ?: streamToPlay.addonName).trim()
                for (remaining in 3 downTo 1) {
                    _uiState.update { current ->
                        val episodeForMode = current.nextEpisode ?: nextInfo
                        current.copy(
                            postPlayMode = PostPlayMode.AutoPlay(
                                nextEpisode = episodeForMode,
                                searching = false,
                                sourceName = sourceName,
                                countdownSec = remaining,
                            ),
                        )
                    }
                    delay(1000)
                }
                _uiState.update {
                    it.copy(
                        postPlayMode = null,
                        postPlayDismissedForCurrentEpisode = true,
                        playbackEnded = false,
                    )
                }
                switchToEpisodeStream(
                    stream = streamToPlay,
                    forcedTargetVideo = nextVideo,
                    isAutoPlay = !userInitiated
                )
            } else {
                _uiState.update {
                    it.copy(
                        postPlayMode = null,
                        postPlayDismissedForCurrentEpisode = true,
                    )
                }
                showEpisodeStreamPicker(
                    video = nextVideo,
                    forceRefresh = lastError != null || selectedStream != null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    postPlayMode = null,
                    postPlayDismissedForCurrentEpisode = true,
                )
            }
            showEpisodeStreamPicker(video = nextVideo, forceRefresh = false)
        }
    }
}
