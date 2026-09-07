package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamEpgEntry
import com.nuvio.tv.data.remote.api.XtreamEpgResponse
import com.nuvio.tv.data.remote.api.XtreamCategory
import com.nuvio.tv.data.remote.api.XtreamLiveCategory
import com.nuvio.tv.data.remote.api.XtreamLiveStream
import com.nuvio.tv.data.remote.api.XtreamSeriesInfoResponse
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodInfoResponse
import com.nuvio.tv.data.remote.api.XtreamVodItem
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamLiveRepositoryTest {

    private class FakeLiveSource : XtreamDataSource {
        var liveCategoriesCalls = 0
        var liveStreamsCalls = 0
        var shortEpgCalls = 0
        var fail = false

        override suspend fun getVodCategories(): List<XtreamCategory> = emptyList()
        override suspend fun getSeriesCategories(): List<XtreamCategory> = emptyList()

        override suspend fun getLiveCategories(): List<XtreamLiveCategory> {
            liveCategoriesCalls += 1
            if (fail) error("offline")
            return listOf(
                XtreamLiveCategory(categoryId = 42, categoryName = "Canais 4K"),
                XtreamLiveCategory(categoryId = 46, categoryName = "❌Adultos❌"),
                XtreamLiveCategory(categoryId = 125, categoryName = "❌Adultos OnlyFans(+18)❌"),
                XtreamLiveCategory(categoryId = 7, categoryName = "Notícias", parentId = 1),
            )
        }

        override suspend fun getLiveStreams(categoryId: Int?): List<XtreamLiveStream> {
            liveStreamsCalls += 1
            if (fail) error("offline")
            return listOf(
                XtreamLiveStream(
                    num = 1,
                    name = "Globo HD",
                    streamId = 1234,
                    streamIcon = "",
                    epgChannelId = "globo.br",
                    categoryId = 7
                ),
                XtreamLiveStream(
                    num = 2,
                    name = "Record HD",
                    streamId = 1235,
                    streamIcon = "http://icon/record.png",
                    epgChannelId = "",
                    categoryId = 0
                ),
            )
        }

        override suspend fun getShortEpg(streamId: Int, limit: Int): XtreamEpgResponse {
            shortEpgCalls += 1
            if (fail) error("offline")
            if (streamId == 9999) return XtreamEpgResponse(error = "channel id not found")
            return XtreamEpgResponse(
                epgListings = listOf(
                    XtreamEpgEntry(
                        id = "1",
                        title = b64("Jornal da Globo"),
                        description = b64("Principais notícias do dia"),
                        startTimestamp = 1756407600L,
                        stopTimestamp = 1756411200L
                    ),
                    XtreamEpgEntry(
                        id = "2",
                        title = "Novela das Nove",
                        startTimestamp = 1756411200L,
                        stopTimestamp = 1756414800L
                    ),
                )
            )
        }

        override suspend fun getVodStreams(): List<XtreamVodItem> = error("not used")
        override suspend fun getVodInfo(id: Int): XtreamVodInfoResponse = error("not used")
        override suspend fun getSeries(): List<XtreamSeriesItem> = error("not used")
        override suspend fun getSeriesInfo(id: Int): XtreamSeriesInfoResponse = error("not used")

        private fun b64(value: String): String =
            Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun repository(
        source: FakeLiveSource,
        credentials: XtreamCredentials?,
        now: () -> Long
    ) = XtreamLiveRepository(
        dataSource = source,
        credentialsProvider = { credentials },
        nowMillis = now,
        base64Decoder = jvmDecoder
    )

    private val jvmDecoder: (String) -> String? = { value ->
        runCatching { String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrNull()
    }

    @Test
    fun `categories are sorted alphabetically with adult detection`() = runTest {
        var now = 0L
        val repository = repository(FakeLiveSource(), XtreamCredentials("https://capone.icu", "u", "p")) { now }

        val snapshot = repository.loadSnapshot()

        assertEquals(
            listOf("Canais 4K", "Notícias", "❌Adultos OnlyFans(+18)❌", "❌Adultos❌"),
            snapshot.categories.map { it.name }
        )
        assertTrue(snapshot.categories.first { it.name == "❌Adultos❌" }.isAdult)
        assertTrue(snapshot.categories.first { it.name == "❌Adultos OnlyFans(+18)❌" }.isAdult)
        assertTrue(!snapshot.categories.first { it.name == "Canais 4K" }.isAdult)
        assertTrue(!snapshot.categories.first { it.name == "Notícias" }.isAdult)
    }

    @Test
    fun `channels map blank icon to null and missing category to zero`() = runTest {
        var now = 0L
        val repository = repository(FakeLiveSource(), null) { now }

        val snapshot = repository.loadSnapshot()

        assertEquals(2, snapshot.channels.size)
        val glued = snapshot.channels.first { it.streamId == 1234 }
        assertNull(glued.iconUrl)
        assertEquals(7, glued.categoryId)
        assertEquals("globo.br", glued.epgChannelId)
        val record = snapshot.channels.first { it.streamId == 1235 }
        assertEquals("http://icon/record.png", record.iconUrl)
        assertEquals(0, record.categoryId)
        assertNull(record.epgChannelId)
    }

    @Test
    fun `second load within ttl does not touch the data source`() = runTest {
        var now = 0L
        val source = FakeLiveSource()
        val repository = repository(source, null) { now }

        repository.loadSnapshot()
        repository.loadSnapshot()

        assertEquals(1, source.liveCategoriesCalls)
        assertEquals(1, source.liveStreamsCalls)
    }

    @Test
    fun `failed load without cache throws`() = runTest {
        val source = FakeLiveSource().apply { fail = true }
        val repository = repository(source, null) { 0L }

        var thrown = false
        try {
            repository.loadSnapshot()
        } catch (_: Exception) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `failed refresh with cache returns stale snapshot`() = runTest {
        var now = 0L
        val source = FakeLiveSource()
        val repository = repository(source, null) { now }

        repository.loadSnapshot()
        now = LIVE_CACHE_TTL_MILLIS + 1L
        source.fail = true
        val stale = repository.loadSnapshot()

        assertEquals("Canais 4K", stale.categories.first().name)
        assertEquals(2, stale.channels.size)
    }

    @Test
    fun `playback url follows live path with normalized base`() {
        val url = XtreamLiveRepository(
            dataSource = FakeLiveSource(),
            credentialsProvider = {
                XtreamCredentials("https://capone.icu/", "8221421", "5544324")
            },
            nowMillis = { 0L }
        ).playbackUrl(channel(1234))

        assertEquals("https://capone.icu/live/8221421/5544324/1234.m3u8", url)
    }

    @Test
    fun `playback url is null without credentials`() {
        val url = XtreamLiveRepository(
            dataSource = FakeLiveSource(),
            credentialsProvider = { null },
            nowMillis = { 0L }
        ).playbackUrl(channel(1234))

        assertNull(url)
    }

    @Test
    fun `epg decodes base64 titles and descriptions`() = runTest {
        val repository = repository(FakeLiveSource(), null) { 0L }

        val programs = repository.epg(1234)

        assertEquals(2, programs.size)
        assertEquals("Jornal da Globo", programs[0].title)
        assertEquals("Principais notícias do dia", programs[0].description)
        assertEquals(1756407600L, programs[0].startEpoch)
        assertEquals(1756411200L, programs[0].endEpoch)
        assertEquals("Novela das Nove", programs[1].title)
    }

    @Test
    fun `epg error response yields empty list`() = runTest {
        val repository = repository(FakeLiveSource(), null) { 0L }

        assertTrue(repository.epg(9999).isEmpty())
    }

    private fun channel(streamId: Int) = com.nuvio.tv.domain.model.LiveChannel(
        streamId = streamId,
        name = "Canal $streamId",
        iconUrl = null,
        epgChannelId = null,
        categoryId = 0,
        number = null
    )
}
