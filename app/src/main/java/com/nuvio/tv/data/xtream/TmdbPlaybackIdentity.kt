package com.nuvio.tv.data.xtream

suspend fun resolveTmdbPlaybackId(
    candidates: List<String>,
    mediaType: String,
    lookup: suspend (String, String) -> String?,
): Int? {
    candidates.asSequence()
        .map(String::trim)
        .firstNotNullOfOrNull { candidate ->
            candidate
                .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.substringBefore(':')
                ?.toIntOrNull()
        }
        ?.let { return it }

    val imdbId = candidates.asSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith("tt", ignoreCase = true) }
        ?: return null
    return lookup(imdbId, mediaType)?.toIntOrNull()
}
