package com.nuvio.tv.core.config

import com.squareup.moshi.Moshi

/**
 * Operator-controlled provider endpoint list for the authorized Xtream source.
 *
 * The document is downloaded from the public repository and only used after its detached
 * signature is verified, so a compromised host can never redirect credentials to a domain the
 * operator did not approve. `revision` is monotonic: a device ignores a document that is not
 * newer than the last one it applied, which protects the mirror (with a longer CDN cache)
 * from rolling an endpoint rotation back.
 */
data class RemoteConfig(
    val schema: Int,
    val revision: Int,
    val keyId: String,
    val issuedAt: String?,
    val endpoints: List<String>,
)

internal object RemoteConfigParser {
    const val SUPPORTED_SCHEMA = 1

    fun parse(moshi: Moshi, json: String): RemoteConfig? {
        val dto = runCatching { moshi.adapter(RemoteConfigDocument::class.java).fromJson(json) }
            .getOrNull()
            ?: return null
        if (dto.schema != SUPPORTED_SCHEMA) return null
        if (dto.revision < 1) return null
        if (dto.keyId.isBlank()) return null
        return RemoteConfig(
            schema = dto.schema,
            revision = dto.revision,
            keyId = dto.keyId,
            issuedAt = dto.issuedAt,
            endpoints = dto.endpoints.filter { it.isNotBlank() },
        )
    }
}

internal data class RemoteConfigDocument(
    val schema: Int = 0,
    val revision: Int = 0,
    val keyId: String = "",
    val issuedAt: String? = null,
    val endpoints: List<String> = emptyList(),
)
