package com.nuvio.tv.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamUserInfoTest {
    @Test
    fun `parses account metadata returned as strings`() {
        val info = XtreamUserInfo(
            auth = "1",
            status = "Active",
            username = "viewer",
            expirationDate = "1798761600",
            activeConnections = "1",
            maxConnections = "3",
        )

        assertTrue(info.isAuthorized)
        assertEquals(1798761600L, info.expirationEpochSeconds)
        assertEquals(1, info.activeConnectionCount)
        assertEquals(3, info.maximumConnectionCount)
    }

    @Test
    fun `ignores missing or non expiring account date`() {
        assertNull(XtreamUserInfo(expirationDate = "null").expirationEpochSeconds)
        assertNull(XtreamUserInfo(expirationDate = 0).expirationEpochSeconds)
    }
}
