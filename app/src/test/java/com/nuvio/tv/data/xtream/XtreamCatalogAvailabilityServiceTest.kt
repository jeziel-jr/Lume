package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodItem
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamCatalogAvailabilityServiceTest {
    @Test
    fun `localized item matches original title without remote details`() = runTest {
        val index = index(
            vod = listOf(XtreamVodItem(1, name = "The Substance", year = "2024")),
        )
        val service = service(index)
        val item = preview(
            id = "tmdb:933260",
            name = "A Substancia",
            year = "2024",
            alternativeTitles = listOf("The Substance"),
        )

        assertEquals(
            CatalogPlaybackAvailability.LIKELY_AVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
    }

    @Test
    fun `tmdb item without local candidate waits for alias lookup instead of unavailable`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        every { lookup.cachedTitles(any(), any()) } returns null
        val service = service(
            index = index(vod = listOf(XtreamVodItem(1, name = "Duna", year = "2021"))),
            lookup = lookup,
        )
        val item = preview(id = "tmdb:2", name = "Duna", year = "1984")

        assertEquals(
            CatalogPlaybackAvailability.UNKNOWN,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
        verify(exactly = 1) { lookup.requestTitles(2, false) }
    }

    @Test
    fun `missing conservative candidate stays unavailable after alias lookup found nothing`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        every { lookup.cachedTitles(any(), any()) } returns emptyList()
        val service = service(
            index = index(vod = listOf(XtreamVodItem(1, name = "Duna", year = "2021"))),
            lookup = lookup,
        )
        val item = preview(id = "tmdb:2", name = "Duna", year = "1984")

        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
        verify(exactly = 0) { lookup.requestTitles(any(), any()) }
    }

    @Test
    fun `tmdb alternative title from alias lookup makes title likely available`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        every { lookup.cachedTitles(1429, false) } returns listOf("Ataque dos Titãs")
        val service = service(
            index = index(vod = listOf(XtreamVodItem(1, name = "Ataque dos Titãs", year = "2013"))),
            lookup = lookup,
        )
        val item = preview(id = "tmdb:1429", name = "Attack on Titan", year = "2013")

        assertEquals(
            CatalogPlaybackAvailability.LIKELY_AVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
    }

    @Test
    fun `tmdb alternative titles that still do not match stay unavailable`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        every { lookup.cachedTitles(1429, false) } returns listOf("Shingeki no Kyojin")
        val service = service(
            index = index(vod = listOf(XtreamVodItem(1, name = "Ataque dos Titãs", year = "2013"))),
            lookup = lookup,
        )
        val item = preview(id = "tmdb:1429", name = "Attack on Titan", year = "2013")

        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
    }

    @Test
    fun `yearless duplicate title stays unavailable after aliases instead of guessing`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        every { lookup.cachedTitles(any(), any()) } returns emptyList()
        val service = service(
            index(
                vod = listOf(
                    XtreamVodItem(1, name = "Nosferatu"),
                    XtreamVodItem(2, name = "Nosferatu"),
                ),
            ),
            lookup = lookup,
        )
        val item = preview(id = "tmdb:3", name = "Nosferatu", year = "")

        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
        verify(exactly = 0) { lookup.requestTitles(any(), any()) }
    }

    @Test
    fun `exact cached unavailability wins over title candidate`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        val index = index(vod = listOf(XtreamVodItem(1, name = "Duna", year = "2021")))
        val service = service(index, cachedMovies = mapOf(438631 to false), lookup = lookup)
        val item = preview(id = "tmdb:438631", name = "Duna", year = "2021")

        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
        verify(exactly = 0) { lookup.cachedTitles(any(), any()) }
    }

    @Test
    fun `series and movies use separate indexes`() = runTest {
        val index = index(
            vod = listOf(XtreamVodItem(1, name = "Ruptura", year = "2022")),
            series = listOf(XtreamSeriesItem(2, name = "Ruptura", year = "2022")),
        )
        val service = service(index)
        val series = preview("tmdb:95396", "Ruptura", "2022", ContentType.SERIES)

        assertEquals(
            CatalogPlaybackAvailability.LIKELY_AVAILABLE,
            service.classify(listOf(series))[series.catalogAvailabilityKey()],
        )
    }

    @Test
    fun `items remain unknown while index is not ready`() = runTest {
        val service = service(index = null)
        val item = preview(id = "tmdb:1", name = "Duna", year = "2021")

        assertEquals(
            CatalogPlaybackAvailability.UNKNOWN,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
    }

    @Test
    fun `imdb item uses conservative local candidate without tmdb lookup`() = runTest {
        val service = service(index(vod = listOf(XtreamVodItem(1, name = "O Drama", year = "2026"))))
        val item = preview(id = "tt31015278", name = "O Drama", year = "2026")

        assertEquals(
            CatalogPlaybackAvailability.LIKELY_AVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
    }

    @Test
    fun `imdb item without local candidate is unavailable`() = runTest {
        val lookup = mockk<XtreamAlternativeTitleLookup>(relaxed = true)
        val service = service(
            index = index(vod = listOf(XtreamVodItem(1, name = "Outro Filme", year = "2026"))),
            lookup = lookup,
        )
        val item = preview(id = "tt31015278", name = "O Drama", year = "2026")

        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            service.classify(listOf(item))[item.catalogAvailabilityKey()],
        )
        verify(exactly = 0) { lookup.cachedTitles(any(), any()) }
    }

    private fun service(
        index: XtreamCatalogIndex?,
        cachedMovies: Map<Int, Boolean> = emptyMap(),
        cachedSeries: Map<Int, Boolean> = emptyMap(),
        lookup: XtreamAlternativeTitleLookup = mockk(relaxed = true),
    ): XtreamCatalogAvailabilityService {
        val repository = mockk<XtreamCatalogRepository>()
        val store = mockk<XtreamAvailabilityStore>()
        every { repository.state } returns MutableStateFlow(
            if (index == null) XtreamCatalogState.Loading else XtreamCatalogState.Ready(),
        )
        every { repository.currentIndexOrNull() } returns index
        every { store.revision } returns MutableStateFlow(0L)
        coEvery { store.catalogAvailability(any(), any(), any()) } returns CachedCatalogAvailability(
            movies = cachedMovies,
            series = cachedSeries,
        )
        return XtreamCatalogAvailabilityService(repository, store, lookup)
    }

    private fun index(
        vod: List<XtreamVodItem> = emptyList(),
        series: List<XtreamSeriesItem> = emptyList(),
    ): XtreamCatalogIndex = XtreamCatalogIndex.from(
        XtreamCatalogSnapshot(
            fetchedAtMillis = 10L,
            vod = vod,
            series = series,
        ),
        generation = 1L,
    )

    private fun preview(
        id: String,
        name: String,
        year: String,
        type: ContentType = ContentType.MOVIE,
        alternativeTitles: List<String> = emptyList(),
    ) = MetaPreview(
        id = id,
        type = type,
        name = name,
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = year,
        imdbRating = null,
        genres = emptyList(),
        alternativeTitles = alternativeTitles,
    )
}
