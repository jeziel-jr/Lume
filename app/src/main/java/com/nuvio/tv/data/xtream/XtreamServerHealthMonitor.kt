package com.nuvio.tv.data.xtream

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodItem
import com.nuvio.tv.ui.screens.player.PlayerMediaSourceFactory
import com.nuvio.tv.ui.screens.player.PlayerPlaybackNetworking
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

enum class XtreamHealthState {
    UNKNOWN,
    CHECKING,
    HEALTHY,
    DEGRADED,
    PROVIDER_FAILURE,
    UNAUTHORIZED,
    LOCAL_NETWORK_FAILURE,
    APP_FORMAT_FAILURE,
}

enum class XtreamHealthReason {
    NONE,
    CATALOG_NOT_READY,
    NO_SAMPLE,
    EMPTY_RESPONSE,
    ERROR_DOCUMENT,
    HTTP_UNAUTHORIZED,
    HTTP_FORBIDDEN,
    HTTP_RATE_LIMITED,
    HTTP_SERVER_ERROR,
    HTTP_OTHER,
    TIMEOUT,
    DNS_FAILURE,
    NETWORK_FAILURE,
    MEDIA_REJECTED_BY_PLAYER,
}

data class XtreamComponentHealth(
    val state: XtreamHealthState = XtreamHealthState.UNKNOWN,
    val reason: XtreamHealthReason = XtreamHealthReason.NONE,
    val successfulSamples: Int = 0,
    val attemptedSamples: Int = 0,
)

data class XtreamServerHealth(
    val account: XtreamComponentHealth = XtreamComponentHealth(),
    val movies: XtreamComponentHealth = XtreamComponentHealth(),
    val series: XtreamComponentHealth = XtreamComponentHealth(),
    val checkedAtMillis: Long? = null,
    val latencyMillis: Long? = null,
) {
    val playback: XtreamComponentHealth
        get() {
            val components = listOf(movies, series).filter { it.state != XtreamHealthState.UNKNOWN }
            if (components.isEmpty()) return XtreamComponentHealth()
            val states = components.map { it.state }
            val state = when {
                XtreamHealthState.CHECKING in states -> XtreamHealthState.CHECKING
                XtreamHealthState.LOCAL_NETWORK_FAILURE in states -> XtreamHealthState.LOCAL_NETWORK_FAILURE
                XtreamHealthState.PROVIDER_FAILURE in states && XtreamHealthState.HEALTHY !in states ->
                    XtreamHealthState.PROVIDER_FAILURE
                XtreamHealthState.APP_FORMAT_FAILURE in states -> XtreamHealthState.APP_FORMAT_FAILURE
                XtreamHealthState.DEGRADED in states ||
                    (XtreamHealthState.PROVIDER_FAILURE in states && XtreamHealthState.HEALTHY in states) ->
                    XtreamHealthState.DEGRADED
                states.all { it == XtreamHealthState.HEALTHY } -> XtreamHealthState.HEALTHY
                else -> XtreamHealthState.UNKNOWN
            }
            val reason = components.firstOrNull { it.state == state }?.reason
                ?: components.firstOrNull { it.reason != XtreamHealthReason.NONE }?.reason
                ?: XtreamHealthReason.NONE
            return XtreamComponentHealth(
                state = state,
                reason = reason,
                successfulSamples = components.sumOf { it.successfulSamples },
                attemptedSamples = components.sumOf { it.attemptedSamples },
            )
        }
}

internal object XtreamProbeClassifier {
    fun classify(code: Int, contentType: String?, bytes: ByteArray): XtreamComponentHealth = when (code) {
        401 -> failure(XtreamHealthReason.HTTP_UNAUTHORIZED)
        403 -> failure(XtreamHealthReason.HTTP_FORBIDDEN)
        429 -> failure(XtreamHealthReason.HTTP_RATE_LIMITED)
        in 500..599 -> failure(XtreamHealthReason.HTTP_SERVER_ERROR)
        !in 200..299 -> failure(XtreamHealthReason.HTTP_OTHER)
        else -> {
            val normalizedContentType = contentType.orEmpty().lowercase()
            when {
                bytes.isEmpty() -> failure(XtreamHealthReason.EMPTY_RESPONSE)
                isErrorDocument(normalizedContentType, bytes) -> failure(XtreamHealthReason.ERROR_DOCUMENT)
                isMediaResponse(normalizedContentType, bytes) -> XtreamComponentHealth(
                    XtreamHealthState.HEALTHY,
                    successfulSamples = 1,
                    attemptedSamples = 1,
                )
                else -> failure(XtreamHealthReason.ERROR_DOCUMENT)
            }
        }
    }

    private fun failure(reason: XtreamHealthReason) = XtreamComponentHealth(
        XtreamHealthState.PROVIDER_FAILURE,
        reason,
        attemptedSamples = 1,
    )

    private fun isErrorDocument(contentType: String, bytes: ByteArray): Boolean {
        if (contentType.contains("text/html") || contentType.contains("application/json")) return true
        val prefix = bytes.take(64).toByteArray().toString(Charsets.UTF_8).trimStart().lowercase()
        return prefix.startsWith("<html") || prefix.startsWith("<!doctype") ||
            prefix.startsWith("{") || prefix.startsWith("[")
    }

    private fun isMediaResponse(contentType: String, bytes: ByteArray): Boolean {
        if (contentType.startsWith("video/") || contentType.startsWith("audio/") ||
            contentType.contains("octet-stream") || contentType.contains("mpegurl") ||
            contentType.contains("dash+xml")
        ) return true
        if (bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())) return true
        if (bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        ) return true
        if (bytes.size >= 376 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte()) return true
        val prefix = bytes.take(12).toByteArray().toString(Charsets.US_ASCII)
        return prefix.startsWith("#EXTM3U") || prefix.startsWith("FLV") ||
            prefix.startsWith("OggS") || (prefix.startsWith("RIFF") && prefix.contains("AVI"))
    }
}

@Singleton
class XtreamServerHealthMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsStore: XtreamCredentialsStore,
    private val apiFactory: XtreamApiFactory,
    private val dataSource: XtreamDataSource,
    private val catalogRepository: XtreamCatalogRepository,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val checkMutex = Mutex()
    private val probeHttpClient by lazy {
        PlayerPlaybackNetworking.playbackHttpClient.newBuilder()
            .connectTimeout(PROBE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
    private val _health = MutableStateFlow(readStoredHealth())
    val health: StateFlow<XtreamServerHealth> = _health.asStateFlow()

    suspend fun refresh(accountAuthorized: Boolean = false): XtreamServerHealth = checkMutex.withLock {
        val credentials = credentialsStore.current() ?: return@withLock clear()
        val startedAt = SystemClock.elapsedRealtime()
        _health.update {
            it.copy(
                account = XtreamComponentHealth(XtreamHealthState.CHECKING),
                movies = XtreamComponentHealth(XtreamHealthState.CHECKING),
                series = XtreamComponentHealth(XtreamHealthState.CHECKING),
            )
        }

        val authorized = if (accountAuthorized) {
            true
        } else {
            runCatching {
                withTimeout(PROBE_API_TIMEOUT_MS) {
                    apiFactory.apiFor(credentials.baseUrl)
                        .authenticate(credentials.username, credentials.password)
                        .userInfo
                        ?.isAuthorized == true
                }
            }.getOrElse { error ->
                val failure = connectionFailure(error)
                return@withLock finish(
                    XtreamServerHealth(
                        account = failure,
                        movies = failure,
                        series = failure,
                    ),
                    startedAt,
                )
            }
        }
        if (!authorized) {
            return@withLock finish(
                XtreamServerHealth(
                    account = XtreamComponentHealth(
                        XtreamHealthState.UNAUTHORIZED,
                        XtreamHealthReason.HTTP_UNAUTHORIZED,
                    ),
                ),
                startedAt,
            )
        }

        val candidates = catalogRepository.healthProbeCandidatesOrNull()
        if (candidates == null) {
            val unavailable = XtreamComponentHealth(
                XtreamHealthState.UNKNOWN,
                XtreamHealthReason.CATALOG_NOT_READY,
            )
            return@withLock finish(
                XtreamServerHealth(
                    account = XtreamComponentHealth(XtreamHealthState.HEALTHY),
                    movies = unavailable,
                    series = unavailable,
                ),
                startedAt,
            )
        }

        val movies = checkType {
            checkCandidates(candidates.vod.map { movieUrl(credentials, it) })
        }
        val series = checkType {
            checkCandidates(seriesUrls(credentials, candidates.series))
        }
        finish(
            XtreamServerHealth(
                account = XtreamComponentHealth(XtreamHealthState.HEALTHY),
                movies = movies,
                series = series,
            ),
            startedAt,
        )
    }

    suspend fun diagnosePlaybackFailure(url: String): XtreamComponentHealth {
        if (!isXtreamStream(url)) return XtreamComponentHealth()
        val result = checkUrl(url)
        val diagnosis = if (result.state == XtreamHealthState.HEALTHY) {
            XtreamComponentHealth(
                state = XtreamHealthState.APP_FORMAT_FAILURE,
                reason = XtreamHealthReason.MEDIA_REJECTED_BY_PLAYER,
                attemptedSamples = 1,
                successfulSamples = 1,
            )
        } else {
            result
        }
        val isSeries = runCatching { url.toHttpUrl().pathSegments.contains("series") }.getOrDefault(false)
        val updated = _health.value.copy(
            movies = if (isSeries) _health.value.movies else diagnosis,
            series = if (isSeries) diagnosis else _health.value.series,
            checkedAtMillis = System.currentTimeMillis(),
        )
        persist(updated)
        _health.value = updated
        return diagnosis
    }

    fun isXtreamStream(url: String): Boolean {
        val credentials = credentialsStore.current() ?: return false
        return runCatching {
            val candidate = url.toHttpUrl()
            val configured = credentials.baseUrl.toHttpUrl()
            candidate.host.equals(configured.host, ignoreCase = true) &&
                candidate.pathSegments.any { it == "movie" || it == "series" }
        }.getOrDefault(false)
    }

    fun clear(): XtreamServerHealth {
        preferences.edit().clear().apply()
        return XtreamServerHealth().also { _health.value = it }
    }

    private suspend fun seriesUrls(
        credentials: XtreamCredentials,
        candidates: List<XtreamSeriesItem>,
    ): List<String> {
        val urls = mutableListOf<String>()
        for (candidate in candidates) {
            if (urls.size >= MAX_SAMPLES_PER_TYPE) break
            val detail = runCatching {
                withTimeout(PROBE_CALL_TIMEOUT_MS) {
                    dataSource.getSeriesInfo(candidate.seriesId)
                }
            }.getOrNull() ?: continue
            val episode = detail.episodes.values.asSequence().flatten().firstOrNull() ?: continue
            urls += buildUrl(
                credentials = credentials,
                kind = "series",
                id = episode.id,
                extension = episode.containerExtension ?: "mp4",
            )
        }
        return urls
    }

    private fun movieUrl(credentials: XtreamCredentials, item: XtreamVodItem): String = buildUrl(
        credentials = credentials,
        kind = "movie",
        id = item.streamId.toString(),
        extension = item.containerExtension ?: "mp4",
    )

    private fun buildUrl(
        credentials: XtreamCredentials,
        kind: String,
        id: String,
        extension: String,
    ): String = credentials.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
        .addPathSegment(kind)
        .addPathSegment(credentials.username)
        .addPathSegment(credentials.password)
        .addPathSegment("$id.${extension.trimStart('.')}")
        .build()
        .toString()

    private suspend fun checkCandidates(urls: List<String>): XtreamComponentHealth {
        if (urls.isEmpty()) {
            return XtreamComponentHealth(
                XtreamHealthState.UNKNOWN,
                XtreamHealthReason.NO_SAMPLE,
            )
        }
        var firstFailure: XtreamComponentHealth? = null
        var attempts = 0
        for (url in urls.take(MAX_SAMPLES_PER_TYPE)) {
            attempts++
            val result = checkUrl(url)
            if (result.state == XtreamHealthState.HEALTHY) {
                return if (firstFailure == null) {
                    result.copy(attemptedSamples = attempts, successfulSamples = 1)
                } else {
                    XtreamComponentHealth(
                        XtreamHealthState.DEGRADED,
                        firstFailure.reason,
                        successfulSamples = 1,
                        attemptedSamples = attempts,
                    )
                }
            }
            if (firstFailure == null) firstFailure = result
        }
        return (firstFailure ?: XtreamComponentHealth()).copy(attemptedSamples = attempts)
    }

    private suspend fun checkType(block: suspend () -> XtreamComponentHealth): XtreamComponentHealth =
        try {
            withTimeout(PROBE_TYPE_TIMEOUT_MS) { block() }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            XtreamComponentHealth(
                XtreamHealthState.PROVIDER_FAILURE,
                XtreamHealthReason.TIMEOUT,
            )
        }

    private suspend fun checkUrl(url: String): XtreamComponentHealth = try {
        withTimeout(PROBE_TOTAL_TIMEOUT_MS) {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${PROBE_MAX_BYTES - 1}")
                .header("User-Agent", PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
                .build()
            probeHttpClient.newCall(request).execute().use { response ->
                val bytes = if (response.code in 200..299) {
                    response.body.source().readByteArray(PROBE_MAX_BYTES)
                } else {
                    ByteArray(0)
                }
                XtreamProbeClassifier.classify(
                    code = response.code,
                    contentType = response.header("Content-Type"),
                    bytes = bytes,
                )
            }
        }
    } catch (error: Throwable) {
        connectionFailure(error)
    }

    private fun connectionFailure(error: Throwable): XtreamComponentHealth {
        if (!hasInternetConnection()) {
            return XtreamComponentHealth(
                XtreamHealthState.LOCAL_NETWORK_FAILURE,
                XtreamHealthReason.NETWORK_FAILURE,
            )
        }
        val reason = when (error) {
            is SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> XtreamHealthReason.TIMEOUT
            is UnknownHostException -> XtreamHealthReason.DNS_FAILURE
            is IOException -> XtreamHealthReason.NETWORK_FAILURE
            else -> XtreamHealthReason.NETWORK_FAILURE
        }
        return XtreamComponentHealth(XtreamHealthState.PROVIDER_FAILURE, reason)
    }

    private fun hasInternetConnection(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun finish(health: XtreamServerHealth, startedAt: Long): XtreamServerHealth {
        val completed = health.copy(
            checkedAtMillis = System.currentTimeMillis(),
            latencyMillis = SystemClock.elapsedRealtime() - startedAt,
        )
        persist(completed)
        _health.value = completed
        return completed
    }

    private fun persist(value: XtreamServerHealth) {
        val credentials = credentialsStore.current() ?: return
        val json = JSONObject()
            .put("fingerprint", XtreamCatalogRepository.sourceFingerprint(credentials.baseUrl, credentials.username))
            .put("accountState", value.account.state.name)
            .put("accountReason", value.account.reason.name)
            .put("movieState", value.movies.state.name)
            .put("movieReason", value.movies.reason.name)
            .put("seriesState", value.series.state.name)
            .put("seriesReason", value.series.reason.name)
            .put("checkedAt", value.checkedAtMillis)
            .put("latency", value.latencyMillis)
        preferences.edit().putString(HEALTH_KEY, json.toString()).apply()
    }

    private fun readStoredHealth(): XtreamServerHealth {
        val credentials = credentialsStore.current() ?: return XtreamServerHealth()
        val raw = preferences.getString(HEALTH_KEY, null) ?: return XtreamServerHealth()
        return runCatching {
            val json = JSONObject(raw)
            val expected = XtreamCatalogRepository.sourceFingerprint(credentials.baseUrl, credentials.username)
            if (json.optString("fingerprint") != expected) return@runCatching XtreamServerHealth()
            XtreamServerHealth(
                account = storedComponent(json, "account"),
                movies = storedComponent(json, "movie"),
                series = storedComponent(json, "series"),
                checkedAtMillis = json.optLong("checkedAt").takeIf { it > 0L },
                latencyMillis = json.optLong("latency").takeIf { it >= 0L },
            )
        }.getOrDefault(XtreamServerHealth())
    }

    private fun storedComponent(json: JSONObject, prefix: String): XtreamComponentHealth =
        XtreamComponentHealth(
            state = enumValueOf(json.optString("${prefix}State", XtreamHealthState.UNKNOWN.name)),
            reason = enumValueOf(json.optString("${prefix}Reason", XtreamHealthReason.NONE.name)),
        )

    private companion object {
        const val PREFERENCES_NAME = "xtream_server_health"
        const val HEALTH_KEY = "last_health"
        const val MAX_SAMPLES_PER_TYPE = 2
        const val PROBE_MAX_BYTES = 64L * 1024L
        const val PROBE_TOTAL_TIMEOUT_MS = 15_000L
        const val PROBE_CALL_TIMEOUT_MS = 3_500L
        const val PROBE_TYPE_TIMEOUT_MS = 7_500L
        const val PROBE_API_TIMEOUT_MS = 5_000L
    }
}
