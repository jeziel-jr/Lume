package com.nuvio.tv.core.config

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.network.IPv4FirstDns
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A verified remote configuration together with the exact bytes that were signed. */
data class RemoteConfigEnvelope(
    val config: RemoteConfig,
    val payload: String,
    val signature: String,
)

/**
 * Downloads the signed provider configuration from the public repository.
 *
 * The raw GitHub URL is the primary location and jsDelivr mirrors it, so a single host being
 * unreachable does not stop an endpoint rotation. The client is deliberately separate from the
 * shared one: no disk cache (a cached document would defeat a rotation), no trust-all TLS, and
 * short timeouts so an unreachable host can never delay app startup.
 */
@Singleton
class RemoteConfigFetcher @Inject constructor(
    private val moshi: Moshi,
    private val verifier: RemoteConfigVerifier,
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(REQUEST_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun fetch(cacheBuster: String? = null): RemoteConfigEnvelope? = withContext(Dispatchers.IO) {
        for (location in locations(cacheBuster)) {
            val envelope = runCatching { download(location) }.getOrNull()
            if (envelope != null) return@withContext envelope
        }
        null
    }

    private fun download(location: Location): RemoteConfigEnvelope? {
        val payload = downloadText(location.payloadUrl) ?: return null
        val config = RemoteConfigParser.parse(moshi, payload) ?: return null
        val signature = downloadText(location.signatureUrl) ?: return null
        if (!verifier.verify(payload.toByteArray(Charsets.UTF_8), signature, config.keyId)) return null
        return RemoteConfigEnvelope(config = config, payload = payload, signature = signature.trim())
    }

    private fun downloadText(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun locations(cacheBuster: String?): List<Location> {
        val suffix = cacheBuster?.let { "?cb=$it" }.orEmpty()
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO
        return listOf(
            Location(
                payloadUrl = "$RAW_HOST/$owner/$repo/$BRANCH/$DOCUMENT_PATH$suffix",
                signatureUrl = "$RAW_HOST/$owner/$repo/$BRANCH/$DOCUMENT_PATH.sig$suffix",
            ),
            Location(
                payloadUrl = "$MIRROR_HOST/gh/$owner/$repo@$BRANCH/$DOCUMENT_PATH$suffix",
                signatureUrl = "$MIRROR_HOST/gh/$owner/$repo@$BRANCH/$DOCUMENT_PATH.sig$suffix",
            ),
        )
    }

    private data class Location(val payloadUrl: String, val signatureUrl: String)

    private companion object {
        const val RAW_HOST = "https://raw.githubusercontent.com"
        const val MIRROR_HOST = "https://cdn.jsdelivr.net"
        const val BRANCH = "main"
        const val DOCUMENT_PATH = "remote-config/xtream.json"
        const val REQUEST_TIMEOUT_SECONDS = 5L
    }
}
