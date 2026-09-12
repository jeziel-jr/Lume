package com.nuvio.tv.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigParserTest {

    private val moshi = PublishedRemoteConfig.moshi

    @Test
    fun `parses a valid document`() {
        val config = RemoteConfigParser.parse(
            moshi,
            """{"schema":1,"revision":7,"keyId":"k1","issuedAt":"2026-10-01T12:00:00Z","endpoints":["https://a.test","https://b.test"]}""",
        )

        assertEquals(7, config?.revision)
        assertEquals("k1", config?.keyId)
        assertEquals(listOf("https://a.test", "https://b.test"), config?.endpoints)
    }

    @Test
    fun `drops blank endpoints`() {
        val config = RemoteConfigParser.parse(
            moshi,
            """{"schema":1,"revision":1,"keyId":"k1","endpoints":["https://a.test","","  "]}""",
        )

        assertEquals(listOf("https://a.test"), config?.endpoints)
    }

    @Test
    fun `rejects an unsupported schema`() {
        val config = RemoteConfigParser.parse(
            moshi,
            """{"schema":2,"revision":1,"keyId":"k1","endpoints":["https://a.test"]}""",
        )

        assertNull(config)
    }

    @Test
    fun `rejects a non positive revision`() {
        val config = RemoteConfigParser.parse(
            moshi,
            """{"schema":1,"revision":0,"keyId":"k1","endpoints":["https://a.test"]}""",
        )

        assertNull(config)
    }

    @Test
    fun `rejects a blank key identifier`() {
        val config = RemoteConfigParser.parse(
            moshi,
            """{"schema":1,"revision":1,"keyId":"","endpoints":["https://a.test"]}""",
        )

        assertNull(config)
    }

    @Test
    fun `rejects malformed json`() {
        assertNull(RemoteConfigParser.parse(moshi, "<html>not a document</html>"))
    }
}
