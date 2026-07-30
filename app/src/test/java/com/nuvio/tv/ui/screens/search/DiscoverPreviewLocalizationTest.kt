package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.core.tmdb.TmdbLocalizedPreview
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverPreviewLocalizationTest {
    @Test
    fun `localized TMDB preview replaces Cinemeta identity title and artwork`() {
        val raw = preview(
            id = "tt0114709",
            name = "Toy Story",
            poster = "https://cinemeta.example/toy-story.jpg",
        )
        val localized = raw.withLocalizedTmdbPreview(
            TmdbLocalizedPreview(
                tmdbId = 862,
                imdbId = "tt0114709",
                localizedTitle = "Toy Story: Um Mundo de Aventuras",
                originalTitle = "Toy Story",
                posterUrl = "https://image.tmdb.org/t/p/w500/pt-poster.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
                description = "Brinquedos ganham vida.",
                releaseDate = "1995-12-22",
                rating = 8.0f,
                voteCount = 20_000,
            )
        )

        assertEquals("tmdb:862", localized.id)
        assertEquals("tt0114709", localized.imdbId)
        assertEquals("Toy Story: Um Mundo de Aventuras", localized.name)
        assertEquals("https://image.tmdb.org/t/p/w500/pt-poster.jpg", localized.poster)
        assertEquals("1995", localized.releaseInfo)
        assertTrue("Toy Story" in localized.alternativeTitles)
        assertEquals(raw.discoverIdentityKey(), localized.discoverIdentityKey())
    }

    private fun preview(id: String, name: String, poster: String?) = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        name = name,
        poster = poster,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = "1995",
        imdbRating = null,
        genres = emptyList(),
    )
}
