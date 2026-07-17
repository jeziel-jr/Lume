package com.nuvio.tv.data.xtream

import com.nuvio.tv.core.tmdb.TmdbEnrichment
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XtreamPlaybackServiceTest {
    private val metadataService = mockk<TmdbMetadataService>()
    private val resolver = mockk<XtreamPlaybackResolver>()
    private val service = XtreamPlaybackService(metadataService, resolver)

    @Test
    fun `movie availability preserves available unavailable and provider error`() = runTest {
        coEvery { metadataService.fetchEnrichment(any(), any(), any()) } returns enrichment()
        coEvery { resolver.movieAvailability(438631, any(), 2021) } returnsMany listOf(
            XtreamAvailability.AVAILABLE,
            XtreamAvailability.UNAVAILABLE,
            XtreamAvailability.ERROR,
        )

        assertEquals(XtreamAvailability.AVAILABLE, service.movieAvailability(438631))
        assertEquals(XtreamAvailability.UNAVAILABLE, service.movieAvailability(438631))
        assertEquals(XtreamAvailability.ERROR, service.movieAvailability(438631))
    }

    @Test
    fun `partial series response remains available but incomplete`() = runTest {
        coEvery { metadataService.fetchEnrichment(any(), any(), any()) } returns enrichment()
        coEvery { resolver.seriesAvailability(95396, any(), 2021) } returns
            XtreamSeriesAvailability.Resolved(setOf(1 to 1), complete = false)

        val result = service.seriesAvailability(95396)

        assertEquals(XtreamAvailability.AVAILABLE, result.status)
        assertEquals(setOf(1 to 1), result.availableEpisodes)
        assertFalse(result.complete)
    }

    @Test
    fun `missing tmdb metadata is an error rather than unavailable`() = runTest {
        coEvery { metadataService.fetchEnrichment(any(), any(), any()) } returns null

        assertEquals(XtreamAvailability.ERROR, service.movieAvailability(438631))
        assertEquals(XtreamAvailability.ERROR, service.seriesAvailability(95396).status)
    }

    private fun enrichment(): TmdbEnrichment = mockk {
        every { localizedTitle } returns "Duna"
        every { originalTitle } returns "Dune"
        every { alternativeTitles } returns listOf("Duna: Parte Um")
        every { releaseInfo } returns "2021-10-21"
    }
}
