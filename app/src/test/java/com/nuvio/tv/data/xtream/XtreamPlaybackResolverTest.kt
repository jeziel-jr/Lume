package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamEpisode
import com.nuvio.tv.data.remote.api.XtreamMovieData
import com.nuvio.tv.data.remote.api.XtreamSeriesInfoResponse
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodInfo
import com.nuvio.tv.data.remote.api.XtreamVodInfoResponse
import com.nuvio.tv.data.remote.api.XtreamVodItem
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamPlaybackResolverTest {
    @Test
    fun `normalization removes accents year and media tags`() {
        assertEquals("cidade de deus", XtreamTitleMatcher.normalize("Cidade de Deus (2002) [4K Dublado]"))
    }

    @Test
    fun `movie resolves only candidate with matching tmdb id`() = runTest {
        val source = FakeSource(
            vod = listOf(XtreamVodItem(10, name = "Duna 2021 4K Dublado", year = "2021", containerExtension = "mkv")),
            vodInfo = mapOf(10 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(10, "mkv")))
        )

        val result = resolver(source).resolveMovie(438631, listOf("Duna", "Dune"), 2021)

        assertTrue(result is XtreamResolution.Available)
        val stream = (result as XtreamResolution.Available).source.streams.single()
        assertEquals("http://example.com/movie/user/pass/10.mkv", stream.url)
    }

    @Test
    fun `movie rejects title match with different tmdb id`() = runTest {
        val source = FakeSource(
            vod = listOf(XtreamVodItem(10, name = "Duna 2021", year = "2021")),
            vodInfo = mapOf(10 to XtreamVodInfoResponse(XtreamVodInfo("999"), XtreamMovieData(10, "mp4")))
        )

        assertEquals(XtreamResolution.Unavailable, resolver(source).resolveMovie(438631, listOf("Duna"), 2021))
    }

    @Test
    fun `movie accepts conservative title and year match when provider tmdb id is zero`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(
                    91811,
                    name = "Barbie e O Segredo das Fadas (2011)",
                    containerExtension = "mp4",
                ),
            ),
            vodInfo = mapOf(
                91811 to XtreamVodInfoResponse(
                    XtreamVodInfo("0"),
                    XtreamMovieData(91811, "mp4"),
                ),
            ),
        )

        val result = resolver(source).resolveMovie(
            tmdbId = 57737,
            titles = listOf("Barbie e o Segredo das Fadas", "Barbie: A Fairy Secret"),
            year = 2011,
        )

        assertTrue(result is XtreamResolution.Available)
        assertEquals(
            "http://example.com/movie/user/pass/91811.mp4",
            (result as XtreamResolution.Available).source.streams.single().url,
        )
    }

    @Test
    fun `barbie live action uses provider release date when year field is absent`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(
                    streamId = 100,
                    name = "Barbie (2023) [4K Dublado]",
                    releaseDate = "2023-07-19",
                    containerExtension = "mkv",
                ),
            ),
            vodInfo = mapOf(
                100 to XtreamVodInfoResponse(
                    XtreamVodInfo("346698"),
                    XtreamMovieData(100, "mkv"),
                ),
            ),
        )

        val result = resolver(source).resolveMovie(
            tmdbId = 346698,
            titles = listOf("Barbie"),
            year = 2023,
        )

        assertTrue(result is XtreamResolution.Available)
    }

    @Test
    fun `odyssey future release does not match older provider movie`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(
                    streamId = 200,
                    name = "A Odisséia",
                    releaseDate = "2016-04-01",
                ),
            ),
        )

        val result = resolver(source).resolveMovie(
            tmdbId = 1368337,
            titles = listOf("A Odisseia"),
            year = 2026,
        )

        assertEquals(XtreamResolution.Unavailable, result)
        assertTrue(source.requestedVodDetails.isEmpty())
    }

    @Test
    fun `movie rejects same title from a different year before detail lookup`() = runTest {
        val source = FakeSource(vod = listOf(XtreamVodItem(10, name = "Duna", year = "1984")))

        assertEquals(XtreamResolution.Unavailable, resolver(source).resolveMovie(438631, listOf("Duna"), 2021))
        assertTrue(source.requestedVodDetails.isEmpty())
    }

    @Test
    fun `movie with missing provider year still requires matching tmdb id`() = runTest {
        val source = FakeSource(
            vod = listOf(XtreamVodItem(10, name = "Duna")),
            vodInfo = mapOf(10 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(10, "mp4")))
        )

        assertTrue(resolver(source).resolveMovie(438631, listOf("Duna"), 2021) is XtreamResolution.Available)
    }

    @Test
    fun `series accepts a unique exact title when provider year is absent`() = runTest {
        val source = FakeSource(
            series = listOf(XtreamSeriesItem(20, name = "Ruptura")),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("55", 1, 1, "mkv"))))
            )
        )

        assertTrue(resolver(source).resolveEpisode(listOf("Ruptura"), 2022, 1, 1) is XtreamResolution.Available)
    }

    @Test
    fun `series rejects ambiguous exact titles when provider years are absent`() = runTest {
        val source = FakeSource(
            series = listOf(
                XtreamSeriesItem(20, name = "Ruptura"),
                XtreamSeriesItem(21, name = "Ruptura")
            )
        )

        assertEquals(XtreamResolution.Unavailable, resolver(source).resolveEpisode(listOf("Ruptura"), 2022, 1, 1))
        assertTrue(source.requestedSeriesDetails.isEmpty())
    }

    @Test
    fun `series uses provider release date when year field is absent`() = runTest {
        val source = FakeSource(
            series = listOf(XtreamSeriesItem(20, name = "Agatha Desde Sempre", releaseDate = "2024-09-18")),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("88", "1", 1, "mkv"))))
            )
        )

        val result = resolver(source).resolveEpisode(listOf("Agatha Desde Sempre"), 2024, 1, 1)

        assertTrue(result is XtreamResolution.Available)
    }

    @Test
    fun `dubbed higher quality stream ranks first`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "Duna 2021 Legendado 720p", year = "2021"),
                XtreamVodItem(2, name = "Duna 2021 Dublado 1080p", year = "2021")
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(1, "mp4")),
                2 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(2, "mp4"))
            )
        )

        val result = resolver(source).resolveMovie(438631, listOf("Duna"), 2021) as XtreamResolution.Available

        assertEquals("http://example.com/movie/user/pass/2.mp4", result.source.streams.first().url)
    }

    @Test
    fun `repeated movie resolution reuses detail and result caches`() = runTest {
        val source = FakeSource(
            vod = listOf(XtreamVodItem(10, name = "Duna", year = "2021")),
            vodInfo = mapOf(10 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(10, "mp4")))
        )
        val resolver = resolver(source)

        resolver.resolveMovie(438631, listOf("Duna"), 2021)
        resolver.resolveMovie(438631, listOf("Duna"), 2021)

        assertEquals(listOf(10), source.requestedVodDetails)
        assertEquals(1, source.vodCatalogRequests)
    }

    @Test
    fun `partial candidate failure still returns successful stream`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "Duna Dublado", year = "2021"),
                XtreamVodItem(2, name = "Duna Legendado", year = "2021")
            ),
            vodInfo = mapOf(
                2 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(2, "mp4"))
            ),
            vodDetailFailures = setOf(1)
        )

        val result = resolver(source).resolveMovie(438631, listOf("Duna"), 2021)

        assertTrue(result is XtreamResolution.Available)
        assertEquals(
            "http://example.com/movie/user/pass/2.mp4",
            (result as XtreamResolution.Available).source.streams.single().url
        )
    }

    @Test
    fun `movie detail requests are limited to four concurrent calls`() = runTest {
        val candidates = (1..6).map { id -> XtreamVodItem(id, name = "Duna $id 1080p", year = "2021") }
        val details = candidates.associate { item ->
            item.streamId to XtreamVodInfoResponse(
                XtreamVodInfo("438631"),
                XtreamMovieData(item.streamId, "mp4")
            )
        }
        val source = FakeSource(vod = candidates, vodInfo = details, vodDetailDelayMillis = 100L)

        val result = resolver(source).resolveMovie(438631, listOf("Duna 1", "Duna 2", "Duna 3", "Duna 4", "Duna 5", "Duna 6"), 2021)

        assertTrue(result is XtreamResolution.Available)
        assertEquals(4, source.maxConcurrentVodDetails)
    }

    @Test
    fun `episode resolves exact season and episode`() = runTest {
        val source = FakeSource(
            series = listOf(XtreamSeriesItem(20, name = "Ruptura Dublado", year = "2022")),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(
                    mapOf("1" to listOf(XtreamEpisode("55", 3, 1, "mkv", "S01E03 Dublado 1080p")))
                )
            )
        )

        val result = resolver(source).resolveEpisode(listOf("Ruptura", "Severance"), 2022, 1, 3)

        assertTrue(result is XtreamResolution.Available)
        assertEquals(
            "http://example.com/series/user/pass/55.mkv",
            (result as XtreamResolution.Available).source.streams.single().url
        )
    }

    @Test
    fun `missing episode is unavailable instead of selecting another episode`() = runTest {
        val source = FakeSource(
            series = listOf(XtreamSeriesItem(20, name = "Ruptura", year = "2022")),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("55", 2, 1, "mkv"))))
            )
        )

        assertEquals(
            XtreamResolution.Unavailable,
            resolver(source).resolveEpisode(listOf("Ruptura"), 2022, 1, 3)
        )
    }

    @Test
    fun `series availability loads all episodes and reuses details for playback`() = runTest {
        val source = FakeSource(
            series = listOf(XtreamSeriesItem(20, name = "Ruptura", year = "2022")),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(
                    mapOf(
                        "1" to listOf(
                            XtreamEpisode("51", 1, 1, "mkv"),
                            XtreamEpisode("52", 2, 1, "mkv")
                        ),
                        "2" to listOf(XtreamEpisode("61", 1, 2, "mkv"))
                    )
                )
            )
        )
        val resolver = resolver(source)

        val availability = resolver.resolveSeriesAvailability(listOf("Ruptura"), 2022)
        val playback = resolver.resolveEpisode(listOf("Ruptura"), 2022, 2, 1)

        assertEquals(
            setOf(1 to 1, 1 to 2, 2 to 1),
            (availability as XtreamSeriesAvailability.Resolved).episodes
        )
        assertTrue(playback is XtreamResolution.Available)
        assertEquals(listOf(20), source.requestedSeriesDetails)
    }

    @Test
    fun `series maps a provider season encoded as release year suffix to season one`() = runTest {
        val source = FakeSource(
            series = listOf(
                XtreamSeriesItem(
                    5141,
                    name = "Uma Questão de Química (2023)",
                    title = "Uma Questão de Química",
                    year = "2023"
                )
            ),
            seriesInfo = mapOf(
                5141 to XtreamSeriesInfoResponse(
                    mapOf(
                        "23" to listOf(
                            XtreamEpisode("276151", 1, 23, "mp4", "Uma Questão de Química - S23E01"),
                            XtreamEpisode("276152", 2, 23, "mp4", "Uma Questão de Química - S23E02")
                        )
                    )
                )
            )
        )
        val resolver = resolver(source)

        val availability = resolver.resolveSeriesAvailability(listOf("Uma Questão de Química", "Lessons in Chemistry"), 2023)
        val playback = resolver.resolveEpisode(
            listOf("Uma Questão de Química", "Lessons in Chemistry"),
            2023,
            1,
            2
        )

        assertEquals(
            setOf(1 to 1, 1 to 2),
            (availability as XtreamSeriesAvailability.Resolved).episodes
        )
        assertTrue(playback is XtreamResolution.Available)
        assertEquals(
            "http://example.com/series/user/pass/276152.mp4",
            (playback as XtreamResolution.Available).source.streams.single().url
        )
    }

    @Test
    fun `series keeps a sole season that does not match the release year suffix`() = runTest {
        val source = FakeSource(
            series = listOf(XtreamSeriesItem(30, name = "Serie Longa", year = "2023")),
            seriesInfo = mapOf(
                30 to XtreamSeriesInfoResponse(mapOf("2" to listOf(XtreamEpisode("88", 1, 2, "mp4"))))
            )
        )

        val availability = resolver(source).resolveSeriesAvailability(listOf("Serie Longa"), 2023)

        assertEquals(setOf(2 to 1), (availability as XtreamSeriesAvailability.Resolved).episodes)
    }

    @Test
    fun `provider exception is distinct from unavailable`() = runTest {
        val result = resolver(FakeSource(error = IllegalStateException("offline")))
            .resolveMovie(1, listOf("Filme"), 2020)

        assertTrue(result is XtreamResolution.Failure)
    }

    private fun resolver(source: XtreamDataSource) =
        XtreamPlaybackResolver(
            "http://example.com",
            "user",
            "pass",
            source,
            XtreamCatalogRepository(source, MemoryCatalogStorage())
        )

    private class MemoryCatalogStorage : XtreamCatalogStorage {
        private var snapshot: XtreamCatalogSnapshot? = null

        override suspend fun read(): XtreamCatalogSnapshot? = snapshot
        override suspend fun write(snapshot: XtreamCatalogSnapshot) {
            this.snapshot = snapshot
        }
        override suspend fun clear() {
            snapshot = null
        }
    }

    private class FakeSource(
        private val vod: List<XtreamVodItem> = emptyList(),
        private val vodInfo: Map<Int, XtreamVodInfoResponse> = emptyMap(),
        private val series: List<XtreamSeriesItem> = emptyList(),
        private val seriesInfo: Map<Int, XtreamSeriesInfoResponse> = emptyMap(),
        private val vodDetailFailures: Set<Int> = emptySet(),
        private val vodDetailDelayMillis: Long = 0L,
        private val error: Throwable? = null
    ) : XtreamDataSource {
        val requestedVodDetails = mutableListOf<Int>()
        var vodCatalogRequests = 0
        var activeVodDetails = 0
        var maxConcurrentVodDetails = 0
        val requestedSeriesDetails = mutableListOf<Int>()

        override suspend fun getVodStreams(): List<XtreamVodItem> {
            vodCatalogRequests += 1
            return error?.let { throw it } ?: vod
        }
        override suspend fun getVodInfo(id: Int): XtreamVodInfoResponse {
            requestedVodDetails += id
            activeVodDetails += 1
            maxConcurrentVodDetails = maxOf(maxConcurrentVodDetails, activeVodDetails)
            return try {
                if (vodDetailDelayMillis > 0) delay(vodDetailDelayMillis)
                error?.let { throw it }
                if (id in vodDetailFailures) error("detail unavailable")
                vodInfo.getValue(id)
            } finally {
                activeVodDetails -= 1
            }
        }
        override suspend fun getSeries(): List<XtreamSeriesItem> = error?.let { throw it } ?: series
        override suspend fun getSeriesInfo(id: Int): XtreamSeriesInfoResponse {
            requestedSeriesDetails += id
            return error?.let { throw it } ?: seriesInfo.getValue(id)
        }
    }
}
