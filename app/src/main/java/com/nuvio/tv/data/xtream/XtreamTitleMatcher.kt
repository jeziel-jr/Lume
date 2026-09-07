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

    const val AUDIO_BINGE_GROUP_DUB = "audio:dub"
    const val AUDIO_BINGE_GROUP_LEG = "audio:leg"

    /**
     * Maps a provider title to the audio-variant binge group used so
     * next-episode auto-play keeps the variant the user last watched.
     * Catalog convention: dublado entries carry no marker (or explicit
     * "Dublado"/"dual"/"nacional" tags), legendado entries carry
     * "Legendado" or the "[L]" abbreviation.
     */
    fun audioBingeGroup(raw: String): String {
        val lower = raw.lowercase()
        val isLegendado = "legendado" in lower || "[l]" in lower || "(l)" in lower
        return if (isLegendado) AUDIO_BINGE_GROUP_LEG else AUDIO_BINGE_GROUP_DUB
    }

    /** Provider-defined stream tags derived from the section categories the
     *  entry lives in (e.g. "Filmes • 4K", "Filmes • Legendado"). */
    enum class XtreamStreamTag(val label: String) {
        LEGENDADO("Legendado"),
        DUBLADO("Dublado"),
        ULTRA_4K("4K"),
        CINEMA("CINEMA"),
    }

    fun tagFromCategoryName(name: String?): XtreamStreamTag? {
        if (name.isNullOrBlank()) return null
        val lower = name.lowercase()
        return when {
            "legendado" in lower -> XtreamStreamTag.LEGENDADO
            "dublado" in lower -> XtreamStreamTag.DUBLADO
            "4k" in lower || "2160" in lower || "uhd" in lower -> XtreamStreamTag.ULTRA_4K
            "cinema" in lower -> XtreamStreamTag.CINEMA
            else -> null
        }
    }

    /** True when the row text already communicates the tag (name markers
     *  such as "[L]", quality words, ...), so no suffix needs to be added. */
    fun titleShowsTag(title: String, tag: XtreamStreamTag): Boolean {
        val lower = title.lowercase()
        return when (tag) {
            XtreamStreamTag.LEGENDADO -> "legendado" in lower || "[l]" in lower || "(l)" in lower
            XtreamStreamTag.DUBLADO -> "dublado" in lower || "dual" in lower || "nacional" in lower
            XtreamStreamTag.ULTRA_4K -> "4k" in lower || "uhd" in lower || "2160" in lower
            XtreamStreamTag.CINEMA -> "cinema" in lower
        }
    }
}
