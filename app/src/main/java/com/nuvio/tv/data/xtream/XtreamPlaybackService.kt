package com.nuvio.tv.data.xtream

import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.domain.model.ContentType
import javax.inject.Inject
import javax.inject.Singleton

enum class XtreamAvailability {
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}

data class XtreamSeriesAvailabilityResult(
    val availableEpisodes: Set<Pair<Int, Int>> = emptySet(),
    val complete: Boolean = false,
    val status: XtreamAvailability
)

@Singleton
class XtreamPlaybackService @Inject constructor(
    private val tmdbMetadataService: TmdbMetadataService,
    private val resolver: XtreamPlaybackResolver
) {
    suspend fun resolve(
        tmdbId: Int,
        contentType: ContentType,
        season: Int?,
        episode: Int?
    ): XtreamResolution {
        val identity = identity(tmdbId, contentType)
            ?: return XtreamResolution.Failure("TMDB metadata is unavailable")
        return if (contentType == ContentType.SERIES || contentType == ContentType.TV) {
            if (season == null || episode == null) XtreamResolution.Unavailable
            else resolver.resolveEpisode(identity.titles, identity.year, season, episode)
        } else {
            resolver.resolveMovie(tmdbId, identity.titles, identity.year)
        }
    }

    suspend fun movieAvailability(tmdbId: Int): XtreamAvailability {
        val identity = identity(tmdbId, ContentType.MOVIE) ?: return XtreamAvailability.ERROR
        return resolver.movieAvailability(tmdbId, identity.titles, identity.year)
    }

    suspend fun seriesAvailability(tmdbId: Int): XtreamSeriesAvailabilityResult {
        val identity = identity(tmdbId, ContentType.SERIES)
            ?: return XtreamSeriesAvailabilityResult(status = XtreamAvailability.ERROR)
        return when (val result = resolver.seriesAvailability(tmdbId, identity.titles, identity.year)) {
            is XtreamSeriesAvailability.Resolved -> XtreamSeriesAvailabilityResult(
                availableEpisodes = result.episodes,
                complete = result.complete,
                status = XtreamAvailability.AVAILABLE
            )
            XtreamSeriesAvailability.Unavailable -> XtreamSeriesAvailabilityResult(
                complete = true,
                status = XtreamAvailability.UNAVAILABLE
            )
            is XtreamSeriesAvailability.Failure -> XtreamSeriesAvailabilityResult(
                status = XtreamAvailability.ERROR
            )
        }
    }

    suspend fun prefetchAvailability(tmdbId: Int, contentType: ContentType) {
        if (contentType == ContentType.SERIES || contentType == ContentType.TV) {
            seriesAvailability(tmdbId)
        } else {
            movieAvailability(tmdbId)
        }
    }

    private suspend fun identity(tmdbId: Int, contentType: ContentType): XtreamIdentity? {
        val enrichment = tmdbMetadataService.fetchEnrichment(
            tmdbId = tmdbId.toString(),
            contentType = contentType,
            language = "pt-BR"
        ) ?: return null
        val titles = buildSet {
            enrichment.localizedTitle?.let(::add)
            enrichment.originalTitle?.let(::add)
            addAll(enrichment.alternativeTitles)
        }.filter(String::isNotBlank)
        if (titles.isEmpty()) return null
        return XtreamIdentity(
            titles = titles,
            year = enrichment.releaseInfo?.take(4)?.toIntOrNull()
        )
    }

    private data class XtreamIdentity(
        val titles: List<String>,
        val year: Int?
    )
}
