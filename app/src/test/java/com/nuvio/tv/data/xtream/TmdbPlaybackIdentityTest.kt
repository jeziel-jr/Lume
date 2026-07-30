package com.nuvio.tv.data.xtream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbPlaybackIdentityTest {
    @Test
    fun `tmdb movie and episode ids resolve without external lookup`() = runTest {
        var lookups = 0
        val lookup: suspend (String, String) -> String? = { _, _ ->
            lookups += 1
            null
        }

        assertEquals(438631, resolveTmdbPlaybackId(listOf("tmdb:438631"), "movie", lookup))
        assertEquals(1396, resolveTmdbPlaybackId(listOf("tmdb:1396:1:1"), "series", lookup))
        assertEquals(0, lookups)
    }

    @Test
    fun `imdb movie and episode ids convert through tmdb lookup`() = runTest {
        val seen = mutableListOf<Pair<String, String>>()
        val lookup: suspend (String, String) -> String? = { id, type ->
            seen += id to type
            "1396"
        }

        assertEquals(1396, resolveTmdbPlaybackId(listOf("tt0903747"), "movie", lookup))
        assertEquals(1396, resolveTmdbPlaybackId(listOf("tt0903747:1:1"), "series", lookup))
        assertEquals(
            listOf("tt0903747" to "movie", "tt0903747:1:1" to "series"),
            seen,
        )
    }

    @Test
    fun `unconvertible legacy id preserves legacy fallback`() = runTest {
        assertNull(
            resolveTmdbPlaybackId(listOf("kitsu:12:1"), "series") { _, _ -> "1396" }
        )
    }
}
