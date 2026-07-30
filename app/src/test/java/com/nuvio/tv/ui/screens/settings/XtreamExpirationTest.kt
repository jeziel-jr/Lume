package com.nuvio.tv.ui.screens.settings

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamExpirationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testMissingExpiration() {
        assertEquals(XtreamExpiration.Missing, classifyXtreamExpiration(null, now))
    }

    @Test
    fun testInvalidExpirationEpoch() {
        assertEquals(XtreamExpiration.Missing, classifyXtreamExpiration(Long.MAX_VALUE, now))
    }

    @Test
    fun testExpiredExpiration() {
        val expiration = now.minusSeconds(1).epochSecond

        assertEquals(XtreamExpiration.Expired, classifyXtreamExpiration(expiration, now))
    }

    @Test
    fun testExpirationInLessThanOneMinute() {
        val expiration = now.plusSeconds(59).epochSecond

        assertEquals(XtreamExpiration.LessThanMinute, classifyXtreamExpiration(expiration, now))
    }

    @Test
    fun testExpirationExactlyInTwentyFourHours() {
        val expiration = now.plus(Duration.ofHours(24)).epochSecond

        assertEquals(XtreamExpiration.Days(1), classifyXtreamExpiration(expiration, now))
    }

    @Test
    fun testExpirationMoreThanTwentyFourHoursWithWholeDays() {
        val expiration = now.plus(Duration.ofHours(72)).epochSecond

        assertEquals(XtreamExpiration.Days(3), classifyXtreamExpiration(expiration, now))
    }

    @Test
    fun testExpirationBelowTwentyFourHoursWithHoursAndMinutes() {
        val expiration = now.plus(Duration.ofHours(5)).plus(Duration.ofMinutes(42)).epochSecond

        assertEquals(
            XtreamExpiration.HoursAndMinutes(hours = 5, minutes = 42),
            classifyXtreamExpiration(expiration, now),
        )
    }
}
