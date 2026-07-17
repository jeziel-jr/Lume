package com.nuvio.tv.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAutoPlayPolicyDirectTmdbTest {
    @Test
    fun `normal tmdb route forces direct playback`() {
        assertTrue(StreamAutoPlayPolicy.shouldForceDirectTmdbPlayback("tmdb:438631", false))
    }

    @Test
    fun `manual tmdb route preserves source selection`() {
        assertFalse(StreamAutoPlayPolicy.shouldForceDirectTmdbPlayback("tmdb:series:95396", true))
    }

    @Test
    fun `non tmdb route keeps global autoplay policy`() {
        assertFalse(StreamAutoPlayPolicy.shouldForceDirectTmdbPlayback("local:movie:1", false))
    }

    @Test
    fun `tmdb detail canonicalizes addon episode id for direct playback`() {
        assertEquals(
            "tmdb:95396:2:4",
            StreamAutoPlayPolicy.canonicalTmdbVideoId("tmdb:95396", "tt11280740:2:4", 2, 4)
        )
    }
}
