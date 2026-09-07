package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamEpgResponse
import com.nuvio.tv.data.remote.api.XtreamCategory
import com.nuvio.tv.data.remote.api.XtreamLiveCategory
import com.nuvio.tv.data.remote.api.XtreamLiveStream
import com.nuvio.tv.data.remote.api.XtreamSeriesInfoResponse
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodInfoResponse
import com.nuvio.tv.data.remote.api.XtreamVodItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamCatalogRepositoryTest {
    @Test
    fun `fresh cache becomes ready without network`() = runTest {
        val storage = MemoryStorage(snapshot(fetchedAt = 1_000L))
        val source = ControlledSource()
        val repository = XtreamCatalogRepository(source, storage) { 2_000L }

        repository.initialize()

        assertTrue(repository.state.value is XtreamCatalogState.Ready)
        assertEquals(0, source.vodRequests)
        assertEquals(0, source.seriesRequests)
    }

    @Test
    fun `legacy snapshot is reused and stamped with the current source`() = runTest {
        val storage = MemoryStorage(snapshot(fetchedAt = 1_000L).copy(sourceFingerprint = ""))
        val source = ControlledSource()
        val repository = XtreamCatalogRepository(source, storage, { 2_000L }, "configured-source")

        repository.initialize()

        assertTrue(repository.state.value is XtreamCatalogState.Ready)
        assertEquals(0, source.vodRequests)
        assertEquals(1, storage.writeCount)
        assertEquals("configured-source", storage.current()?.sourceFingerprint)
    }

    @Test
    fun `first load requests vod and series in parallel`() = runTest {
        val release = CompletableDeferred<Unit>()
        val source = ControlledSource(release = release)
        val repository = XtreamCatalogRepository(source, MemoryStorage()) { 10_000L }

        val initialization = async { repository.initialize() }
        runCurrent()

        assertEquals(1, source.vodRequests)
        assertEquals(1, source.seriesRequests)
        assertEquals(2, source.activeCatalogRequests)
        assertTrue(repository.state.value is XtreamCatalogState.Loading)

        release.complete(Unit)
        initialization.await()
        assertTrue(repository.state.value is XtreamCatalogState.Ready)
    }

    @Test
    fun `stale cache is usable while refresh runs`() = runTest {
        val release = CompletableDeferred<Unit>()
        val storage = MemoryStorage(snapshot(fetchedAt = 0L))
        val repository = XtreamCatalogRepository(
            ControlledSource(release = release),
            storage
        ) { 7L * 60L * 60L * 1_000L }

        repository.initialize()

        val refreshing = repository.state.value as XtreamCatalogState.Ready
        assertTrue(refreshing.isRefreshing)
        assertEquals(listOf(1), repository.currentIndex().findVod(listOf("duna"), 2021).map { it.streamId })

        release.complete(Unit)
        repository.state.first { it is XtreamCatalogState.Ready && !it.isRefreshing }
        assertFalse((repository.state.value as XtreamCatalogState.Ready).isRefreshing)
    }

    @Test
    fun `failed cold load shows error and retry succeeds`() = runTest {
        val source = ControlledSource(fail = true)
        val repository = XtreamCatalogRepository(source, MemoryStorage()) { 1_000L }

        repository.initialize()
        assertTrue(repository.state.value is XtreamCatalogState.Error)

        source.fail = false
        repository.retry()
        assertTrue(repository.state.value is XtreamCatalogState.Ready)
    }

    @Test
    fun `concurrent initialization shares one catalog download`() = runTest {
        val release = CompletableDeferred<Unit>()
        val source = ControlledSource(release = release)
        val repository = XtreamCatalogRepository(source, MemoryStorage()) { 1_000L }

        val first = async { repository.initialize() }
        val second = async { repository.initialize() }
        runCurrent()
        release.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, source.vodRequests)
        assertEquals(1, source.seriesRequests)
    }

    @Test
    fun `incompatible snapshot is cleared and rebuilt`() = runTest {
        val storage = MemoryStorage(snapshot(0L).copy(schemaVersion = 99))
        val source = ControlledSource()
        val repository = XtreamCatalogRepository(source, storage) { 1_000L }

        repository.initialize()

        assertEquals(1, storage.clearCount)
        assertEquals(1, source.vodRequests)
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun `single title index preserves exact year and unique yearless fallback`() {
        val index = XtreamCatalogIndex.from(
            XtreamCatalogSnapshot(
                fetchedAtMillis = 1L,
                vod = listOf(
                    XtreamVodItem(1, name = "Duna", year = "1984"),
                    XtreamVodItem(2, name = "Duna 2021 4K", year = "2021"),
                    XtreamVodItem(3, name = "Duna"),
                ),
                series = listOf(
                    XtreamSeriesItem(10, name = "Uma Questão de Química", year = "2023"),
                ),
            ),
            generation = 1L,
        )

        assertEquals(listOf(2), index.findVod(listOf("duna"), 2021).map { it.streamId })
        assertEquals(listOf(3), index.findVod(listOf("duna"), 2024).map { it.streamId })
        assertEquals(
            listOf(10),
            index.findSeries(listOf(XtreamTitleMatcher.normalize("Uma Questao de Quimica")), 2023)
                .map { it.seriesId },
        )
    }

    @Test
    fun `vod release date separates remakes when year field is absent`() {
        val index = XtreamCatalogIndex.from(
            XtreamCatalogSnapshot(
                fetchedAtMillis = 1L,
                vod = listOf(
                    XtreamVodItem(1, name = "Barbie", releaseDate = "2023-07-19"),
                    XtreamVodItem(2, name = "A Odisséia", releaseDate = "2016-04-01"),
                ),
                series = emptyList(),
            ),
            generation = 1L,
        )

        assertEquals(listOf(1), index.findVod(listOf("barbie"), 2023).map { it.streamId })
        assertTrue(index.findVod(listOf("a odisseia"), 2026).isEmpty())
    }

    @Test
    fun `current index joins an initialization already in progress`() = runTest {
        val release = CompletableDeferred<Unit>()
        val source = ControlledSource(release = release)
        val repository = XtreamCatalogRepository(source, MemoryStorage()) { 1_000L }

        val initialization = async { repository.initialize() }
        val reader = async { repository.currentIndex() }
        runCurrent()
        release.complete(Unit)

        initialization.await()
        assertEquals(2, reader.await().itemCount)
        assertEquals(1, source.vodRequests)
        assertEquals(1, source.seriesRequests)
    }

    private fun snapshot(fetchedAt: Long) = XtreamCatalogSnapshot(
        fetchedAtMillis = fetchedAt,
        vod = listOf(XtreamVodItem(1, name = "Duna 2021 4K", year = "2021")),
        series = listOf(XtreamSeriesItem(2, name = "Ruptura", year = "2022"))
    )

    private class MemoryStorage(
        private var value: XtreamCatalogSnapshot? = null
    ) : XtreamCatalogStorage {
        var clearCount = 0
        var writeCount = 0

        override suspend fun read(): XtreamCatalogSnapshot? = value
        override suspend fun write(snapshot: XtreamCatalogSnapshot) {
            writeCount += 1
            value = snapshot
        }
        override suspend fun clear() {
            clearCount += 1
            value = null
        }

        fun current(): XtreamCatalogSnapshot? = value
    }

    private class ControlledSource(
        private val release: CompletableDeferred<Unit>? = null,
        var fail: Boolean = false
    ) : XtreamDataSource {
        var vodRequests = 0
        var seriesRequests = 0
        var activeCatalogRequests = 0

        override suspend fun getVodStreams(): List<XtreamVodItem> {
            vodRequests += 1
            activeCatalogRequests += 1
            release?.await()
            activeCatalogRequests -= 1
            if (fail) error("offline")
            return listOf(XtreamVodItem(10, name = "Duna", year = "2021"))
        }

        override suspend fun getSeries(): List<XtreamSeriesItem> {
            seriesRequests += 1
            activeCatalogRequests += 1
            release?.await()
            activeCatalogRequests -= 1
            if (fail) error("offline")
            return listOf(XtreamSeriesItem(20, name = "Ruptura", year = "2022"))
        }

        override suspend fun getVodInfo(id: Int): XtreamVodInfoResponse = error("not used")
        override suspend fun getSeriesInfo(id: Int): XtreamSeriesInfoResponse = error("not used")
        override suspend fun getVodCategories(): List<XtreamCategory> = emptyList()
        override suspend fun getSeriesCategories(): List<XtreamCategory> = emptyList()
        override suspend fun getLiveCategories(): List<XtreamLiveCategory> = error("not used")
        override suspend fun getLiveStreams(categoryId: Int?): List<XtreamLiveStream> = error("not used")
        override suspend fun getShortEpg(streamId: Int, limit: Int): XtreamEpgResponse = error("not used")
    }
}
