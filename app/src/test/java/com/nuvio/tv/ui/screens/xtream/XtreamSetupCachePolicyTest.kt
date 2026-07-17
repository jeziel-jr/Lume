package com.nuvio.tv.ui.screens.xtream

import com.nuvio.tv.data.xtream.XtreamCredentials
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamSetupCachePolicyTest {
    private val account = XtreamCredentials("http://provider.example", "viewer", "secret")

    @Test
    fun `signing in after sign out keeps the existing catalog cache`() {
        assertFalse(shouldClearXtreamCache(previous = null, next = account))
    }

    @Test
    fun `changing a configured source clears the active source cache`() {
        assertTrue(
            shouldClearXtreamCache(
                previous = account,
                next = account.copy(username = "another-viewer"),
            ),
        )
    }
}
