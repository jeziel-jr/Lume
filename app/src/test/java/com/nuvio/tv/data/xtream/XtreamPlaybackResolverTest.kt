package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamCategory
import com.nuvio.tv.data.remote.api.XtreamEpisode
import com.nuvio.tv.data.remote.api.XtreamMovieData
import com.nuvio.tv.data.remote.api.XtreamSeriesInfoResponse
import com.nuvio.tv.data.remote.api.XtreamEpgResponse
import com.nuvio.tv.data.remote.api.XtreamLiveCategory
import com.nuvio.tv.data.remote.api.XtreamLiveStream
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodInfo
import com.nuvio.tv.data.remote.api.XtreamVodInfoResponse
import com.nuvio.tv.data.remote.api.XtreamVodItem
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `audio binge group maps legendado markers to leg and plain titles to dub`() {
        assertEquals(
            XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG,
            XtreamTitleMatcher.audioBingeGroup("Minha Serie [L] (2000)")
        )
        assertEquals(
            XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG,
            XtreamTitleMatcher.audioBingeGroup("Repay It in Blood LEGENDADO (2026)")
        )
        assertEquals(
            XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB,
            XtreamTitleMatcher.audioBingeGroup("Minha Serie (2000)")
        )
        assertEquals(
            XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB,
            XtreamTitleMatcher.audioBingeGroup("Duna (2021) [4K Dublado]")
        )
    }

    @Test
    fun `title L marker is stripped and rendered as canonical Legendado suffix`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "O Último Nascer do Sol [L] (2026)", year = "2026")
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(1, "mp4"))
            )
        )

        val result = resolver(source).resolveMovie(438631, listOf("O Último Nascer do Sol"), 2026)
            as XtreamResolution.Available

        val stream = result.source.streams.single()
        assertEquals("O Último Nascer do Sol (2026) • Legendado", stream.title)
        assertFalse(stream.title.orEmpty().contains("[L]", ignoreCase = true))
        assertEquals(XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG, stream.behaviorHints?.bingeGroup)
    }

    @Test
    fun `plain Legendado word is normalized to canonical suffix`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "Repay It in Blood LEGENDADO (2026)", year = "2026")
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(1, "mp4"))
            )
        )

        val result = resolver(source).resolveMovie(438631, listOf("Repay It in Blood"), 2026)
            as XtreamResolution.Available

        val stream = result.source.streams.single()
        assertEquals("Repay It in Blood (2026) • Legendado", stream.title)
        assertFalse(stream.title.orEmpty().contains("LEGENDADO"))
        assertEquals(XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG, stream.behaviorHints?.bingeGroup)
    }

    @Test
    fun `duplicate series entries carry distinct audio variant binge groups`() = runTest {
        val source = FakeSource(
            series = listOf(
                XtreamSeriesItem(20, name = "Ruptura (2022)"),
                XtreamSeriesItem(21, name = "Ruptura [L] (2022)")
            ),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("55", 1, 1, "mkv")))),
                21 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("56", 1, 1, "mkv"))))
            )
        )

        val result = resolver(source).resolveEpisode(listOf("Ruptura"), 2022, 1, 1)
            as XtreamResolution.Available

        val groups = result.source.streams.mapNotNull { it.behaviorHints?.bingeGroup }.sorted()
        assertEquals(
            listOf(XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB, XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG),
            groups
        )
        assertEquals(2, result.source.streams.size)
    }

    @Test
    fun `duplicate movie entries carry distinct audio variant binge groups`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "Duna (2021)"),
                XtreamVodItem(2, name = "Duna [L] (2021)")
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(1, "mp4")),
                2 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(2, "mp4"))
            )
        )

        val result = resolver(source).resolveMovie(438631, listOf("Duna"), 2021)
            as XtreamResolution.Available

        val groups = result.source.streams.mapNotNull { it.behaviorHints?.bingeGroup }.sorted()
        assertEquals(
            listOf(XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB, XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG),
            groups
        )
    }

    @Test
    fun `legendado category entry and untagged genre copy are labelled and grouped`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "Duna (2021)", year = "2021", categoryIds = listOf(133)),
                XtreamVodItem(2, name = "Duna (2021)", year = "2021", categoryIds = listOf(135))
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(1, "mp4")),
                2 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(2, "mp4"))
            ),
            vodCategories = listOf(
                XtreamCategory("133", "Filmes • Animação"),
                XtreamCategory("135", "Filmes • Legendado")
            )
        )

        val result = resolver(source).resolveMovie(438631, listOf("Duna"), 2021)
            as XtreamResolution.Available

        val titles = result.source.streams.map { it.title.orEmpty() }.sorted()
        assertEquals(listOf("Duna (2021) • Dublado", "Duna (2021) • Legendado"), titles)
        val groups = result.source.streams.mapNotNull { it.behaviorHints?.bingeGroup }.sorted()
        assertEquals(
            listOf(XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB, XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG),
            groups
        )
    }

    @Test
    fun `4k category entry gets a 4K tag and quality`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "Duna (2021)", year = "2021", categoryIds = listOf(136)),
                XtreamVodItem(2, name = "Duna (2021)", year = "2021", categoryIds = listOf(135))
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(1, "mp4")),
                2 to XtreamVodInfoResponse(XtreamVodInfo("438631"), XtreamMovieData(2, "mp4"))
            ),
            vodCategories = listOf(
                XtreamCategory("136", "Filmes • 4K"),
                XtreamCategory("135", "Filmes • Legendado")
            )
        )

        val result = resolver(source).resolveMovie(438631, listOf("Duna"), 2021)
            as XtreamResolution.Available

        val fourK = result.source.streams.first { it.title.orEmpty().contains("4K") }
        assertEquals("Duna (2021) • 4K", fourK.title)
        assertEquals("2160p", fourK.quality)
    }

    @Test
    fun `series legendado category tags the pair without name markers`() = runTest {
        val source = FakeSource(
            series = listOf(
                XtreamSeriesItem(20, name = "Serie X (2022)", year = "2022", categoryIds = listOf(9)),
                XtreamSeriesItem(21, name = "Serie X (2022)", year = "2022", categoryIds = listOf(160))
            ),
            seriesInfo = mapOf(
                20 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("55", 1, 1, "mkv")))),
                21 to XtreamSeriesInfoResponse(mapOf("1" to listOf(XtreamEpisode("56", 1, 1, "mkv"))))
            ),
            seriesCategories = listOf(
                XtreamCategory("9", "Séries"),
                XtreamCategory("160", "Series - Legendado")
            )
        )

        val result = resolver(source).resolveEpisode(listOf("Serie X"), 2022, 1, 1)
            as XtreamResolution.Available

        val titles = result.source.streams.map { it.title.orEmpty() }.sorted()
        assertEquals(listOf("Serie X (2022) • Dublado", "Serie X (2022) • Legendado"), titles)
    }

    @Test
    fun `dublado word and bracket markers are canonicalized while mixed tags stay`() = runTest {
        val source = FakeSource(
            vod = listOf(
                XtreamVodItem(1, name = "A Grande Jornada Dublado 1080p (2024)", year = "2024"),
                XtreamVodItem(2, name = "A Grande Jornada [Dublado] (2024)", year = "2024"),
                XtreamVodItem(3, name = "Rambo [4K Dublado] (2024)", year = "2024")
            ),
            vodInfo = mapOf(
                1 to XtreamVodInfoResponse(XtreamVodInfo("111"), XtreamMovieData(1, "mp4")),
                2 to XtreamVodInfoResponse(XtreamVodInfo("111"), XtreamMovieData(2, "mp4")),
                3 to XtreamVodInfoResponse(XtreamVodInfo("222"), XtreamMovieData(3, "mp4"))
            )
        )

        val resolver = resolver(source)
        val jornada = resolver.resolveMovie(111, listOf("A Grande Jornada"), 2024)
            as XtreamResolution.Available
        val titles = jornada.source.streams.map { it.title.orEmpty() }.sorted()
        assertEquals(
            listOf("A Grande Jornada (2024) • Dublado", "A Grande Jornada 1080p (2024) • Dublado"),
            titles
        )
        assertTrue(jornada.source.streams.all { it.behaviorHints?.bingeGroup == XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB })

        val rambo = resolver.resolveMovie(222, listOf("Rambo"), 2024) as XtreamResolution.Available
        assertEquals("Rambo [4K Dublado] (2024)", rambo.source.streams.single().title)
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
        private val vodCategories: List<XtreamCategory> = emptyList(),
        private val seriesCategories: List<XtreamCategory> = emptyList(),
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
        override suspend fun getVodCategories(): List<XtreamCategory> = error?.let { throw it } ?: vodCategories
        override suspend fun getSeriesCategories(): List<XtreamCategory> = error?.let { throw it } ?: seriesCategories
        override suspend fun getLiveCategories(): List<XtreamLiveCategory> = error("not used")
        override suspend fun getLiveStreams(categoryId: Int?): List<XtreamLiveStream> = error("not used")
        override suspend fun getShortEpg(streamId: Int, limit: Int): XtreamEpgResponse = error("not used")
    }
}
