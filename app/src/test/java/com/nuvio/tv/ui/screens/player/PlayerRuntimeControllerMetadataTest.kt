package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRuntimeControllerMetadataTest {

    private fun video(season: Int, episode: Int, id: String) = Video(
        id = id,
        title = "Episode $episode",
        released = "2020-01-01",
        thumbnail = null,
        season = season,
        episode = episode,
        overview = null,
    )

    @Test
    fun `tmdb series fallback is eligible for empty legacy metadata and valid numeric ids`() {
        assertEquals(
            "123",
            tmdbSeriesFallbackId(
                id = "tmdb:123",
                type = "series",
                legacyVideos = emptyList(),
            )
        )
        assertEquals(
            "456",
            tmdbSeriesFallbackId(
                id = "tmdb:456",
                type = "tv",
                legacyVideos = emptyList(),
            )
        )
    }

    @Test
    fun `tmdb series fallback does not replace legacy videos or accept invalid content`() {
        val legacyVideo = video(1, 1, "legacy-1")

        assertNull(tmdbSeriesFallbackId("tmdb:123", "series", listOf(legacyVideo)))
        assertNull(tmdbSeriesFallbackId("tmdb:not-a-number", "series", emptyList()))
        assertNull(tmdbSeriesFallbackId("tmdb:123", "movie", emptyList()))
    }

    @Test
    fun `only nonempty tmdb videos for the active episode request may be published`() {
        val request = PlayerMetadataRequestKey(
            contentId = "tmdb:123",
            contentType = "series",
            videoId = "tmdb:123:1:1",
            season = 1,
            episode = 1,
        )
        val tmdbVideos = listOf(video(1, 1, "tmdb:123:1:1"))

        assertTrue(
            shouldPublishTmdbSeriesVideos(
                requestKey = request,
                activeRequestKey = request,
                currentMetaVideos = emptyList(),
                tmdbVideos = tmdbVideos,
            )
        )
        assertFalse(
            shouldPublishTmdbSeriesVideos(
                requestKey = request,
                activeRequestKey = request.copy(episode = 2),
                currentMetaVideos = emptyList(),
                tmdbVideos = tmdbVideos,
            )
        )
        assertFalse(
            shouldPublishTmdbSeriesVideos(
                requestKey = request,
                activeRequestKey = request,
                currentMetaVideos = listOf(video(1, 1, "legacy-1")),
                tmdbVideos = tmdbVideos,
            )
        )
    }

    @Test
    fun `tmdb series videos resolve the next episode without numeric inference`() {
        val videos = listOf(
            video(1, 1, "tmdb:123:1:1"),
            video(1, 2, "tmdb:123:1:2"),
        )

        val next = PlayerNextEpisodeRules.resolveNextEpisode(
            videos = videos,
            currentSeason = 1,
            currentEpisode = 1,
        )

        assertEquals("tmdb:123:1:2", next?.id)
        assertEquals(2, next?.episode)
    }
}
