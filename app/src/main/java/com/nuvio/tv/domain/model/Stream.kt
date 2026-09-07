package com.nuvio.tv.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a stream source from a Stremio addon
 */
@Immutable
data class Stream(
    val name: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    val ytId: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val externalUrl: String?,
    val behaviorHints: StreamBehaviorHints?,
    val addonName: String,
    val addonLogo: String?,
    val sources: List<String>? = null,
    val quality: String? = null,
    val qualityValue: Int = -1
) {
    /**
     * Returns the primary stream source URL
     */
    fun getStreamUrl(): String? =
        listOfNotNull(url, externalUrl)
            .firstOrNull { url -> !url.isMagnetLink() && !url.trimStart().startsWith("torrent:", ignoreCase = true) }

    /**
     * Returns true if this is a YouTube stream
     */
    fun isYouTube(): Boolean = ytId != null

    /**
     * Returns true if this is an external URL (opens in browser)
     */
    fun isExternal(): Boolean = externalUrl != null && url == null && !externalUrl.isMagnetLink()

    /**
     * Returns a display name for the stream, or null when no field is usable.
     * UI call sites should substitute a localized fallback (R.string.stream_unknown).
     */
    fun getDisplayNameOrNull(): String? = name ?: title ?: description

    /**
     * Returns a display name for the stream
     */
    fun getDisplayName(): String = getDisplayNameOrNull() ?: "Unknown Stream"

    /**
     * Returns a display description for the stream
     */
    fun getDisplayDescription(): String? = description ?: title

    /**
     * Returns a stable key for use in LazyColumn/LazyRow.
     * Incorporates all content-identifying fields so the key doesn't change
     * when the list recomposes or items shift position. The [occurrence] parameter
     * disambiguates genuine duplicates (same addon+url+name+title).
     */
    fun stableKey(occurrence: Int = 0): String = buildString {
        append(addonName)
        append('\u0000')
        append(url ?: infoHash ?: ytId ?: externalUrl ?: "")
        append('\u0000')
        append(fileIdx ?: "")
        append('\u0000')
        append(name ?: "")
        append('\u0000')
        append(title ?: "")
        append('\u0000')
        append(description ?: "")
        append('\u0000')
        append(quality ?: "")
        append('\u0000')
        append(sources.orEmpty().joinToString("|"))
        append('\u0000')
        append(occurrence)
    }
}

@Immutable
data class StreamBehaviorHints(
    val notWebReady: Boolean?,
    val bingeGroup: String?,
    val countryWhitelist: List<String>?,
    val proxyHeaders: ProxyHeaders?,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null
)

@Immutable
data class ProxyHeaders(
    val request: Map<String, String>?,
    val response: Map<String, String>?
)

/**
 * Represents streams grouped by addon source
 */
@Immutable
data class AddonStreams(
    val addonName: String,
    val addonLogo: String?,
    val streams: List<Stream>
)

private fun String?.isMagnetLink(): Boolean =
    this?.trimStart()?.startsWith("magnet:", ignoreCase = true) == true
