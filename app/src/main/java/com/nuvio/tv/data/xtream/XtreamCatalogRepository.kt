package com.nuvio.tv.data.xtream

import android.content.Context
import android.os.SystemClock
import android.os.Process
import android.util.Log
import com.nuvio.tv.data.remote.api.XtreamCategory
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodItem
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher

private const val CATALOG_SCHEMA_VERSION = 3
private const val CATALOG_TTL_MILLIS = 6L * 60L * 60L * 1_000L

sealed interface XtreamCatalogState {
    data object Loading : XtreamCatalogState
    data class Ready(val isRefreshing: Boolean = false) : XtreamCatalogState
    data class Error(val message: String) : XtreamCatalogState
}

/** Observable stages of the first catalog preparation (cold start, no usable cache). */
enum class XtreamCatalogBootPhase {
    READING_CACHE,
    DOWNLOADING_VOD,
    DOWNLOADING_SERIES,
    DOWNLOADING_CATEGORIES,
    INDEXING,
}

data class XtreamCatalogBootProgress(
    val phase: XtreamCatalogBootPhase = XtreamCatalogBootPhase.READING_CACHE,
    val vodCount: Int? = null,
    val seriesCount: Int? = null,
)

@JsonClass(generateAdapter = true)
internal data class XtreamCatalogSnapshot(
    val schemaVersion: Int = CATALOG_SCHEMA_VERSION,
    val sourceFingerprint: String = "",
    val fetchedAtMillis: Long,
    val vod: List<XtreamVodItem>,
    val series: List<XtreamSeriesItem>,
    val vodCategories: List<XtreamCategory> = emptyList(),
    val seriesCategories: List<XtreamCategory> = emptyList()
)

internal interface XtreamCatalogStorage {
    suspend fun read(): XtreamCatalogSnapshot?
    suspend fun write(snapshot: XtreamCatalogSnapshot)
    suspend fun clear()
}

internal class FileXtreamCatalogStorage(
    private val file: File,
    moshi: Moshi
) : XtreamCatalogStorage {
    private val adapter = moshi.adapter(XtreamCatalogSnapshot::class.java)

    override suspend fun read(): XtreamCatalogSnapshot? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        runCatching {
            GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { reader ->
                adapter.fromJson(reader.readText())
            }
        }.getOrNull()
    }

    override suspend fun write(snapshot: XtreamCatalogSnapshot) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        val serializationStartedAt = SystemClock.elapsedRealtime()
        val json = adapter.toJson(snapshot)
        Log.i("LumeStartup", "xtream_snapshot_serialize_ms=${SystemClock.elapsedRealtime() - serializationStartedAt}")
        val fileWriteStartedAt = SystemClock.elapsedRealtime()
        GZIPOutputStream(temporary.outputStream().buffered()).bufferedWriter().use { writer ->
            writer.write(json)
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
        Log.i("LumeStartup", "xtream_snapshot_file_write_ms=${SystemClock.elapsedRealtime() - fileWriteStartedAt}")
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }
}

internal data class XtreamCatalogIndex(
    val generation: Long,
    val fetchedAtMillis: Long,
    val itemCount: Int,
    private val vodByTitle: Map<String, List<XtreamVodItem>>,
    private val seriesByTitle: Map<String, List<XtreamSeriesItem>>,
    private val vodCategoryNames: Map<String, String>,
    private val seriesCategoryNames: Map<String, String>,
    private val healthVodCandidates: List<XtreamVodItem>,
    private val healthSeriesCandidates: List<XtreamSeriesItem>,
) {
    fun findVod(normalizedTitles: Collection<String>, year: Int?): List<XtreamVodItem> =
        findCandidates(normalizedTitles, year, vodByTitle) { it.releaseYear }

    fun findSeries(normalizedTitles: Collection<String>, year: Int?): List<XtreamSeriesItem> =
        findCandidates(normalizedTitles, year, seriesByTitle) { it.releaseYear }

    fun vodCategoryNames(ids: List<Int>?): List<String> =
        ids.orEmpty().mapNotNull { vodCategoryNames[it.toString()] }

    fun seriesCategoryNames(ids: List<Int>?): List<String> =
        ids.orEmpty().mapNotNull { seriesCategoryNames[it.toString()] }

    fun healthProbeCandidates(): XtreamHealthProbeCandidates = XtreamHealthProbeCandidates(
        vod = healthVodCandidates,
        series = healthSeriesCandidates,
    )

    private fun <T> findCandidates(
        normalizedTitles: Collection<String>,
        year: Int?,
        byTitle: Map<String, List<T>>,
        releaseYear: (T) -> Int?,
    ): List<T> {
        val candidates = normalizedTitles.asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .flatMap { byTitle[it].orEmpty().asSequence() }
            .distinct()
            .toList()
        if (year == null) return candidates
        val exactYear = candidates.filter { releaseYear(it) == year }
        if (exactYear.isNotEmpty()) return exactYear
        return candidates.filter { releaseYear(it) == null }.takeIf { it.size == 1 }.orEmpty()
    }

    companion object {
        fun from(snapshot: XtreamCatalogSnapshot, generation: Long): XtreamCatalogIndex {
            val vodByTitle = HashMap<String, MutableList<XtreamVodItem>>(snapshot.vod.size)
            snapshot.vod.forEach { item ->
                val normalized = XtreamTitleMatcher.normalize(item.displayTitle)
                if (normalized.isNotBlank()) vodByTitle.getOrPut(normalized, ::mutableListOf).add(item)
            }
            val seriesByTitle = HashMap<String, MutableList<XtreamSeriesItem>>(snapshot.series.size)
            snapshot.series.forEach { item ->
                val normalized = XtreamTitleMatcher.normalize(item.displayTitle)
                if (normalized.isNotBlank()) seriesByTitle.getOrPut(normalized, ::mutableListOf).add(item)
            }
            return XtreamCatalogIndex(
                generation = generation,
                fetchedAtMillis = snapshot.fetchedAtMillis,
                itemCount = snapshot.vod.size + snapshot.series.size,
                vodByTitle = vodByTitle,
                seriesByTitle = seriesByTitle,
                vodCategoryNames = snapshot.vodCategories.associate { it.categoryId to it.categoryName },
                seriesCategoryNames = snapshot.seriesCategories.associate { it.categoryId to it.categoryName },
                healthVodCandidates = snapshot.vod.take(2),
                healthSeriesCandidates = snapshot.series.take(3),
            )
        }
    }
}

internal data class XtreamHealthProbeCandidates(
    val vod: List<XtreamVodItem>,
    val series: List<XtreamSeriesItem>,
)

@Singleton
class XtreamCatalogRepository internal constructor(
    private val dataSource: XtreamDataSource,
    private val storage: XtreamCatalogStorage,
    private val nowMillis: () -> Long,
    private val sourceFingerprintProvider: () -> String,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dataSource: XtreamDataSource,
        moshi: Moshi,
        credentialsStore: XtreamCredentialsStore,
    ) : this(
        dataSource = dataSource,
        storage = FileXtreamCatalogStorage(
            File(context.filesDir, "xtream/catalog-v$CATALOG_SCHEMA_VERSION.json.gz"),
            moshi
        ),
        nowMillis = System::currentTimeMillis,
        sourceFingerprintProvider = {
            credentialsStore.current()?.let { sourceFingerprint(it.baseUrl, it.username) }
                ?: error("Xtream is not configured")
        },
    )

    private val initializationMutex = Mutex()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<XtreamCatalogState>(XtreamCatalogState.Loading)
    val state: StateFlow<XtreamCatalogState> = _state.asStateFlow()
    private val _bootProgress = MutableStateFlow(XtreamCatalogBootProgress())
    val bootProgress: StateFlow<XtreamCatalogBootProgress> = _bootProgress.asStateFlow()

    @Volatile private var index: XtreamCatalogIndex? = null
    private var generationCounter = 0L
    private var refreshJob: Job? = null
    private var hasLoggedBootstrapStart = false
    @Volatile private var activeSourceFingerprint: String? = null

    internal constructor(
        dataSource: XtreamDataSource,
        storage: XtreamCatalogStorage
    ) : this(dataSource, storage, System::currentTimeMillis, { "test-source" })

    internal constructor(
        dataSource: XtreamDataSource,
        storage: XtreamCatalogStorage,
        nowMillis: () -> Long,
    ) : this(dataSource, storage, nowMillis, { "test-source" })

    internal constructor(
        dataSource: XtreamDataSource,
        storage: XtreamCatalogStorage,
        nowMillis: () -> Long,
        sourceFingerprint: String,
    ) : this(dataSource, storage, nowMillis, { sourceFingerprint })

    suspend fun initialize() = initializationMutex.withLock {
        val sourceFingerprint = sourceFingerprintProvider()
        if (activeSourceFingerprint != null && activeSourceFingerprint != sourceFingerprint) {
            resetForSourceChangeLocked(clearStorage = true)
        }
        activeSourceFingerprint = sourceFingerprint
        if (!hasLoggedBootstrapStart) {
            hasLoggedBootstrapStart = true
            Log.i("LumeStartup", "xtream_bootstrap_start elapsed_realtime_ms=${SystemClock.elapsedRealtime()}")
        }
        index?.let { current ->
            if (nowMillis() - current.fetchedAtMillis < CATALOG_TTL_MILLIS) return@withLock
            _state.value = XtreamCatalogState.Ready(isRefreshing = true)
            scheduleRefresh()
            return@withLock
        }

        val cacheReadStartedAt = SystemClock.elapsedRealtime()
        _bootProgress.value = XtreamCatalogBootProgress(phase = XtreamCatalogBootPhase.READING_CACHE)
        val storedSnapshot = storage.read()
        val cached = storedSnapshot?.takeIf {
            it.schemaVersion == CATALOG_SCHEMA_VERSION &&
                (sourceFingerprint == "test-source" ||
                    it.sourceFingerprint.isBlank() ||
                    it.sourceFingerprint == sourceFingerprint) &&
                isPlausible(it, previousSize = null)
        }?.let { snapshot ->
            if (sourceFingerprint != "test-source" && snapshot.sourceFingerprint.isBlank()) {
                snapshot.copy(sourceFingerprint = sourceFingerprint).also { storage.write(it) }
            } else {
                snapshot
            }
        }
        Log.i("LumeStartup", "xtream_cache_read_ms=${SystemClock.elapsedRealtime() - cacheReadStartedAt} hit=${cached != null}")
        if (cached != null) {
            install(cached)
            val stale = nowMillis() - cached.fetchedAtMillis >= CATALOG_TTL_MILLIS
            _state.value = XtreamCatalogState.Ready(isRefreshing = stale)
            if (!stale) return@withLock
            scheduleRefresh()
        } else {
            storage.clear()
            _state.value = XtreamCatalogState.Loading
            refresh(keepExistingOnFailure = false)
        }
    }

    suspend fun retry() = initializationMutex.withLock {
        val keepExisting = index != null
        _state.value = if (keepExisting) XtreamCatalogState.Ready(isRefreshing = true) else XtreamCatalogState.Loading
        refresh(keepExistingOnFailure = keepExisting)
    }

    suspend fun reconfigure(clearPersistentCache: Boolean) = initializationMutex.withLock {
        resetForSourceChangeLocked(clearStorage = clearPersistentCache)
        activeSourceFingerprint = sourceFingerprintProvider()
        _state.value = XtreamCatalogState.Loading
        refresh(keepExistingOnFailure = false)
    }

    suspend fun invalidateForCredentialsChange(clearPersistentCache: Boolean) = initializationMutex.withLock {
        resetForSourceChangeLocked(clearStorage = clearPersistentCache)
        activeSourceFingerprint = sourceFingerprintProvider()
        _state.value = XtreamCatalogState.Loading
    }

    internal suspend fun currentIndex(): XtreamCatalogIndex {
        index?.let { current ->
            if (nowMillis() - current.fetchedAtMillis >= CATALOG_TTL_MILLIS) {
                refreshScope.launch { initialize() }
            }
            return current
        }
        initialize()
        return index ?: error("Xtream catalog is unavailable")
    }

    internal fun currentIndexOrNull(): XtreamCatalogIndex? = index

    internal fun healthProbeCandidatesOrNull(): XtreamHealthProbeCandidates? =
        index?.healthProbeCandidates()

    private suspend fun refresh(keepExistingOnFailure: Boolean) {
        val sourceFingerprint = sourceFingerprintProvider()
        val refreshStartedAt = SystemClock.elapsedRealtime()
        _bootProgress.value = XtreamCatalogBootProgress(phase = XtreamCatalogBootPhase.DOWNLOADING_VOD)
        runCatching {
            coroutineScope {
                val vod = async { timedCatalogFetch("vod") { dataSource.getVodStreams() } }
                val series = async { timedCatalogFetch("series") { dataSource.getSeries() } }
                val vodCategories = async { timedCatalogFetch("vod_categories") { dataSource.getVodCategories() } }
                val seriesCategories = async { timedCatalogFetch("series_categories") { dataSource.getSeriesCategories() } }
                val vodItems = vod.await()
                _bootProgress.value = XtreamCatalogBootProgress(
                    phase = XtreamCatalogBootPhase.DOWNLOADING_SERIES,
                    vodCount = vodItems.size,
                )
                val seriesItems = series.await()
                _bootProgress.value = XtreamCatalogBootProgress(
                    phase = XtreamCatalogBootPhase.DOWNLOADING_CATEGORIES,
                    vodCount = vodItems.size,
                    seriesCount = seriesItems.size,
                )
                val vodCategoryItems = vodCategories.await()
                val seriesCategoryItems = seriesCategories.await()
                _bootProgress.value = XtreamCatalogBootProgress(
                    phase = XtreamCatalogBootPhase.INDEXING,
                    vodCount = vodItems.size,
                    seriesCount = seriesItems.size,
                )
                XtreamCatalogSnapshot(
                    sourceFingerprint = sourceFingerprint,
                    fetchedAtMillis = nowMillis(),
                    vod = vodItems,
                    series = seriesItems,
                    vodCategories = vodCategoryItems,
                    seriesCategories = seriesCategoryItems
                ).also { snapshot ->
                    check(isPlausible(snapshot, index?.itemCount)) { "Xtream returned an implausible catalog" }
                    val writeStartedAt = SystemClock.elapsedRealtime()
                    storage.write(snapshot)
                    Log.i("LumeStartup", "xtream_snapshot_write_ms=${SystemClock.elapsedRealtime() - writeStartedAt}")
                }
            }
        }.onSuccess { snapshot ->
            install(snapshot)
            _state.value = XtreamCatalogState.Ready()
            Log.i("LumeStartup", "xtream_ready elapsed_ms=${SystemClock.elapsedRealtime() - refreshStartedAt}")
            Log.i(
                "LumeStartup",
                "xtream_refresh_ms=${SystemClock.elapsedRealtime() - refreshStartedAt} items=${snapshot.vod.size + snapshot.series.size}",
            )
        }.onFailure {
            Log.w("LumeStartup", "xtream_refresh_failed_ms=${SystemClock.elapsedRealtime() - refreshStartedAt}")
            if (keepExistingOnFailure && index != null) {
                _state.value = XtreamCatalogState.Ready()
            } else {
                index = null
                _state.value = XtreamCatalogState.Error("Nao foi possivel preparar o catalogo.")
            }
        }
    }

    private suspend fun resetForSourceChangeLocked(clearStorage: Boolean) {
        refreshJob?.cancel()
        refreshJob = null
        index = null
        generationCounter += 1
        if (clearStorage) storage.clear()
    }

    private suspend fun install(snapshot: XtreamCatalogSnapshot) {
        val indexStartedAt = SystemClock.elapsedRealtime()
        _bootProgress.value = XtreamCatalogBootProgress(
            phase = XtreamCatalogBootPhase.INDEXING,
            vodCount = snapshot.vod.size,
            seriesCount = snapshot.series.size,
        )
        generationCounter += 1
        index = withContext(indexDispatcher) {
            XtreamCatalogIndex.from(snapshot, generationCounter)
        }
        Log.i(
            "LumeStartup",
            "xtream_index_ms=${SystemClock.elapsedRealtime() - indexStartedAt} items=${snapshot.vod.size + snapshot.series.size}",
        )
    }

    private fun scheduleRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = refreshScope.launch {
            initializationMutex.withLock {
                refresh(keepExistingOnFailure = true)
            }
        }
    }

    private fun isPlausible(snapshot: XtreamCatalogSnapshot, previousSize: Int?): Boolean {
        val size = snapshot.vod.size + snapshot.series.size
        if (size == 0) return false
        return previousSize == null || previousSize == 0 || size >= (previousSize / 10).coerceAtLeast(1)
    }

    private suspend fun <T> timedCatalogFetch(kind: String, block: suspend () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            Log.i("LumeStartup", "xtream_${kind}_fetch_ms=${SystemClock.elapsedRealtime() - startedAt}")
        }
    }

    companion object {
        private val indexDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(
                {
                    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                    runnable.run()
                },
                "Lume-Xtream-Index",
            ).apply { isDaemon = true }
        }.asCoroutineDispatcher()

        internal fun sourceFingerprint(baseUrl: String, username: String): String {
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest("${baseUrl.trim().trimEnd('/').lowercase()}\n$username".toByteArray())
            return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
