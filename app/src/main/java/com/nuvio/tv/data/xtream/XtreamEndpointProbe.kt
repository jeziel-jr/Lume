package com.nuvio.tv.data.xtream

import com.nuvio.tv.ui.screens.player.PlayerPlaybackNetworking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Probes a candidate provider endpoint before the app starts using it.
 *
 * Without stored credentials the probe is a liveness check on an operator-approved host: any answer
 * below 500 proves DNS, TCP and HTTP work, including the 404 that XUI panels return for
 * `player_api.php` without parameters. With credentials the probe authenticates, so an endpoint
 * that is reachable but no longer accepts the account never replaces a working one.
 */
@Singleton
class XtreamEndpointProbe @Inject constructor(
    private val apiFactory: XtreamApiFactory,
) {
    private val client = PlayerPlaybackNetworking.playbackHttpClient.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun isReachable(endpoint: String): Boolean = withContext(Dispatchers.IO) {
        val base = normalizeXtreamBaseUrl(endpoint)
        if (base.isBlank()) return@withContext false
        val url = runCatching {
            base.toHttpUrl().newBuilder().addPathSegment("player_api.php").build()
        }.getOrNull() ?: return@withContext false
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                response.code in 200..499
            }
        }.getOrDefault(false)
    }

    suspend fun isAuthorized(credentials: XtreamCredentials): Boolean = runCatching {
        withTimeout(PROBE_TIMEOUT_MILLIS * 2) {
            apiFactory.apiFor(credentials.baseUrl)
                .authenticate(credentials.username, credentials.password)
                .userInfo
                ?.isAuthorized == true
        }
    }.getOrDefault(false)

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 3_000L
    }
}
