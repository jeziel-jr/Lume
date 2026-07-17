package com.nuvio.tv.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamServerHealthMonitorTest {
    @Test
    fun `empty successful response is a provider failure`() {
        val result = XtreamProbeClassifier.classify(200, "text/html", ByteArray(0))

        assertEquals(XtreamHealthState.PROVIDER_FAILURE, result.state)
        assertEquals(XtreamHealthReason.EMPTY_RESPONSE, result.reason)
    }

    @Test
    fun `html and json documents are provider failures`() {
        val html = XtreamProbeClassifier.classify(200, "text/html", "<html>Error</html>".toByteArray())
        val json = XtreamProbeClassifier.classify(200, "application/json", "{\"error\":true}".toByteArray())

        assertEquals(XtreamHealthReason.ERROR_DOCUMENT, html.reason)
        assertEquals(XtreamHealthReason.ERROR_DOCUMENT, json.reason)
    }

    @Test
    fun `mp4 signature is healthy even with generic content type`() {
        val bytes = ByteArray(32).apply {
            "ftyp".toByteArray().copyInto(this, destinationOffset = 4)
        }

        val result = XtreamProbeClassifier.classify(206, "application/octet-stream", bytes)

        assertEquals(XtreamHealthState.HEALTHY, result.state)
    }

    @Test
    fun `transport stream signature is healthy`() {
        val bytes = ByteArray(376).apply {
            this[0] = 0x47
            this[188] = 0x47
        }

        assertEquals(
            XtreamHealthState.HEALTHY,
            XtreamProbeClassifier.classify(200, null, bytes).state,
        )
    }

    @Test
    fun `provider status codes keep actionable reasons`() {
        assertEquals(
            XtreamHealthReason.HTTP_UNAUTHORIZED,
            XtreamProbeClassifier.classify(401, null, ByteArray(0)).reason,
        )
        assertEquals(
            XtreamHealthReason.HTTP_FORBIDDEN,
            XtreamProbeClassifier.classify(403, null, ByteArray(0)).reason,
        )
        assertEquals(
            XtreamHealthReason.HTTP_RATE_LIMITED,
            XtreamProbeClassifier.classify(429, null, ByteArray(0)).reason,
        )
        assertEquals(
            XtreamHealthReason.HTTP_SERVER_ERROR,
            XtreamProbeClassifier.classify(503, null, ByteArray(0)).reason,
        )
    }

    @Test
    fun `healthy and failed media components aggregate as degraded`() {
        val health = XtreamServerHealth(
            movies = XtreamComponentHealth(XtreamHealthState.HEALTHY),
            series = XtreamComponentHealth(
                XtreamHealthState.PROVIDER_FAILURE,
                XtreamHealthReason.EMPTY_RESPONSE,
            ),
        )

        assertEquals(XtreamHealthState.DEGRADED, health.playback.state)
        assertEquals(XtreamHealthReason.EMPTY_RESPONSE, health.playback.reason)
    }

    @Test
    fun `two provider failures aggregate as unavailable`() {
        val failed = XtreamComponentHealth(
            XtreamHealthState.PROVIDER_FAILURE,
            XtreamHealthReason.ERROR_DOCUMENT,
        )

        assertEquals(
            XtreamHealthState.PROVIDER_FAILURE,
            XtreamServerHealth(movies = failed, series = failed).playback.state,
        )
    }
}
