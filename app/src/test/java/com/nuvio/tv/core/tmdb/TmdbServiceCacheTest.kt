package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbExternalIdsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class TmdbServiceCacheTest {
    @Test
    fun `movie and tv mappings with the same tmdb number use separate cache entries`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieExternalIds(100, any()) } returns
            Response.success(TmdbExternalIdsResponse(id = 100, imdbId = "tt-movie"))
        coEvery { api.getTvExternalIds(100, any()) } returns
            Response.success(TmdbExternalIdsResponse(id = 100, imdbId = "tt-series"))
        val service = TmdbService(api)

        assertEquals("tt-movie", service.tmdbToImdb(100, "movie"))
        assertEquals("tt-series", service.tmdbToImdb(100, "series"))
        assertEquals("tt-movie", service.tmdbToImdb(100, "movie"))
        assertEquals("tt-series", service.tmdbToImdb(100, "tv"))

        coVerify(exactly = 1) { api.getMovieExternalIds(100, any()) }
        coVerify(exactly = 1) { api.getTvExternalIds(100, any()) }
    }
}
