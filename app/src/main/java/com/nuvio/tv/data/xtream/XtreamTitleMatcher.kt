package com.nuvio.tv.data.xtream

import java.text.Normalizer
import java.util.Locale

object XtreamTitleMatcher {
    private val yearPattern = Regex("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")
    private val bracketedTagPattern = Regex("[\\[({][^\\]})]{1,24}[\\]})]")
    private val mediaTagPattern = Regex(
        "\\b(4k|uhd|fhd|full\\s*hd|1080p?|720p?|2160p?|480p?|hdr10?|dolby\\s*vision|" +
            "dublado|dual\\s*audio|dual|legendado|nacional|webrip|web-dl|bluray|brrip|x26[45]|hevc)\\b"
    )
    private val punctuationPattern = Regex("[^a-z0-9]+")
    private val combiningMarkPattern = Regex("\\p{M}+")
    private val whitespacePattern = Regex("\\s+")
    private val quality4kPattern = Regex("\\b(4k|uhd|2160p?)\\b", RegexOption.IGNORE_CASE)
    private val quality1080Pattern = Regex("\\b(fhd|full\\s*hd|1080p?)\\b", RegexOption.IGNORE_CASE)
    private val quality720Pattern = Regex("\\b(hd|720p?)\\b", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        val lowercase = raw.lowercase(Locale.ROOT)
        val withoutAccents = if (lowercase.all { it.code < 128 }) lowercase else {
            combiningMarkPattern.replace(Normalizer.normalize(lowercase, Normalizer.Form.NFD), "")
        }
        return withoutAccents
            .replace(bracketedTagPattern, " ")
            .replace(yearPattern, " ")
            .replace(mediaTagPattern, " ")
            .replace(punctuationPattern, " ")
            .trim()
            .replace(whitespacePattern, " ")
    }

    fun extractYear(raw: String): Int? = yearPattern.find(raw)?.value?.toIntOrNull()

    fun matches(candidateTitle: String, candidateYear: Int?, titles: Collection<String>, year: Int?): Boolean {
        val normalizedCandidate = normalize(candidateTitle)
        if (normalizedCandidate.isBlank() || titles.none { normalize(it) == normalizedCandidate }) return false
        return year == null || candidateYear == year
    }

    fun qualityValue(raw: String): Int = when {
        quality4kPattern.containsMatchIn(raw) -> 2160
        quality1080Pattern.containsMatchIn(raw) -> 1080
        quality720Pattern.containsMatchIn(raw) -> 720
        else -> -1
    }

    fun preferenceScore(raw: String): Int {
        val lower = raw.lowercase()
        val language = when {
            "dublado" in lower || "dual audio" in lower || "nacional" in lower -> 10_000
            "legendado" in lower -> 0
            else -> 1_000
        }
        return language + qualityValue(raw).coerceAtLeast(0)
    }
}
