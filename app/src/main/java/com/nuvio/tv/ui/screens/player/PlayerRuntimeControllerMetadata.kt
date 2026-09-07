package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.AutoSkipSegmentType
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PlayerMetadataRequestKey(
    val contentId: String,
    val contentType: String,
    val videoId: String?,
    val season: Int?,
    val episode: Int?,
)

internal fun tmdbSeriesFallbackId(
    id: String?,
    type: String?,
    legacyVideos: List<Video>,
): String? {
    if (legacyVideos.isNotEmpty()) return null

    val normalizedType = type?.trim()?.lowercase() ?: return null
    if (normalizedType != "series" && normalizedType != "tv") return null

    val numericId = id
        ?.trim()
        ?.takeIf { it.startsWith("tmdb:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.substringBefore(':')
        ?.trim()
        ?: return null

    return numericId.takeIf { it.toIntOrNull()?.let { value -> value > 0 } == true }
}

internal fun shouldPublishTmdbSeriesVideos(
    requestKey: PlayerMetadataRequestKey,
    activeRequestKey: PlayerMetadataRequestKey?,
    currentMetaVideos: List<Video>,
    tmdbVideos: List<Video>,
): Boolean = requestKey == activeRequestKey &&
    currentMetaVideos.isEmpty() &&
    tmdbVideos.isNotEmpty()

internal fun PlayerRuntimeController.fetchMetaDetails(id: String?, type: String?) {
    if (isLivePlayback) return
    if (id.isNullOrBlank() || type.isNullOrBlank()) return

    val requestKey = metadataRequestKey(id = id, type = type)
    scope.launch {
        // Series episode lists come straight from TMDB (the addon meta layer that
        // previously supplied them has been removed).
        fetchTmdbSeriesVideosIfNeeded(
            id = id,
            type = type,
            legacyVideos = emptyList(),
            requestKey = requestKey,
        )
    }

    scope.launch {
        enrichDescriptionFromTmdb(id, type)
    }
}

private suspend fun PlayerRuntimeController.fetchTmdbSeriesVideosIfNeeded(
    id: String,
    type: String,
    legacyVideos: List<Video>,
    requestKey: PlayerMetadataRequestKey,
) {
    val tmdbId = tmdbSeriesFallbackId(
        id = id,
        type = type,
        legacyVideos = legacyVideos,
    ) ?: return

    val tmdbVideos = try {
        val language = tmdbSettingsDataStore.settings.first().language
        tmdbMetadataService.fetchSeriesVideos(tmdbId = tmdbId, language = language)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }

    if (!shouldPublishTmdbSeriesVideos(
            requestKey = requestKey,
            activeRequestKey = currentMetadataRequestKey(),
            currentMetaVideos = metaVideos,
            tmdbVideos = tmdbVideos,
        )
    ) {
        return
    }

    metaVideos = tmdbVideos
    recomputeNextEpisode(resetVisibility = false)
}

private fun PlayerRuntimeController.metadataRequestKey(
    id: String,
    type: String,
): PlayerMetadataRequestKey = PlayerMetadataRequestKey(
    contentId = id,
    contentType = type.trim().lowercase(),
    videoId = currentVideoId,
    season = currentSeason,
    episode = currentEpisode,
)

private fun PlayerRuntimeController.currentMetadataRequestKey(): PlayerMetadataRequestKey? {
    val id = contentId ?: return null
    val type = contentType ?: return null
    return metadataRequestKey(id = id, type = type)
}

internal fun PlayerRuntimeController.updateEpisodeDescription() {
    val overview = metaVideos.firstOrNull { video ->
        video.season == currentSeason && video.episode == currentEpisode
    }?.overview

    if (!overview.isNullOrBlank()) {
        _uiState.update { it.copy(description = overview) }
    }

    // Push episode metadata to the MediaSession so Google Home shows the new episode.
    updateMediaSessionMetadata()

    // Re-enrich from TMDB for the new episode.
    scope.launch {
        enrichDescriptionFromTmdb(contentId, contentType)
    }
}

private suspend fun PlayerRuntimeController.enrichDescriptionFromTmdb(id: String?, type: String?) {
    if (isLivePlayback) return
    if (id.isNullOrBlank() || type.isNullOrBlank()) return
    val settings = tmdbSettingsDataStore.settings.first()
    if (!settings.enabled || !settings.useBasicInfo) return

    val tmdbId = runCatching { tmdbService.ensureTmdbId(id, type) }.getOrNull() ?: return
    val contentType = when (type.lowercase()) {
        "series", "tv" -> ContentType.SERIES
        else -> ContentType.MOVIE
    }
    val enrichment = runCatching {
        tmdbMetadataService.fetchEnrichment(
            tmdbId = tmdbId,
            contentType = contentType,
            language = settings.language
        )
    }.getOrNull() ?: return

    val isSeries = type.lowercase() in listOf("series", "tv")
    val season = currentSeason
    val episode = currentEpisode

    // For series, try to get episode-level overview and title from TMDB.
    val episodeEnrichment = if (isSeries && season != null && episode != null) {
        runCatching {
            tmdbMetadataService.fetchEpisodeEnrichment(
                tmdbId = tmdbId,
                seasonNumbers = listOf(season),
                language = settings.language
            )[season to episode]
        }.getOrNull()
    } else null

    val tmdbDescription = episodeEnrichment?.overview ?: enrichment.description
    if (settings.useBasicInfo && !tmdbDescription.isNullOrBlank()) {
        _uiState.update { it.copy(description = tmdbDescription) }
    }

    // Enrich title from TMDB (localized).
    if (settings.useBasicInfo) {
        val tmdbTitle = enrichment.localizedTitle
        if (!tmdbTitle.isNullOrBlank()) {
            _uiState.update { it.copy(title = tmdbTitle) }
        }
    }

    // Enrich logo from TMDB if artwork is enabled.
    if (settings.useArtwork) {
        val tmdbLogo = enrichment.logo
        if (!tmdbLogo.isNullOrBlank()) {
            _uiState.update { it.copy(logo = tmdbLogo) }
        }
    }

    // Also enrich episode title from TMDB if available.
    if (settings.useBasicInfo) {
        val tmdbEpisodeTitle = episodeEnrichment?.title
        if (!tmdbEpisodeTitle.isNullOrBlank()) {
            _uiState.update { it.copy(currentEpisodeTitle = tmdbEpisodeTitle) }
        }
    }

    // Enrich cast from TMDB when the description fetch did not supply any.
    if (settings.useBasicInfo && enrichment.castMembers.isNotEmpty()) {
        _uiState.update { state ->
            if (state.castMembers.isEmpty()) state.copy(castMembers = enrichment.castMembers)
            else state
        }
    }

    // Refresh MediaSession metadata with TMDB-enriched title / artwork.
    updateMediaSessionMetadata()
}

internal fun PlayerRuntimeController.recomputeNextEpisode(resetVisibility: Boolean) {
    val normalizedType = contentType?.lowercase()
    if (normalizedType !in listOf("series", "tv", "other")) {
        nextEpisodeVideo = null
        clearNextEpisodeAndCancelPostPlay()
        return
    }

    if (normalizedType == "other") {
        val currentId = currentVideoId
        val idx = if (currentId != null) metaVideos.indexOfFirst { it.id == currentId } else -1
        val resolvedNext = if (idx >= 0 && idx < metaVideos.size - 1) metaVideos[idx + 1] else null

        nextEpisodeVideo = resolvedNext
        if (resolvedNext == null) {
            clearNextEpisodeAndCancelPostPlay()
            return
        }
        val nextInfo = NextEpisodeInfo(
            videoId = resolvedNext.id,
            season = resolvedNext.season ?: 1,
            episode = resolvedNext.episode ?: (idx + 2),
            title = resolvedNext.title,
            thumbnail = resolvedNext.thumbnail,
            overview = resolvedNext.overview,
            released = resolvedNext.released,
            hasAired = true,
            unairedMessage = null,
            isOtherType = true
        )
        applyRecomputedNextEpisode(nextInfo, resetVisibility)
        return
    }

    val season = currentSeason
    val episode = currentEpisode
    if (season == null || episode == null) {
        nextEpisodeVideo = null
        clearNextEpisodeAndCancelPostPlay()
        return
    }

    val resolvedNext = PlayerNextEpisodeRules.resolveNextEpisode(
        videos = metaVideos,
        currentSeason = season,
        currentEpisode = episode
    )

    nextEpisodeVideo = resolvedNext
    if (resolvedNext == null) {
        clearNextEpisodeAndCancelPostPlay()
        return
    }

    val hasAired = PlayerNextEpisodeRules.hasEpisodeAired(resolvedNext.released)
    val nextInfo = NextEpisodeInfo(
        videoId = resolvedNext.id,
        season = resolvedNext.season ?: return,
        episode = resolvedNext.episode ?: return,
        title = resolvedNext.title,
        thumbnail = resolvedNext.thumbnail,
        overview = resolvedNext.overview,
        released = resolvedNext.released,
        hasAired = hasAired,
        unairedMessage = if (hasAired) {
            null
        } else {
            context.getString(com.nuvio.tv.R.string.next_episode_not_aired_yet)
        }
    )
    applyRecomputedNextEpisode(nextInfo, resetVisibility)
}

private fun PlayerRuntimeController.clearNextEpisodeAndCancelPostPlay() {
    val mode = _uiState.value.postPlayMode
    if (mode != null) {
        resetPostPlayOverlayState(clearEpisode = true)
        return
    }
    _uiState.update {
        it.copy(
            nextEpisode = null,
            postPlayDismissedForCurrentEpisode = false,
        )
    }
}

private fun PlayerRuntimeController.applyRecomputedNextEpisode(
    nextInfo: NextEpisodeInfo,
    resetVisibility: Boolean,
) {
    val previousState = _uiState.value
    val previousNextEpisode = previousState.nextEpisode
    val previousMode = previousState.postPlayMode
    if (previousMode is PostPlayMode.StillWatching &&
        previousNextEpisode != null &&
        previousNextEpisode.videoId != nextInfo.videoId
    ) {
        resetPostPlayOverlayState(clearEpisode = true)
        return
    }
    _uiState.update { state ->
        val sameEpisode = state.nextEpisode?.videoId == nextInfo.videoId
        val shouldResetVisibility = resetVisibility || !sameEpisode
        val updatedMode = if (shouldResetVisibility) {
            null
        } else {
            state.postPlayMode?.copyWithNextEpisode(nextInfo)
        }
        state.copy(
            nextEpisode = nextInfo,
            postPlayMode = updatedMode,
            postPlayDismissedForCurrentEpisode =
                if (shouldResetVisibility && !state.postPlayDismissedForCurrentEpisode) false
                else state.postPlayDismissedForCurrentEpisode,
        )
    }
}

internal fun PlayerRuntimeController.resetPostPlayOverlayState(clearEpisode: Boolean = false) {
    nextEpisodeAutoPlayJob?.cancel()
    nextEpisodeAutoPlayJob = null
    stillWatchingPromptJob?.cancel()
    stillWatchingPromptJob = null
    _uiState.update { state ->
        state.copy(
            nextEpisode = if (clearEpisode) null else state.nextEpisode,
            postPlayMode = null,
            postPlayDismissedForCurrentEpisode = false,
        )
    }
    if (clearEpisode) {
        nextEpisodeVideo = null
    }
}

internal fun PlayerRuntimeController.evaluatePostPlayOverlayVisibility(positionMs: Long, durationMs: Long) {
    if (!hasRenderedFirstFrame) return

    val state = _uiState.value
    if (state.nextEpisode == null || nextEpisodeVideo == null) {
        if (state.postPlayMode != null) {
            _uiState.update { it.copy(postPlayMode = null) }
        }
        return
    }
    if (state.postPlayMode != null || state.postPlayDismissedForCurrentEpisode) return

    val effectiveDuration = durationMs.takeIf { it > 0L } ?: lastKnownDuration
    val shouldShow = PlayerNextEpisodeRules.shouldShowNextEpisodeCard(
        positionMs = positionMs,
        durationMs = effectiveDuration,
        skipIntervals = skipIntervals,
        thresholdMode = nextEpisodeThresholdModeSetting,
        thresholdPercent = nextEpisodeThresholdPercentSetting,
        thresholdMinutesBeforeEnd = nextEpisodeThresholdMinutesBeforeEndSetting
    )

    if (!shouldShow) return

    if (_uiState.value.postPlayDismissedForCurrentEpisode) return

    val shouldEnterStillWatching = shouldEnterStillWatchingPrompt(
        stillWatchingEnabled = stillWatchingEnabledSetting,
        autoPlayNextEpisodeEnabled = streamAutoPlayNextEpisodeEnabledSetting,
        nextEpisodeHasAired = state.nextEpisode.hasAired,
        consecutiveAutoPlayCount = consecutiveAutoPlayCount,
        threshold = stillWatchingEpisodeThresholdSetting,
    )

    if (shouldEnterStillWatching) {
        enterStillWatchingPromptMode()
    } else {
        _uiState.update {
            it.copy(postPlayMode = PostPlayMode.AutoPlay(nextEpisode = state.nextEpisode))
        }
        if (state.nextEpisode.hasAired && streamAutoPlayNextEpisodeEnabledSetting) {
            playNextEpisode()
        }
    }
}

internal fun PlayerRuntimeController.showStreamSourceIndicator(stream: Stream) {
    val chosenSource = (stream.name?.takeIf { it.isNotBlank() } ?: stream.addonName).trim()
    if (chosenSource.isBlank()) return

    hideStreamSourceIndicatorJob?.cancel()
    _uiState.update {
        it.copy(
            showStreamSourceIndicator = true,
            streamSourceIndicatorText = "Source: $chosenSource"
        )
    }
    hideStreamSourceIndicatorJob = scope.launch {
        delay(2200)
        _uiState.update { it.copy(showStreamSourceIndicator = false) }
    }
}

internal fun PlayerRuntimeController.updateActiveSkipInterval(positionMs: Long) {
    if (skipIntervals.isEmpty()) {
        if (_uiState.value.activeSkipInterval != null) {
            _uiState.update { it.copy(activeSkipInterval = null) }
        }
        return
    }

    // Don't evaluate skip intervals until player settings are loaded from DataStore.
    // Without this, autoSkipSegmentTypes is empty on first iterations, causing the
    // skip button to appear instead of auto-skipping.
    if (!playerSettingsInitialized) return

    val positionSec = positionMs / 1000.0
    val active = skipIntervals.find { interval ->
        positionSec >= interval.startTime && positionSec < (interval.endTime - 0.5)
    }

    val currentActive = _uiState.value.activeSkipInterval

    if (active != null) {
        if (currentActive == null || active.type != currentActive.type || active.startTime != currentActive.startTime) {
            lastActiveSkipType = active.type
            _uiState.update { it.copy(activeSkipInterval = active, skipIntervalDismissed = false) }
        }
        val segmentType = AutoSkipSegmentType.fromSkipIntervalType(active.type)
        val activeKey = active.autoSkipKey()
        if (
            segmentType != null &&
            segmentType in autoSkipSegmentTypes &&
            activeKey !in autoSkippedIntervalKeys
        ) {
            autoSkippedIntervalKeys.add(activeKey)
            skipInterval(active)
        }
    } else if (currentActive != null) {
        _uiState.update { it.copy(activeSkipInterval = null, skipIntervalDismissed = false) }
    }
}

private fun SkipInterval.autoSkipKey(): String =
    "$provider:$type:$startTime:$endTime"
