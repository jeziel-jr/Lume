package com.nuvio.tv.data.xtream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamEndpointPolicyTest {

    private val primary = "https://primary.test"
    private val secondary = "https://secondary.test"

    @Test
    fun `sanitize keeps https and http endpoints from the signed document`() {
        val endpoints = XtreamEndpointPolicy.sanitize(
            listOf("http://capone.icu", "https://a.test"),
        )

        assertEquals(listOf("http://capone.icu", "https://a.test"), endpoints)
    }

    @Test
    fun `sanitize drops endpoints that are not http or https`() {
        val endpoints = XtreamEndpointPolicy.sanitize(
            listOf("ftp://a.test", "file:///etc/passwd", "a.test", "https://b.test"),
        )

        assertEquals(listOf("https://b.test"), endpoints)
    }

    @Test
    fun `sanitize normalizes trailing slashes and duplicates`() {
        val endpoints = XtreamEndpointPolicy.sanitize(
            listOf("https://a.test/", " https://a.test ", "https://a.test"),
        )

        assertEquals(listOf("https://a.test"), endpoints)
    }

    @Test
    fun `keeps the current endpoint while it is the published primary`() = runTest {
        var probes = 0

        val target = XtreamEndpointPolicy.selectForConfig(listOf(primary, secondary), primary) {
            probes += 1
            true
        }

        assertNull(target)
        assertEquals("an unchanged endpoint must not be probed", 0, probes)
    }

    @Test
    fun `switches to the new primary when it answers`() = runTest {
        val target = XtreamEndpointPolicy.selectForConfig(listOf(primary, secondary), secondary) { it == primary }

        assertEquals(primary, target)
    }

    @Test
    fun `skips a candidate that does not answer`() = runTest {
        val target = XtreamEndpointPolicy.selectForConfig(listOf(primary, secondary), "https://old.test") {
            it == secondary
        }

        assertEquals(secondary, target)
    }

    @Test
    fun `keeps the current endpoint when no candidate answers`() = runTest {
        val target = XtreamEndpointPolicy.selectForConfig(listOf(primary, secondary), "https://old.test") { false }

        assertNull(target)
    }

    @Test
    fun `failure selection keeps a current endpoint that still answers`() = runTest {
        val target = XtreamEndpointPolicy.selectForFailure(listOf(primary, secondary), primary) { true }

        assertNull(target)
    }

    @Test
    fun `failure selection moves to the first candidate that answers`() = runTest {
        val probed = mutableListOf<String>()

        val target = XtreamEndpointPolicy.selectForFailure(listOf(primary, secondary), "https://dead.test") {
            probed += it
            it == secondary
        }

        assertEquals(secondary, target)
        assertEquals(listOf("https://dead.test", primary, secondary), probed)
    }

    @Test
    fun `failure selection keeps the current endpoint when everything is down`() = runTest {
        val target = XtreamEndpointPolicy.selectForFailure(listOf(primary, secondary), "https://dead.test") { false }

        assertNull(target)
    }
}
