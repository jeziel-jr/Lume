package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.xtream.CatalogPlaybackAvailability
import com.nuvio.tv.data.xtream.XtreamCatalogAvailabilityService
import com.nuvio.tv.data.xtream.XtreamCatalogState
import com.nuvio.tv.data.xtream.catalogAvailabilityKey
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbPlayableCatalogLoaderTest {
    @Test
    fun `initial load expands pages until twenty playable items`() = runTest {
        val tmdb = mockk<TmdbCatalogService>()
        val availability = readyAvailability()
        coEvery { tmdb.homePage("popular-movies", any(), "pt-BR") } answers {
            val page = secondArg<Int>()
            row(
                page = page,
                hasMore = page < 3,
                items = when (page) {
                    1 -> (1..10).map { preview("p1-$it", playable = it <= 5) }
                    2 -> (1..10).map { preview("p2-$it", playable = true) }
                    else -> (1..5).map { preview("p3-$it", playable = true) }
                },
            )
        }
        coEvery { availability.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate { item ->
                item.catalogAvailabilityKey() to if (item.name.startsWith("playable")) {
                    CatalogPlaybackAvailability.LIKELY_AVAILABLE
                } else {
                    CatalogPlaybackAvailability.UNAVAILABLE
                }
            }
        }

        val result = TmdbPlayableCatalogLoader(tmdb, availability).loadInitial("popular-movies")

        assertEquals(20, result?.items?.size)
        assertEquals(3, result?.currentPage)
        assertTrue(result?.items?.all { it.name.startsWith("playable") } == true)
        coVerify(exactly = 3) { tmdb.homePage("popular-movies", any(), "pt-BR") }
    }

    @Test
    fun `index not ready keeps first page and does not delay startup`() = runTest {
        val tmdb = mockk<TmdbCatalogService>()
        val availability = mockk<XtreamCatalogAvailabilityService>()
        every { availability.catalogState } returns MutableStateFlow(XtreamCatalogState.Loading)
        val firstPage = row(page = 1, hasMore = true, items = (1..20).map { preview("$it", true) })
        coEvery { tmdb.homePage("popular-movies", 1, "pt-BR") } returns firstPage
        coEvery { availability.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate { item ->
                item.catalogAvailabilityKey() to CatalogPlaybackAvailability.UNKNOWN
            }
        }

        val result = TmdbPlayableCatalogLoader(tmdb, availability).loadInitial("popular-movies")

        assertEquals(firstPage.items, result?.items)
        coVerify(exactly = 1) { tmdb.homePage("popular-movies", any(), "pt-BR") }
    }

    @Test
    fun `ready index excludes items awaiting alias lookup from home rows`() = runTest {
        val tmdb = mockk<TmdbCatalogService>()
        val availability = readyAvailability()
        val items = (1..20).map { preview("$it", playable = it % 5 != 0) }
        coEvery { tmdb.homePage("popular-movies", 1, "pt-BR") } returns row(
            page = 1,
            hasMore = false,
            items = items,
        )
        coEvery { availability.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate { item ->
                item.catalogAvailabilityKey() to when {
                    item.name.startsWith("playable") -> CatalogPlaybackAvailability.LIKELY_AVAILABLE
                    item.name == "missing-10" || item.name == "missing-15" -> {
                        CatalogPlaybackAvailability.UNKNOWN
                    }
                    else -> CatalogPlaybackAvailability.UNAVAILABLE
                }
            }
        }

        val result = TmdbPlayableCatalogLoader(tmdb, availability).loadInitial("popular-movies")

        assertEquals(16, result?.items?.size)
        assertTrue(result?.items?.all { it.name.startsWith("playable") } == true)
    }

    @Test
    fun `refilter drops items awaiting alias lookup when index is ready`() = runTest {
        val tmdb = mockk<TmdbCatalogService>()
        val availability = readyAvailability()
        val pending = preview("pending", playable = false)
        val playable = preview("ok", playable = true)
        val input = row(page = 1, hasMore = false, items = listOf(playable, pending))
        coEvery { availability.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate { item ->
                item.catalogAvailabilityKey() to if (item.name.startsWith("playable")) {
                    CatalogPlaybackAvailability.LIKELY_AVAILABLE
                } else {
                    CatalogPlaybackAvailability.UNKNOWN
                }
            }
        }

        val result = TmdbPlayableCatalogLoader(tmdb, availability).refilter(input)

        assertEquals(listOf(playable), result.items)
    }

    private fun readyAvailability(): XtreamCatalogAvailabilityService {
        val availability = mockk<XtreamCatalogAvailabilityService>()
        every { availability.catalogState } returns MutableStateFlow(XtreamCatalogState.Ready())
        return availability
    }

    private fun row(page: Int, hasMore: Boolean, items: List<MetaPreview>) = CatalogRow(
        addonId = "tmdb",
        addonName = "TMDB",
        addonBaseUrl = "https://api.themoviedb.org/3/",
        catalogId = "popular-movies",
        catalogName = "Filmes populares",
        type = ContentType.MOVIE,
        items = items,
        currentPage = page,
        hasMore = hasMore,
    )

    private fun preview(id: String, playable: Boolean) = MetaPreview(
        id = "tmdb:$id",
        type = ContentType.MOVIE,
        name = if (playable) "playable-$id" else "missing-$id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = "2024",
        imdbRating = null,
        genres = emptyList(),
    )
}
