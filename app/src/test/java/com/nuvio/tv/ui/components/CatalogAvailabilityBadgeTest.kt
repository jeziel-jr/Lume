package com.nuvio.tv.ui.components

import com.nuvio.tv.data.xtream.CatalogPlaybackAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAvailabilityBadgeTest {
    @Test
    fun `badge is shown only for unavailable and checking states`() {
        assertTrue(CatalogPlaybackAvailability.UNAVAILABLE.showsCatalogAvailabilityBadge())
        assertTrue(CatalogPlaybackAvailability.UNKNOWN.showsCatalogAvailabilityBadge())
        assertFalse(CatalogPlaybackAvailability.AVAILABLE.showsCatalogAvailabilityBadge())
        assertFalse(CatalogPlaybackAvailability.LIKELY_AVAILABLE.showsCatalogAvailabilityBadge())
        assertFalse(null.showsCatalogAvailabilityBadge())
    }
}
