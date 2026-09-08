package com.nuvio.tv.core.tmdb

import android.util.Log
import com.nuvio.tv.data.xtream.CatalogPlaybackAvailability
import com.nuvio.tv.data.xtream.XtreamCatalogAvailabilityService
import com.nuvio.tv.data.xtream.XtreamCatalogState
import com.nuvio.tv.data.xtream.catalogAvailabilityKey
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

private const val TARGET_PLAYABLE_ITEMS = 20
private const val MAX_TMDB_PAGES_PER_LOAD = 5

@Singleton
class TmdbPlayableCatalogLoader @Inject constructor(
    private val tmdbCatalogService: TmdbCatalogService,
    private val availabilityService: XtreamCatalogAvailabilityService,
) {
    val catalogState: StateFlow<XtreamCatalogState>
        get() = availabilityService.catalogState

    suspend fun loadInitial(catalogId: String, language: String = "pt-BR"): CatalogRow? =
        load(
            catalogId = catalogId,
            language = language,
            firstPage = 1,
            seed = null,
            targetItemCount = TARGET_PLAYABLE_ITEMS,
        )

    suspend fun loadMore(current: CatalogRow, language: String = "pt-BR"): CatalogRow? {
        val filteredSeed = filter(current.items)
        if (!filteredSeed.indexReady) return current.copy(isLoading = false)
        return load(
            catalogId = current.catalogId,
            language = language,
            firstPage = current.currentPage + 1,
            seed = current.copy(items = filteredSeed.items),
            targetItemCount = filteredSeed.items.size + TARGET_PLAYABLE_ITEMS,
        )
    }

    suspend fun refilter(row: CatalogRow): CatalogRow {
        val filtered = filter(row.items)
        return if (filtered.indexReady) row.copy(items = filtered.items) else row
    }

    private suspend fun load(
        catalogId: String,
        language: String,
        firstPage: Int,
        seed: CatalogRow?,
        targetItemCount: Int,
    ): CatalogRow? {
        val items = seed?.items.orEmpty().toMutableList()
        val seen = items.mapTo(mutableSetOf()) { it.catalogAvailabilityKey() }
        var template = seed
        var lastPage: CatalogRow? = null
        var pageNumber = firstPage
        var pagesLoaded = 0
        var inputCount = seed?.items?.size ?: 0

        while (pagesLoaded < MAX_TMDB_PAGES_PER_LOAD && items.size < targetItemCount) {
            val page = tmdbCatalogService.homePage(catalogId, pageNumber, language) ?: break
            template = template ?: page
            lastPage = page
            pagesLoaded += 1
            inputCount += page.items.size

            val filtered = filter(page.items)
            if (!filtered.indexReady) {
                page.items.forEach { item ->
                    if (seen.add(item.catalogAvailabilityKey())) items += item
                }
                break
            }
            filtered.items.forEach { item ->
                if (seen.add(item.catalogAvailabilityKey())) items += item
            }
            if (!page.hasMore) break
            pageNumber = page.currentPage + 1
        }

        val base = template ?: return null
        Log.i(
            "LumeAvailability",
            "catalog=$catalogId pages=$pagesLoaded input=$inputCount output=${items.size}",
        )
        return base.copy(
            items = items,
            currentPage = lastPage?.currentPage ?: base.currentPage,
            hasMore = lastPage?.hasMore ?: base.hasMore,
            isLoading = false,
        )
    }

    private suspend fun filter(items: List<MetaPreview>): FilteredItems {
        // While the Xtream index is not ready there is nothing to filter
        // against: keep the whole row and let Home refilter on index readiness.
        val indexReady = availabilityService.catalogState.value is XtreamCatalogState.Ready
        if (!indexReady) return FilteredItems(items, indexReady = false)
        val availability = availabilityService.classify(items)
        return FilteredItems(
            items = items.filter { item ->
                when (availability[item.catalogAvailabilityKey()]) {
                    // Items awaiting the TMDB alias lookup classify as UNKNOWN:
                    // Home shows no badges, so they must stay out of rows until
                    // they are confirmed playable, exactly like UNAVAILABLE ones.
                    CatalogPlaybackAvailability.AVAILABLE,
                    CatalogPlaybackAvailability.LIKELY_AVAILABLE -> true
                    else -> false
                }
            },
            indexReady = true,
        )
    }

    private data class FilteredItems(
        val items: List<MetaPreview>,
        val indexReady: Boolean,
    )
}
