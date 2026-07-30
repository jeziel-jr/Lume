package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.core.tmdb.TmdbLocalizedPreview
import com.nuvio.tv.domain.model.MetaPreview

internal fun MetaPreview.withLocalizedTmdbPreview(
    preview: TmdbLocalizedPreview,
): MetaPreview {
    val localizedName = preview.localizedTitle?.takeIf(String::isNotBlank) ?: name
    val alternatives = buildList {
        addAll(alternativeTitles)
        add(name)
        preview.originalTitle?.let(::add)
    }
        .map(String::trim)
        .filter { it.isNotBlank() && !it.equals(localizedName, ignoreCase = true) }
        .distinctBy(String::lowercase)

    return copy(
        id = "tmdb:${preview.tmdbId}",
        name = localizedName,
        poster = preview.posterUrl ?: poster,
        background = preview.backdropUrl ?: background,
        description = preview.description ?: description,
        releaseInfo = preview.releaseDate?.take(4) ?: releaseInfo,
        imdbRating = preview.rating ?: imdbRating,
        voteCount = preview.voteCount ?: voteCount,
        released = preview.releaseDate ?: released,
        imdbId = preview.imdbId,
        alternativeTitles = alternatives,
    )
}

internal fun MetaPreview.discoverIdentityKey(): String {
    val externalId = imdbId
        ?.substringBefore(':')
        ?.takeIf(String::isNotBlank)
        ?: id.substringBefore(':').takeIf { id.startsWith("tt", ignoreCase = true) }
        ?: id
    return "$apiType:$externalId"
}
