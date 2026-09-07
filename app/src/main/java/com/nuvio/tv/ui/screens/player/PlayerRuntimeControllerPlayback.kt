package com.nuvio.tv.ui.screens.player

import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun PlayerRuntimeController.pauseForLifecycle() {
    // Mark we're in background so onPlayerError can defer recovery to onResume.
    isInBackground = true

    // Release the MediaSession so the system doesn't route media commands
    // (play/pause, audio focus) to this player while the app is in the background.
    try {
        currentMediaSession?.release()
        currentMediaSession = null
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Mark as user-paused so autoplay logic doesn't resume playback.
    userPausedManually = true
    shouldEnforceAutoplayOnFirstReady = false

    pauseStartTimeMs = System.currentTimeMillis()
    _exoPlayer?.let { player ->
        // Disable automatic audio focus handling so ExoPlayer can't
        // re-acquire focus and set playWhenReady=true behind our back.
        player.setAudioAttributes(player.audioAttributes, false)
        player.playWhenReady = false
        player.pause()
    }
}

internal fun PlayerRuntimeController.resumeForLifecycle() {
    isInBackground = false

    // If the codec crashed while in background, the player was released to free
    // resources. Rebuild it now with the saved position so the user comes back
    // to a clean, paused player ready to play.
    if (pendingBackgroundCrashRecovery) {
        pendingBackgroundCrashRecovery = false
        val savedPosition = backgroundCrashSavedPositionMs
        backgroundCrashSavedPositionMs = 0L
        if (savedPosition > 0L) {
            _uiState.update { it.copy(pendingSeekPosition = savedPosition) }
        }
        if (currentStreamUrl.isNotEmpty()) {
            initializePlayer(currentStreamUrl, currentHeaders, startPaused = true)
        }
        return
    }

    val player = _exoPlayer
    if (player != null) {
        // Restore automatic audio focus handling that was disabled in pauseForLifecycle().
        player.setAudioAttributes(player.audioAttributes, true)

        // Re-create the MediaSession so media controls work in the foreground.
        if (currentMediaSession == null) {
            try {
                currentMediaSession = androidx.media3.session.MediaSession.Builder(context, player).build()
                updateMediaSessionMetadata()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

internal fun PlayerRuntimeController.currentPlaybackPositionMs(): Long? {
    return _exoPlayer?.currentPosition
}

internal fun PlayerRuntimeController.currentPlaybackDurationMs(): Long {
    return _exoPlayer?.duration ?: 0L
}

internal fun PlayerRuntimeController.isPlaybackCurrentlyPlaying(): Boolean {
    return _exoPlayer?.isPlaying == true
}

internal fun PlayerRuntimeController.seekPlaybackTo(
    positionMs: Long,
    seekParameters: SeekParameters = SeekParameters.CLOSEST_SYNC
) {
    _exoPlayer?.let { player ->
        if (NuvioExoPlayerPerformanceHelper.enabled) {
            val currentPos = player.currentPosition
            val isForwardSeek = positionMs >= currentPos
            val inBuffer = isForwardSeek && NuvioExoPlayerPerformanceHelper.isSeekInBuffer(player, positionMs)
            if (inBuffer) {
                suppressBufferingUiForSeek = true
                scheduleSeekSuppressTimeout()
            } else {
                // Out of buffer or backward seek: show spinner immediately
                seekBufferingUiDeferred = false
                suppressBufferingUiForSeek = false
                seekBufferingUiJob?.cancel()
                _uiState.update { it.copy(isBuffering = true) }
            }
            NuvioExoPlayerPerformanceHelper.buildScrubbingParams()?.let { params ->
                isScrubbingModeActive = true
                player.setScrubbingModeParameters(params)
            }
        }
        player.setSeekParameters(seekParameters)
        player.seekTo(positionMs)
    }
}

internal fun PlayerRuntimeController.setPlaybackSpeedInternal(speed: Float) {
    _exoPlayer?.setPlaybackSpeed(speed)
}

internal fun PlayerRuntimeController.setPlaybackPaused(paused: Boolean) {
    _exoPlayer?.let { player ->
        if (paused) player.pause() else player.play()
    }
}

internal fun PlayerRuntimeController.pauseForStillWatchingPrompt() {
    setPlaybackPaused(true)
}

/**
 * After an in-buffer seek, automatically clear the buffering-UI suppression
 * flag after a short timeout so normal buffering states resume if the seek
 * takes longer than expected.
 */
internal fun PlayerRuntimeController.scheduleSeekSuppressTimeout() {
    scope.launch {
        delay(NuvioExoPlayerPerformanceHelper.SEEK_SUPPRESS_TIMEOUT_MS)
        suppressBufferingUiForSeek = false
    }
}
