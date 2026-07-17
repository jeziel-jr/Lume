package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamVodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamProviderCatalogRepositoryTest {
    @Test
    fun `barbie variants collapse into one provider card`() {
        val variants = listOf(
            movie(10, "Barbie (2023) [4K]", "2023-07-19"),
            movie(20, "Barbie (2023) [FHD]", "2023-07-19"),
            movie(30, "Barbie (2023) [Dublado]", "2023-07-19"),
        )

        assertEquals(1, variants.groupBy { providerMovieGroupKey(it, 346698) }.size)
    }

    @Test
    fun `release date supplies year when provider year is absent`() {
        assertEquals(2023, movie(1, "Barbie", "2023-07-19").releaseYear)
    }

    @Test
    fun `adult and reels categories stay outside visible catalog`() {
        assertTrue(isExcludedProviderCategory("Adultos XXX"))
        assertTrue(isExcludedProviderCategory("ReelsShort | Drama"))
        assertFalse(isExcludedProviderCategory("Cinema | Lançamentos"))
    }

    private fun movie(id: Int, name: String, releaseDate: String) = XtreamVodItem(
        streamId = id,
        name = name,
        releaseDate = releaseDate,
    )
}
