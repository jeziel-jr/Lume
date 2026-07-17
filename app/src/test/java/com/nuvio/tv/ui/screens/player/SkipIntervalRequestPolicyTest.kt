package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipIntervalRequestPolicyTest {
    @Test
    fun `latest episode request may publish`() {
        assertTrue(shouldPublishSkipIntervals("tmdb:1:1:2", "tmdb:1:1:2"))
    }

    @Test
    fun `stale episode request cannot publish`() {
        assertFalse(shouldPublishSkipIntervals("tmdb:1:1:2", "tmdb:1:1:3"))
        assertFalse(shouldPublishSkipIntervals("tmdb:1:1:2", null))
    }
}
