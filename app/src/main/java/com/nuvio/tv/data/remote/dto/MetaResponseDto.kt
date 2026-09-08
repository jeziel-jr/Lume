package com.nuvio.tv.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Catalog-meta subset of the addon-protocol DTOs (previously part of the addon
 * meta/stream subsystem removed in the cleanup). Only the shared types consumed by
 * [CatalogResponseDto]/[MetaPreviewDto] and the catalog mappers are restored;
 * the meta-detail/stream DTOs (MetaDto, VideoDto, AppExtrasDto, release dates, …)
 * stay out because their consumers (addon meta/stream fetching) are not restored.
 */

@JsonClass(generateAdapter = true)
data class TrailerStreamDto(
    @Json(name = "ytId") val ytId: String? = null
)

@JsonClass(generateAdapter = true)
data class MetaLinkDto(
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class MetaTrailerDto(
    @Json(name = "source") val source: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "ytId") val ytId: String? = null,
    @Json(name = "lang") val lang: String? = null
)

@JsonClass(generateAdapter = true)
data class MetaBehaviorHintsDto(
    @Json(name = "defaultVideoId") val defaultVideoId: String? = null,
    @Json(name = "hasScheduledVideos") val hasScheduledVideos: Boolean? = null
)
