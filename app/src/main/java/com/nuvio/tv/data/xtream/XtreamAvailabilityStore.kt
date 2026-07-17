package com.nuvio.tv.data.xtream

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val AVAILABILITY_SCHEMA_VERSION = 1

@JsonClass(generateAdapter = true)
internal data class CachedMovieAvailability(
    val tmdbId: Int,
    val catalogFetchedAtMillis: Long,
    val available: Boolean,
)

@JsonClass(generateAdapter = true)
internal data class CachedSeriesAvailability(
    val tmdbId: Int,
    val catalogFetchedAtMillis: Long,
    val availableEpisodes: List<String>,
    val complete: Boolean,
    val available: Boolean,
)

@JsonClass(generateAdapter = true)
internal data class XtreamAvailabilitySnapshot(
    val schemaVersion: Int = AVAILABILITY_SCHEMA_VERSION,
    val sourceFingerprint: String,
    val movies: List<CachedMovieAvailability> = emptyList(),
    val series: List<CachedSeriesAvailability> = emptyList(),
)

internal data class CachedCatalogAvailability(
    val movies: Map<Int, Boolean>,
    val series: Map<Int, Boolean>,
)

@Singleton
class XtreamAvailabilityStore @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi,
    private val credentialsStore: XtreamCredentialsStore,
) {
    private val file = File(context.filesDir, "xtream/availability-v$AVAILABILITY_SCHEMA_VERSION.json.gz")
    private val adapter = moshi.adapter(XtreamAvailabilitySnapshot::class.java)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loaded = false
    private var movies = emptyMap<Int, CachedMovieAvailability>()
    private var series = emptyMap<Int, CachedSeriesAvailability>()
    private var writeJob: Job? = null
    private var loadedSourceFingerprint: String? = null

    internal suspend fun movie(tmdbId: Int, catalogFetchedAtMillis: Long): CachedMovieAvailability? =
        mutex.withLock {
            loadLocked()
            movies[tmdbId]?.takeIf { it.catalogFetchedAtMillis == catalogFetchedAtMillis }
        }

    internal suspend fun series(tmdbId: Int, catalogFetchedAtMillis: Long): CachedSeriesAvailability? =
        mutex.withLock {
            loadLocked()
            series[tmdbId]?.takeIf { it.catalogFetchedAtMillis == catalogFetchedAtMillis }
        }

    internal suspend fun catalogAvailability(
        movieIds: Set<Int>,
        seriesIds: Set<Int>,
        catalogFetchedAtMillis: Long,
    ): CachedCatalogAvailability = mutex.withLock {
        loadLocked()
        CachedCatalogAvailability(
            movies = movieIds.mapNotNull { tmdbId ->
                movies[tmdbId]
                    ?.takeIf { it.catalogFetchedAtMillis == catalogFetchedAtMillis }
                    ?.let { tmdbId to it.available }
            }.toMap(),
            series = seriesIds.mapNotNull { tmdbId ->
                series[tmdbId]
                    ?.takeIf { it.catalogFetchedAtMillis == catalogFetchedAtMillis }
                    ?.let { tmdbId to it.available }
            }.toMap(),
        )
    }

    internal suspend fun putMovie(entry: CachedMovieAvailability) = mutex.withLock {
        loadLocked()
        movies = movies + (entry.tmdbId to entry)
        scheduleWriteLocked()
    }

    internal suspend fun putSeries(entry: CachedSeriesAvailability) = mutex.withLock {
        loadLocked()
        series = series + (entry.tmdbId to entry)
        scheduleWriteLocked()
    }

    private suspend fun loadLocked() {
        val sourceFingerprint = currentSourceFingerprint()
        if (loaded && loadedSourceFingerprint == sourceFingerprint) return
        if (loadedSourceFingerprint != null && loadedSourceFingerprint != sourceFingerprint) {
            movies = emptyMap()
            series = emptyMap()
        }
        val snapshot = withContext(Dispatchers.IO) {
            if (!file.isFile) null else runCatching {
                GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { reader ->
                    adapter.fromJson(reader.readText())
                }
            }.getOrNull()
        }
        if (snapshot?.schemaVersion == AVAILABILITY_SCHEMA_VERSION &&
            snapshot.sourceFingerprint == sourceFingerprint
        ) {
            movies = snapshot.movies.associateBy { it.tmdbId }
            series = snapshot.series.associateBy { it.tmdbId }
        }
        loaded = true
        loadedSourceFingerprint = sourceFingerprint
    }

    private fun scheduleWriteLocked() {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(1_500L)
            val snapshot = mutex.withLock {
                XtreamAvailabilitySnapshot(
                    sourceFingerprint = currentSourceFingerprint(),
                    movies = movies.values.toList(),
                    series = series.values.toList(),
                )
            }
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            GZIPOutputStream(temporary.outputStream().buffered()).bufferedWriter().use { writer ->
                writer.write(adapter.toJson(snapshot))
            }
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        writeJob?.cancel()
        writeJob = null
        movies = emptyMap()
        series = emptyMap()
        loaded = true
        loadedSourceFingerprint = currentSourceFingerprint()
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun currentSourceFingerprint(): String {
        val credentials = credentialsStore.current() ?: return "unconfigured"
        return XtreamCatalogRepository.sourceFingerprint(credentials.baseUrl, credentials.username)
    }
}
