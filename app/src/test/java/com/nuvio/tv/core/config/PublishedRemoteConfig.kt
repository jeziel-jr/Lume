package com.nuvio.tv.core.config

import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Reads the configuration published in the repository (`remote-config/xtream.json` plus its
 * detached signature) so tests can assert that what the operator commits is exactly what installed
 * apps accept. Private key material is never needed: verification only uses the public half.
 */
internal object PublishedRemoteConfig {
    data class Document(val payloadBytes: ByteArray, val payload: String, val signature: String)

    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun read(): Document {
        val directory = locate()
        val payloadBytes = File(directory, "xtream.json").readBytes()
        val signature = File(directory, "xtream.json.sig").readText().trim()
        return Document(payloadBytes, payloadBytes.toString(Charsets.UTF_8), signature)
    }

    private fun locate(): File {
        var candidate: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (candidate != null) {
            File(candidate, "remote-config/xtream.json").takeIf(File::isFile)?.let {
                return checkNotNull(it.parentFile)
            }
            candidate = candidate.parentFile
        }
        error("remote-config/xtream.json not found from ${System.getProperty("user.dir")}")
    }
}
