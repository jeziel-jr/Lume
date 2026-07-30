package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbFindResponse
import com.nuvio.tv.data.remote.api.TmdbFindResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class TmdbServiceLocalizedPreviewTest {
    @Test
    fun `localized preview resolves IMDb identity and caches localized artwork`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery {
            api.findByExternalId("tt0114709", any(), "imdb_id", "pt-BR")
        } returns Response.success(
            TmdbFindResponse(
                movieResults = listOf(
                    TmdbFindResult(
                        id = 862,
                        title = "Toy Story: Um Mundo de Aventuras",
                        originalTitle = "Toy Story",
                        posterPath = "/pt-poster.jpg",
                        backdropPath = "/backdrop.jpg",
                        overview = "Brinquedos ganham vida.",
                        releaseDate = "1995-12-22",
                        voteAverage = 8.0,
                        voteCount = 20_000,
                    )
                )
            )
        )
        val service = TmdbService(api)

        val first = service.resolveLocalizedPreview("tt0114709", "movie", "pt-BR")
        val cached = service.resolveLocalizedPreview("tt0114709", "movie", "pt-BR")

        assertEquals(862, first?.tmdbId)
        assertEquals("Toy Story: Um Mundo de Aventuras", first?.localizedTitle)
        assertEquals("https://image.tmdb.org/t/p/w500/pt-poster.jpg", first?.posterUrl)
        assertEquals(first, cached)
        coVerify(exactly = 1) {
            api.findByExternalId("tt0114709", any(), "imdb_id", "pt-BR")
        }
    }
}
