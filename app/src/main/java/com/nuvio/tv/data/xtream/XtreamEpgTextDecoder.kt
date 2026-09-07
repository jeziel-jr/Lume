package com.nuvio.tv.data.xtream

import android.util.Base64
import java.nio.charset.StandardCharsets

private val BASE64_CANDIDATE_REGEX = Regex("^[A-Za-z0-9+/=]+$")

/**
 * Decodes short EPG text from Xtream. The API sometimes delivers titles and
 * descriptions as base64, but plain-text values are also valid. Detects base64
 * only when the candidate shape is plausible and the decoded bytes produce
 * printable UTF-8 text with no replacement characters; anything else is
 * returned unchanged.
 */
internal fun decodeEpgText(
    raw: String,
    decoder: (String) -> String? = ::decodeBase64Candidate
): String {
    val candidate = raw.trim()
    if (candidate.isEmpty()) return candidate
    if (candidate.length % 4 != 0) return raw
    if (!BASE64_CANDIDATE_REGEX.matches(candidate)) return raw

    val decoded = decoder(candidate) ?: return raw
    if ('\uFFFD' in decoded) return raw
    if (decoded.none { !it.isISOControl() }) return raw
    return decoded
}

internal fun decodeBase64Candidate(value: String): String? = runCatching {
    String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8)
}.getOrNull()
