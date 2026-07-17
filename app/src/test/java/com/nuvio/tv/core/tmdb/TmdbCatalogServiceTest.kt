package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbDetailsResponse
import com.nuvio.tv.data.remote.api.TmdbDiscoverResponse
import com.nuvio.tv.data.remote.api.TmdbDiscoverResult
import com.nuvio.tv.data.remote.api.TmdbEpisode
import com.nuvio.tv.data.remote.api.TmdbListDetailsResponse
import com.nuvio.tv.data.remote.api.TmdbListItem
import com.nuvio.tv.data.remote.api.TmdbSeasonResponse
import com.nuvio.tv.data.remote.api.TmdbSeasonSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class TmdbCatalogServiceTest {
    @Test
    fun `home definitions preserve stable order and expose the initial catalogs`() {
        val definitions = TmdbCatalogService(mockk()).homeCatalogDefinitions

        assertEquals("trending-movies", definitions.first().id)
        assertEquals(42, definitions.size)
        assertTrue(definitions.map { it.id }.containsAll(
            listOf(
                "trending-movies",
                "trending-series",
                "popular-movies",
                "popular-series",
                "recent-releases",
                "new-tv-episodes",
            )
        ))
    }

    @Test
    fun `search returns separate movie and series rows with tmdb ids`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.searchMovies(any(), "duna", "pt-BR", 1, false) } returns Response.success(
            TmdbDiscoverResponse(
                results = listOf(
                    TmdbDiscoverResult(
                        10,
                        title = "A Substancia",
                        originalTitle = "The Substance",
                        releaseDate = "2024-09-20",
                    ),
                ),
            )
        )
        coEvery { api.searchTv(any(), "duna", "pt-BR", 1, false) } returns Response.success(
            TmdbDiscoverResponse(results = listOf(TmdbDiscoverResult(20, name = "Duna: A Profecia", firstAirDate = "2024-11-17")))
        )

        val rows = TmdbCatalogService(api).search("duna")

        assertEquals(listOf("Filmes", "Series"), rows.map { it.catalogName })
        assertEquals(listOf("tmdb:10", "tmdb:20"), rows.flatMap { it.items }.map { it.id })
        assertEquals(listOf("The Substance"), rows.first().items.first().alternativeTitles)
    }

    @Test
    fun `series videos have stable ids metadata and future availability`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getTvDetails(30, any(), "pt-BR") } returns Response.success(
            TmdbDetailsResponse(id = 30, seasons = listOf(TmdbSeasonSummary(1, 2)))
        )
        coEvery { api.getTvSeasonDetails(30, 1, any(), "pt-BR") } returns Response.success(
            TmdbSeasonResponse(
                seasonNumber = 1,
                episodes = listOf(
                    TmdbEpisode(1, "Piloto", "Inicio", "/one.jpg", "2020-01-01", 52),
                    TmdbEpisode(2, "Futuro", "Depois", "/two.jpg", "2999-01-01", 48)
                )
            )
        )

        val videos = TmdbMetadataService(api).fetchSeriesVideos("30", "pt-BR")

        assertEquals(listOf("tmdb:30:1:1", "tmdb:30:1:2"), videos.map { it.id })
        assertEquals("https://image.tmdb.org/t/p/w780/one.jpg", videos.first().thumbnail)
        assertTrue(videos.first().available == true)
        assertFalse(videos.last().available == true)
    }

    @Test
    fun `home keeps cached rows when tmdb becomes unavailable`() = runTest {
        val api = mockk<TmdbApi>()
        val movieResponse = Response.success(
            TmdbDiscoverResponse(results = listOf(TmdbDiscoverResult(1, title = "Filme")))
        )
        val tvResponse = Response.success(
            TmdbDiscoverResponse(results = listOf(TmdbDiscoverResult(2, name = "Serie")))
        )
        coEvery { api.discoverMovies(any(), any(), any(), any(), any(), any(), any()) } returns movieResponse
        coEvery { api.discoverTv(any(), any(), any(), any(), any(), any(), any(), any()) } returns tvResponse
        val service = TmdbCatalogService(api)
        val initial = service.home()
        coEvery { api.discoverMovies(any(), any(), any(), any(), any(), any(), any()) } throws IOException("offline")
        coEvery { api.discoverTv(any(), any(), any(), any(), any(), any(), any(), any()) } throws IOException("offline")

        val cached = service.home()

        assertEquals(initial, cached)
        assertTrue(cached.isNotEmpty())
    }

    @Test
    fun `home page exposes next page for unique append`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.discoverMovies(any(), any(), any(), any(), any(), any(), any()) } returns Response.success(
            TmdbDiscoverResponse(
                page = 2,
                totalPages = 3,
                results = listOf(TmdbDiscoverResult(3, title = "Outro filme"))
            )
        )

        val page = TmdbCatalogService(api).homePage("popular-movies", 2)

        assertEquals(2, page?.currentPage)
        assertTrue(page?.hasMore == true)
        assertEquals(listOf("tmdb:3"), page?.items?.map { it.id })
    }

    @Test
    fun `platform page merges movies and series available in brazil`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery {
            api.discoverMovies(
                apiKey = any(),
                language = "pt-BR",
                page = 1,
                sortBy = "popularity.desc",
                withCompanies = null,
                releaseDateLte = any(),
                voteCountGte = 30,
                withGenres = null,
                releaseDateGte = null,
                voteAverageGte = null,
                voteAverageLte = null,
                withOriginalLanguage = null,
                withOriginCountry = null,
                withKeywords = null,
                year = null,
                watchRegion = "BR",
                withWatchProviders = "8",
                withWatchMonetizationTypes = "flatrate",
            )
        } returns Response.success(
            TmdbDiscoverResponse(
                page = 1,
                totalPages = 2,
                results = listOf(TmdbDiscoverResult(10, title = "Filme", popularity = 10.0)),
            ),
        )
        coEvery {
            api.discoverTv(
                apiKey = any(),
                language = "pt-BR",
                page = 1,
                sortBy = "popularity.desc",
                withCompanies = null,
                withNetworks = null,
                firstAirDateLte = any(),
                voteCountGte = 30,
                withGenres = null,
                firstAirDateGte = null,
                voteAverageGte = null,
                voteAverageLte = null,
                withOriginalLanguage = null,
                withOriginCountry = null,
                withKeywords = null,
                firstAirDateYear = null,
                withStatus = null,
                watchRegion = "BR",
                withWatchProviders = "8",
                withWatchMonetizationTypes = "flatrate",
            )
        } returns Response.success(
            TmdbDiscoverResponse(
                page = 1,
                totalPages = 3,
                results = listOf(TmdbDiscoverResult(20, name = "Serie", popularity = 20.0)),
            ),
        )

        val page = TmdbCatalogService(api).homePage("netflix", 1)

        assertEquals(listOf("tmdb:20", "tmdb:10"), page?.items?.map { it.id })
        assertEquals(listOf("series", "movie"), page?.items?.map { it.rawType })
        assertEquals("true", page?.extraArgs?.get("hideTypeSuffix"))
        assertTrue(page?.hasMore == true)
    }

    @Test
    fun `editorial list keeps mixed media types and pagination`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getListDetails(84979, any(), "pt-BR", 1) } returns Response.success(
            TmdbListDetailsResponse(
                id = 84979,
                page = 1,
                totalPages = 4,
                items = listOf(
                    TmdbListItem(1, title = "Filme Marvel", mediaType = "movie"),
                    TmdbListItem(2, name = "Serie Marvel", mediaType = "tv"),
                ),
            ),
        )

        val page = TmdbCatalogService(api).homePage("marvel-universe", 1)

        assertEquals(listOf("movie", "series"), page?.items?.map { it.rawType })
        assertTrue(page?.hasMore == true)
    }
}
