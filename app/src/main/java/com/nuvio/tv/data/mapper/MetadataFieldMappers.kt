package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaBehaviorHintsDto
import com.nuvio.tv.data.remote.dto.MetaTrailerDto
import com.nuvio.tv.data.remote.dto.TrailerStreamDto
import com.nuvio.tv.domain.model.MetaBehaviorHints
import com.nuvio.tv.domain.model.MetaTrailer

/**
 * Catalog-meta subset of the shared addon field mappers. The people/release-date helpers
 * served the addon meta-detail flow (removed in the cleanup) and are not restored.
 */

internal fun coerceStringList(value: Any?): List<String> {
    return when (value) {
        null -> emptyList()
        is String -> listOf(value)
        is List<*> -> value.mapNotNull { entry ->
            when (entry) {
                is String -> entry
                is Map<*, *> -> entry["name"] as? String
                else -> null
            }
        }
        is Map<*, *> -> {
            val name = value["name"] as? String
            if (!name.isNullOrBlank()) listOf(name) else emptyList()
        }
        else -> emptyList()
    }.mapNotNull { it.trim().takeIf(String::isNotBlank) }
}

internal fun mapBehaviorHints(dto: MetaBehaviorHintsDto?): MetaBehaviorHints? {
    if (dto == null) return null
    return MetaBehaviorHints(
        defaultVideoId = dto.defaultVideoId?.takeIf { it.isNotBlank() },
        hasScheduledVideos = dto.hasScheduledVideos
    )
}

internal fun mapTrailers(
    trailers: List<MetaTrailerDto>?,
    trailerStreams: List<TrailerStreamDto>?
): List<MetaTrailer> {
    val fromTrailers = trailers.orEmpty().map {
        MetaTrailer(
            source = it.source?.takeIf(String::isNotBlank),
            type = it.type?.takeIf(String::isNotBlank),
            name = it.name?.takeIf(String::isNotBlank),
            ytId = (it.source ?: it.ytId)?.takeIf(String::isNotBlank),
            lang = it.lang?.takeIf(String::isNotBlank)
        )
    }
    val existingYtIds = fromTrailers.mapNotNull { it.ytId }.toSet()
    val fromStreams = trailerStreams.orEmpty().mapNotNull { stream ->
        val ytId = stream.ytId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        if (ytId in existingYtIds) return@mapNotNull null
        MetaTrailer(ytId = ytId)
    }
    return fromTrailers + fromStreams
}

internal fun collectTrailerYtIds(
    trailers: List<MetaTrailerDto>?,
    trailerStreams: List<TrailerStreamDto>?
): List<String> {
    return mapTrailers(trailers, trailerStreams)
        .mapNotNull { it.ytId }
        .distinct()
}
