package com.nuvio.tv.data.xtream

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

enum class CatalogPlaybackAvailability {
    AVAILABLE,
    LIKELY_AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

fun MetaPreview.catalogAvailabilityKey(): String = "$apiType:$id"

@Singleton
class XtreamCatalogAvailabilityService @Inject constructor(
    private val catalogRepository: XtreamCatalogRepository,
    private val availabilityStore: XtreamAvailabilityStore,
) {
    val catalogState: StateFlow<XtreamCatalogState> = catalogRepository.state

    suspend fun classify(items: List<MetaPreview>): Map<String, CatalogPlaybackAvailability> {
        if (items.isEmpty()) return emptyMap()
        val index = catalogRepository.currentIndexOrNull()
            ?: return items.associate { it.catalogAvailabilityKey() to CatalogPlaybackAvailability.UNKNOWN }

        val identities = items.map { item -> item to item.tmdbIdOrNull() }
        val movieIds = identities.asSequence()
            .filter { (item, id) -> id != null && !item.isSeries() }
            .mapNotNull { it.second }
            .toSet()
        val seriesIds = identities.asSequence()
            .filter { (item, id) -> id != null && item.isSeries() }
            .mapNotNull { it.second }
            .toSet()
        val cached = availabilityStore.catalogAvailability(
            movieIds = movieIds,
            seriesIds = seriesIds,
            catalogFetchedAtMillis = index.fetchedAtMillis,
        )

        return identities.associate { (item, tmdbId) ->
            val availability = when {
                tmdbId == null -> CatalogPlaybackAvailability.UNKNOWN
                item.isSeries() && cached.series[tmdbId] == true -> CatalogPlaybackAvailability.AVAILABLE
                item.isSeries() && cached.series[tmdbId] == false -> CatalogPlaybackAvailability.UNAVAILABLE
                !item.isSeries() && cached.movies[tmdbId] == true -> CatalogPlaybackAvailability.AVAILABLE
                !item.isSeries() && cached.movies[tmdbId] == false -> CatalogPlaybackAvailability.UNAVAILABLE
                item.hasConservativeLocalCandidate(index) -> CatalogPlaybackAvailability.LIKELY_AVAILABLE
                else -> CatalogPlaybackAvailability.UNAVAILABLE
            }
            item.catalogAvailabilityKey() to availability
        }
    }

    private fun MetaPreview.hasConservativeLocalCandidate(index: XtreamCatalogIndex): Boolean {
        val titles = buildSet {
            add(name)
            addAll(alternativeTitles)
        }.map(XtreamTitleMatcher::normalize).filter(String::isNotBlank)
        val year = releaseInfo?.take(4)?.toIntOrNull()
        val candidateCount = if (isSeries()) {
            index.findSeries(titles, year).size
        } else {
            index.findVod(titles, year).size
        }
        return candidateCount > 0 && (year != null || candidateCount == 1)
    }

    private fun MetaPreview.tmdbIdOrNull(): Int? = id
        .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.substringBefore(':')
        ?.toIntOrNull()

    private fun MetaPreview.isSeries(): Boolean =
        type == ContentType.SERIES || type == ContentType.TV ||
            apiType.equals("series", ignoreCase = true) || apiType.equals("tv", ignoreCase = true)
}
