package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode

object StreamAutoPlayPolicy {
    fun shouldForceDirectTmdbPlayback(videoId: String, manualSelection: Boolean): Boolean =
        !manualSelection && videoId.startsWith("tmdb:", ignoreCase = true)

    /**
     * A fresh (non-manual) Watch press reveals the manual picker whenever
     * several playable streams exist, so the user chooses the variant.
     * A single playable stream keeps playing directly, and an auto-next
     * continuation is never forced through the picker: it relies on the
     * remembered binge group (with first-playable fallback) so an ongoing
     * marathon is not interrupted between episodes.
     */
    fun shouldRequirePickerForDirectPlay(
        forceDirectPlayback: Boolean,
        playableStreamCount: Int,
        isAutoNext: Boolean
    ): Boolean =
        forceDirectPlayback && !isAutoNext && playableStreamCount > 1

    fun canonicalTmdbVideoId(
        itemId: String,
        fallback: String,
        season: Int? = null,
        episode: Int? = null
    ): String {
        val tmdbId = itemId
            .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.substringBefore(':')
            ?.toIntOrNull()
        return canonicalTmdbVideoId(tmdbId, fallback, season, episode)
    }

    fun canonicalTmdbVideoId(
        tmdbId: Int?,
        fallback: String,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        tmdbId ?: return fallback
        return if (season != null && episode != null) {
            "tmdb:$tmdbId:$season:$episode"
        } else {
            "tmdb:$tmdbId"
        }
    }

    fun isEffectivelyEnabled(playerSettings: PlayerSettings): Boolean {
        if (playerSettings.streamReuseLastLinkEnabled) return true
        if (playerSettings.streamAutoPlayReuseBingeGroup &&
            playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode) return true

        return when (playerSettings.streamAutoPlayMode) {
            StreamAutoPlayMode.MANUAL -> false
            StreamAutoPlayMode.FIRST_STREAM -> true
            StreamAutoPlayMode.REGEX_MATCH -> isRegexSelectionConfigured(playerSettings.streamAutoPlayRegex)
        }
    }

    fun isRegexSelectionConfigured(regexPattern: String): Boolean {
        val pattern = regexPattern.trim()
        if (pattern.isEmpty() || !pattern.any { it.isLetterOrDigit() }) return false
        return runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
    }
}
