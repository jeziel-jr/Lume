package com.nuvio.tv.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamCredentialsTest {
    @Test
    fun `normalizes host and player api path`() {
        assertEquals("http://provider.example:8080", normalizeXtreamBaseUrl("provider.example:8080/player_api.php/"))
        assertEquals("https://provider.example", normalizeXtreamBaseUrl(" https://provider.example/ "))
    }

    @Test
    fun `complete credentials require all fields`() {
        assertTrue(XtreamCredentials("provider.example", "user", "pass").normalized().isComplete)
    }
}
