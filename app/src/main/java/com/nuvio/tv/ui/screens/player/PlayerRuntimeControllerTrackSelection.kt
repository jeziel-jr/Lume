package com.nuvio.tv.ui.screens.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun PlayerRuntimeController.filterEpisodeStreamsByAddon(addonName: String?) {
    val allStreams = _uiState.value.episodeAllStreams
    val filteredStreams = if (addonName == null) {
        allStreams
    } else {
        allStreams.filter { it.addonName == addonName }
    }

    _uiState.update {
        it.copy(
            episodeSelectedAddonFilter = addonName,
            episodeFilteredStreams = filteredStreams
        )
    }
}

internal fun PlayerRuntimeController.showControlsTemporarily() {
    hideSeekOverlayJob?.cancel()
    _uiState.update { it.copy(showControls = true, showSeekOverlay = false) }
    scheduleHideControls()
}

internal fun PlayerRuntimeController.showSeekOverlayTemporarily() {
    hideSeekOverlayJob?.cancel()
    _uiState.update { it.copy(showSeekOverlay = true) }
    hideSeekOverlayJob = scope.launch {
        delay(1500)
        _uiState.update { it.copy(showSeekOverlay = false) }
    }
}

internal fun PlayerRuntimeController.selectAudioTrack(trackIndex: Int) {
    logSwitchTrace(
        stage = "select-audio-track",
        message = "trackIndex=$trackIndex usingMpv=${isUsingMpvEngine()} " +
            "track=${_uiState.value.audioTracks.getOrNull(trackIndex)?.let { "${it.language}/${it.name}/${it.trackId}" } ?: "none"}"
    )
    if (isUsingMpvEngine()) {
        val wasPlaying = isPlaybackCurrentlyPlaying()
        val track = _uiState.value.audioTracks.getOrNull(trackIndex)
        val trackId = track?.trackId?.toIntOrNull()
        val changed = trackId != null && mpvView?.selectAudioTrackById(trackId) == true
        if (changed) {
            keepMpvPlayingIfNeeded(wasPlaying)
        }
        return
    }

    _exoPlayer?.let { player ->
        val tracks = player.currentTracks
        var currentAudioIndex = 0
        
        tracks.groups.forEach { trackGroup ->
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until trackGroup.length) {
                    if (currentAudioIndex == trackIndex) {
                        val override = TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .build()
                        // Nudge the player to avoid infinite buffering after audio track switch
                        // where the new track requires a different segment.
                        val pos = player.currentPosition
                        if (pos > 0) player.seekTo((pos - 1).coerceAtLeast(0))
                        return
                    }
                    currentAudioIndex++
                }
            }
        }
    }
}

internal fun PlayerRuntimeController.rememberAudioSelection(trackIndex: Int) {
    val selectedTrack = _uiState.value.audioTracks.getOrNull(trackIndex) ?: return
    logSwitchTrace(
        stage = "user-remember-audio",
        message = "trackIndex=$trackIndex lang=${selectedTrack.language} name=${selectedTrack.name} id=${selectedTrack.trackId}"
    )
    val basePreference = currentTrackPreferenceForPersistence()
    clearPendingEngineSwitchTrackPreference()
    persistedTrackPreference = null
    rememberedTrackPreference =
        basePreference
            .copy(
                audio = PlayerRuntimeController.RememberedTrackSelection(
                    language = selectedTrack.language,
                    name = selectedTrack.name,
                    trackId = selectedTrack.trackId
                )
            )
    persistTrackPreference()
}

internal fun PlayerRuntimeController.selectSubtitleTrack(trackIndex: Int) {
    logSwitchTrace(
        stage = "select-subtitle-track",
        message = "trackIndex=$trackIndex usingMpv=${isUsingMpvEngine()} " +
            "track=${_uiState.value.subtitleTracks.getOrNull(trackIndex)?.let { "${it.language}/${it.name}/${it.trackId}/forced=${it.isForced}" } ?: "none"}"
    )
    if (isUsingMpvEngine()) {
        Log.d(PlayerRuntimeController.TAG, "Selecting INTERNAL subtitle trackIndex=$trackIndex (mpv)")
        val shouldKeepPlaying = !userPausedManually && !_uiState.value.playbackEnded
        val track = _uiState.value.subtitleTracks.getOrNull(trackIndex)
        val trackId = track?.trackId?.toIntOrNull()
        val changed = trackId != null && mpvView?.selectSubtitleTrackById(trackId) == true
        if (changed) {
            updateMpvAvailableTracks()
            keepMpvPlayingIfNeeded(shouldKeepPlaying)
        }
        return
    }

    _exoPlayer?.let { player ->
        Log.d(PlayerRuntimeController.TAG, "Selecting INTERNAL subtitle trackIndex=$trackIndex")
        val tracks = player.currentTracks
        var currentSubIndex = 0
        
        tracks.groups.forEach { trackGroup ->
            if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until trackGroup.length) {
                    if (currentSubIndex == trackIndex) {
                        val override = TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .build()
                        return
                    }
                    currentSubIndex++
                }
            }
        }
    }
}

internal fun PlayerRuntimeController.rememberInternalSubtitleSelection(trackIndex: Int) {
    val selectedTrack = _uiState.value.subtitleTracks.getOrNull(trackIndex) ?: return
    logSwitchTrace(
        stage = "user-remember-subtitle-internal",
        message = "trackIndex=$trackIndex lang=${selectedTrack.language} name=${selectedTrack.name} " +
            "id=${selectedTrack.trackId} forced=${selectedTrack.isForced}"
    )
    val rememberedSelection = PlayerRuntimeController.RememberedSubtitleSelection.Internal(
        track = buildRememberedInternalSubtitleSelectionForEngineSwitch(
            state = _uiState.value,
            language = selectedTrack.language,
            name = selectedTrack.name,
            trackId = selectedTrack.trackId,
            isForced = selectedTrack.isForced,
            selectedUiTrackOverride = selectedTrack
        )
    )
    val basePreference = currentTrackPreferenceForPersistence()
    clearPendingEngineSwitchTrackPreference()
    persistedTrackPreference = null
    subtitleDisabledByPersistedPreference = false
    explicitSubtitleSelectionForEngineSwitch =
        PlayerRuntimeController.ExplicitSubtitleSelectionForEngineSwitch(
            streamUrl = currentStreamUrl,
            selection = rememberedSelection
        )
    effectiveSubtitleSelectionForEngineSwitch =
        PlayerRuntimeController.ExplicitSubtitleSelectionForEngineSwitch(
            streamUrl = currentStreamUrl,
            selection = rememberedSelection
        )
    rememberedTrackPreference =
        basePreference
            .copy(subtitle = rememberedSelection)
    persistTrackPreference()
}

internal fun PlayerRuntimeController.disableSubtitles() {
    logSwitchTrace(
        stage = "disable-subtitles",
        message = "usingMpv=${isUsingMpvEngine()} selectedSubtitleIndex=${_uiState.value.selectedSubtitleTrackIndex}"
    )
    if (isUsingMpvEngine()) {
        if (mpvView?.disableSubtitles() == true) {
            _uiState.update {
                it.copy(
                    selectedSubtitleTrackIndex = -1
                )
            }
            updateMpvAvailableTracks()
        }
        return
    }
    _exoPlayer?.let { player ->
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }
}

internal fun PlayerRuntimeController.refreshActiveSubtitleTrackAfterTimingChange() {
    val player = _exoPlayer ?: return
    if (_uiState.value.selectedSubtitleTrackIndex < 0) return

    // Force a renderer reset so stale cues from the old delay do not linger on screen.
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()

    scope.launch {
        delay(90)
        if (_exoPlayer !== player) return@launch
        if (_uiState.value.selectedSubtitleTrackIndex < 0) {
            return@launch
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }
}

internal fun PlayerRuntimeController.rememberSubtitleDisabled() {
    logSwitchTrace(
        stage = "user-remember-subtitle-disabled",
        message = "selectedSubtitleIndex=${_uiState.value.selectedSubtitleTrackIndex}"
    )
    val basePreference = currentTrackPreferenceForPersistence()
    clearPendingEngineSwitchTrackPreference()
    persistedTrackPreference = null
    subtitleDisabledByPersistedPreference = false
    explicitSubtitleSelectionForEngineSwitch =
        PlayerRuntimeController.ExplicitSubtitleSelectionForEngineSwitch(
            streamUrl = currentStreamUrl,
            selection = PlayerRuntimeController.RememberedSubtitleSelection.Disabled
        )
    effectiveSubtitleSelectionForEngineSwitch =
        PlayerRuntimeController.ExplicitSubtitleSelectionForEngineSwitch(
            streamUrl = currentStreamUrl,
            selection = PlayerRuntimeController.RememberedSubtitleSelection.Disabled
        )
    rememberedTrackPreference =
        basePreference
            .copy(subtitle = PlayerRuntimeController.RememberedSubtitleSelection.Disabled)
    persistTrackPreference()
}

private fun PlayerRuntimeController.currentTrackPreferenceForPersistence(): PlayerRuntimeController.TrackPreference {
    return rememberedTrackPreference ?: persistedTrackPreference ?: PlayerRuntimeController.TrackPreference()
}

internal fun PlayerRuntimeController.persistTrackPreference() {
    val id = contentId ?: return
    // Use the currently-effective preference (remembered OR previously persisted)
    // so that a delay-only change does not wipe a previously-saved track selection.
    // For the resume-from-CW case, rememberedTrackPreference is null for the fresh
    // session, and falling through to persistedTrackPreference preserves the user's
    // earlier audio/subtitle choices. See issue #1063.
    val pref = currentTrackPreferenceForPersistence()
    val audio = pref.audio
    val subtitle = pref.subtitle
    val persisted = com.nuvio.tv.data.local.PersistedTrackPreference(
        subtitleType = when (subtitle) {
            is PlayerRuntimeController.RememberedSubtitleSelection.Internal -> "INTERNAL"
            PlayerRuntimeController.RememberedSubtitleSelection.Disabled -> "DISABLED"
            null -> null
        },
        subtitleLanguage = when (subtitle) {
            is PlayerRuntimeController.RememberedSubtitleSelection.Internal -> subtitle.track.language
            else -> null
        },
        subtitleName = (subtitle as? PlayerRuntimeController.RememberedSubtitleSelection.Internal)?.track?.name,
        subtitleTrackId = (subtitle as? PlayerRuntimeController.RememberedSubtitleSelection.Internal)?.track?.trackId,
        subtitleIsForced = (subtitle as? PlayerRuntimeController.RememberedSubtitleSelection.Internal)?.track?.isForcedHint,
        audioLanguage = audio?.language,
        audioName = audio?.name,
        audioTrackId = audio?.trackId
    )
    scope.launch { trackPreferenceDataStore.save(id, persisted) }
    // Subtitle delay is keyed per-videoId (not per-contentId) because a delay
    // calibrated against one episode release rarely applies to the next
    // episode. Scoping it to the exact video prevents cross-episode leakage.
    // See issue #1063 discussion.
    val vid = currentVideoId?.takeIf { it.isNotBlank() }
    if (vid != null) {
        val currentDelayMs = _uiState.value.subtitleDelayMs
        scope.launch {
            trackPreferenceDataStore.saveSubtitleDelayMs(vid, currentDelayMs.takeIf { it != 0 })
        }
    }
}
