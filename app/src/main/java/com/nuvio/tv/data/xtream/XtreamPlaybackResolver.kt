package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamCategory
import com.nuvio.tv.data.remote.api.XtreamEpisode
import com.nuvio.tv.data.remote.api.XtreamEpgResponse
import com.nuvio.tv.data.remote.api.XtreamLiveCategory
import com.nuvio.tv.data.remote.api.XtreamLiveStream
import com.nuvio.tv.data.remote.api.XtreamSeriesInfoResponse
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodInfoResponse
import com.nuvio.tv.data.remote.api.XtreamVodItem
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.data.xtream.XtreamTitleMatcher.XtreamStreamTag
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Bracketed/parenthesised tag segments (e.g. "[4K Dublado]"), kept verbatim
 *  unless they contain only a language marker. */
private val MARKER_SEGMENT = Regex("""\[[^\[\]]*\]|\([^()]*\)""")
private val PURE_MARKER_CONTENT = setOf(
    "l", "legendado", "dublado", "dual", "dual audio", "nacional"
)
private val LEGENDADO_WORD = Regex("""\blegendado\b""", RegexOption.IGNORE_CASE)
private val DUBLADO_WORD = Regex(
    """\bdublado\b|\bdual audio\b|\bdual\b|\bnacional\b""",
    RegexOption.IGNORE_CASE
)

/** Presence checks on the raw provider title, used to canonicalize the row
 *  into the matching "• Legendado"/"• Dublado" suffix. */
private val LEGENDADO_MARKER = Regex(
    """\[[Ll]\]|\([Ll]\)|\[legendado\]|\(legendado\)|\blegendado\b""",
    RegexOption.IGNORE_CASE
)
private val DUBLADO_MARKER = Regex(
    """\[dublado\]|\(dublado\)|\[dual audio\]|\(dual audio\)|\[dual\]|\(dual\)|""" +
        """\[nacional\]|\(nacional\)|\bdual audio\b|\bdublado\b|\bdual\b|\bnacional\b""",
    RegexOption.IGNORE_CASE
)

/** Removes marker text of one audio side from a row so the canonical
 *  "• Dublado"/"• Legendado" suffix can be appended instead. Mixed segments
 *  such as "[4K Dublado]" are kept intact. */
private fun stripAudioMarkers(title: String, markerWord: Regex): String {
    val out = StringBuilder(title.length)
    var last = 0
    for (match in MARKER_SEGMENT.findAll(title)) {
        out.append(markerWord.replace(title.substring(last, match.range.first), " "))
        val inner = match.value.drop(1).dropLast(1).trim().lowercase()
        if (inner in PURE_MARKER_CONTENT) {
            out.append(' ')
        } else {
            out.append(match.value)
        }
        last = match.range.last + 1
    }
    out.append(markerWord.replace(title.substring(last), " "))
    return out.toString().replace(Regex("\\s{2,}"), " ").trim()
}

interface XtreamDataSource {
    suspend fun getVodStreams(): List<XtreamVodItem>
    suspend fun getVodInfo(id: Int): XtreamVodInfoResponse
    suspend fun getSeries(): List<XtreamSeriesItem>
    suspend fun getSeriesInfo(id: Int): XtreamSeriesInfoResponse
    suspend fun getVodCategories(): List<XtreamCategory>
    suspend fun getSeriesCategories(): List<XtreamCategory>
    suspend fun getLiveCategories(): List<XtreamLiveCategory>
    suspend fun getLiveStreams(categoryId: Int? = null): List<XtreamLiveStream>
    suspend fun getShortEpg(streamId: Int, limit: Int): XtreamEpgResponse
}

sealed interface XtreamResolution {
    data class Available(val source: AddonStreams) : XtreamResolution
    data object Unavailable : XtreamResolution
    data class Failure(val message: String) : XtreamResolution
}

sealed interface XtreamSeriesAvailability {
    data class Resolved(
        val episodes: Set<Pair<Int, Int>>,
        val complete: Boolean
    ) : XtreamSeriesAvailability
    data object Unavailable : XtreamSeriesAvailability
    data class Failure(val message: String) : XtreamSeriesAvailability
}

class XtreamPlaybackResolver(
    private val credentialsProvider: () -> XtreamCredentials,
    private val dataSource: XtreamDataSource,
    private val catalogRepository: XtreamCatalogRepository,
    private val availabilityStore: XtreamAvailabilityStore? = null,
) {
    constructor(
        baseUrl: String,
        username: String,
        password: String,
        dataSource: XtreamDataSource,
        catalogRepository: XtreamCatalogRepository,
        availabilityStore: XtreamAvailabilityStore? = null,
    ) : this(
        credentialsProvider = { XtreamCredentials(baseUrl, username, password).normalized() },
        dataSource = dataSource,
        catalogRepository = catalogRepository,
        availabilityStore = availabilityStore,
    )

    private val detailSemaphore = Semaphore(4)
    private val vodDetails = ConcurrentHashMap<Int, XtreamVodInfoResponse>()
    private val seriesDetails = ConcurrentHashMap<Int, XtreamSeriesInfoResponse>()
    private val resolutions = ConcurrentHashMap<String, XtreamResolution>()
    @Volatile private var cacheGeneration: Long = Long.MIN_VALUE

    suspend fun resolveMovie(tmdbId: Int, titles: Collection<String>, year: Int?): XtreamResolution =
        runCatching {
            val index = catalogRepository.currentIndex()
            resetCachesIfNeeded(index.generation)
            val key = "movie:$tmdbId"
            resolutions[key] ?: resolveMovieCandidates(
                candidates = index.findVod(normalizeTitles(titles), year),
                tmdbId = tmdbId,
                index = index
            ).also { cacheResolution(key, it) }
        }.getOrElse { XtreamResolution.Failure("Nao foi possivel acessar a fonte de reproducao.") }

    suspend fun movieAvailability(
        tmdbId: Int,
        titles: Collection<String>,
        year: Int?,
    ): XtreamAvailability {
        val index = runCatching { catalogRepository.currentIndex() }.getOrNull()
            ?: return XtreamAvailability.ERROR
        availabilityStore?.movie(tmdbId, index.fetchedAtMillis)?.let { cached ->
            return if (cached.available) XtreamAvailability.AVAILABLE else XtreamAvailability.UNAVAILABLE
        }
        val availability = when (resolveMovie(tmdbId, titles, year)) {
            is XtreamResolution.Available -> XtreamAvailability.AVAILABLE
            XtreamResolution.Unavailable -> XtreamAvailability.UNAVAILABLE
            is XtreamResolution.Failure -> XtreamAvailability.ERROR
        }
        if (availability != XtreamAvailability.ERROR) {
            availabilityStore?.putMovie(
                CachedMovieAvailability(
                    tmdbId = tmdbId,
                    catalogFetchedAtMillis = index.fetchedAtMillis,
                    available = availability == XtreamAvailability.AVAILABLE,
                )
            )
        }
        return availability
    }

    suspend fun resolveEpisode(
        titles: Collection<String>,
        year: Int?,
        season: Int,
        episode: Int
    ): XtreamResolution = runCatching {
        val normalizedTitles = normalizeTitles(titles)
        val index = catalogRepository.currentIndex()
        resetCachesIfNeeded(index.generation)
        val key = "series:${normalizedTitles.sorted().joinToString("|")}:$year:$season:$episode"
        resolutions[key] ?: resolveSeriesCandidates(
            candidates = index.findSeries(normalizedTitles, year),
            season = season,
            episode = episode,
            index = index
        ).also { cacheResolution(key, it) }
    }.getOrElse { XtreamResolution.Failure("Nao foi possivel acessar a fonte de reproducao.") }

    suspend fun resolveSeriesAvailability(
        titles: Collection<String>,
        year: Int?
    ): XtreamSeriesAvailability = runCatching {
        val index = catalogRepository.currentIndex()
        resetCachesIfNeeded(index.generation)
        val candidates = index.findSeries(normalizeTitles(titles), year)
        if (candidates.isEmpty()) return@runCatching XtreamSeriesAvailability.Unavailable
        val attempts = supervisorScope {
            candidates.map { candidate ->
                async { runCatching { candidate to getSeriesDetail(candidate.seriesId) } }
            }.awaitAll()
        }
        val successful = attempts.mapNotNull { it.getOrNull() }
        if (successful.isEmpty()) {
            XtreamSeriesAvailability.Failure("Nao foi possivel acessar a fonte de reproducao.")
        } else {
            val episodes = successful.asSequence()
                .flatMap { (candidate, detail) ->
                    detail.normalizedEpisodes(candidate.releaseYear)
                        .map { (season, episode) -> season to episode }
                }
                .toSet()
            XtreamSeriesAvailability.Resolved(
                episodes = episodes,
                complete = attempts.all { it.isSuccess }
            )
        }
    }.getOrElse {
        XtreamSeriesAvailability.Failure("Nao foi possivel acessar a fonte de reproducao.")
    }

    suspend fun seriesAvailability(
        tmdbId: Int,
        titles: Collection<String>,
        year: Int?,
    ): XtreamSeriesAvailability {
        val index = runCatching { catalogRepository.currentIndex() }.getOrNull()
            ?: return XtreamSeriesAvailability.Failure("Nao foi possivel acessar a fonte de reproducao.")
        availabilityStore?.series(tmdbId, index.fetchedAtMillis)?.let { cached ->
            if (!cached.available) return XtreamSeriesAvailability.Unavailable
            return XtreamSeriesAvailability.Resolved(
                episodes = cached.availableEpisodes.mapNotNull(::parseEpisodeKey).toSet(),
                complete = cached.complete,
            )
        }
        val result = resolveSeriesAvailability(titles, year)
        when (result) {
            is XtreamSeriesAvailability.Resolved -> availabilityStore?.putSeries(
                CachedSeriesAvailability(
                    tmdbId = tmdbId,
                    catalogFetchedAtMillis = index.fetchedAtMillis,
                    availableEpisodes = result.episodes.map { (season, episode) -> "$season:$episode" },
                    complete = result.complete,
                    available = true,
                )
            )
            XtreamSeriesAvailability.Unavailable -> availabilityStore?.putSeries(
                CachedSeriesAvailability(
                    tmdbId = tmdbId,
                    catalogFetchedAtMillis = index.fetchedAtMillis,
                    availableEpisodes = emptyList(),
                    complete = true,
                    available = false,
                )
            )
            is XtreamSeriesAvailability.Failure -> Unit
        }
        return result
    }

    private fun parseEpisodeKey(value: String): Pair<Int, Int>? {
        val season = value.substringBefore(':').toIntOrNull() ?: return null
        val episode = value.substringAfter(':', missingDelimiterValue = "").toIntOrNull() ?: return null
        return season to episode
    }

    private suspend fun resolveMovieCandidates(
        candidates: List<XtreamVodItem>,
        tmdbId: Int,
        index: XtreamCatalogIndex
    ): XtreamResolution {
        if (candidates.isEmpty()) return XtreamResolution.Unavailable
        val siblingHasLegendado = candidates.any { candidate ->
            isLegendadoEntry(candidate.displayTitle, index.vodCategoryNames(candidate.categoryIds))
        }
        val attempts = supervisorScope {
            candidates.map { candidate ->
                async {
                    runCatching {
                        val detail = vodDetails[candidate.streamId] ?: detailSemaphore.withPermit {
                            vodDetails[candidate.streamId] ?: dataSource.getVodInfo(candidate.streamId).also {
                                vodDetails[candidate.streamId] = it
                            }
                        }
                        val providerTmdbId = detail.info?.tmdbId?.trim()?.toIntOrNull()
                        if (providerTmdbId != null && providerTmdbId > 0 && providerTmdbId != tmdbId) {
                            return@runCatching null
                        }
                        val streamId = detail.movieData?.streamId ?: candidate.streamId
                        val extension = detail.movieData?.containerExtension
                            ?: candidate.containerExtension
                            ?: "mp4"
                        candidate.toStream(
                            url = buildUrl("movie", streamId.toString(), extension),
                            categoryNames = index.vodCategoryNames(candidate.categoryIds),
                            siblingHasLegendado = siblingHasLegendado
                        )
                    }
                }
            }.awaitAll()
        }
        val streams = attempts.mapNotNull { it.getOrNull() }
            .sortedByDescending { XtreamTitleMatcher.preferenceScore(it.title.orEmpty()) }
        return attempts.toResolution(streams)
    }

    private suspend fun resolveSeriesCandidates(
        candidates: List<XtreamSeriesItem>,
        season: Int,
        episode: Int,
        index: XtreamCatalogIndex
    ): XtreamResolution {
        if (candidates.isEmpty()) return XtreamResolution.Unavailable
        val siblingHasLegendado = candidates.any { candidate ->
            isLegendadoEntry(candidate.displayTitle, index.seriesCategoryNames(candidate.categoryIds))
        }
        val attempts = supervisorScope {
            candidates.map { candidate ->
                async {
                    runCatching {
                        val detail = getSeriesDetail(candidate.seriesId)
                        val match = detail.episodeAt(
                            season = season,
                            episode = episode,
                            releaseYear = candidate.releaseYear
                        ) ?: return@runCatching null
                        candidate.toStream(
                            episode = match,
                            url = buildUrl("series", match.id, match.containerExtension ?: "mp4"),
                            categoryNames = index.seriesCategoryNames(candidate.categoryIds),
                            siblingHasLegendado = siblingHasLegendado
                        )
                    }
                }
            }.awaitAll()
        }
        val streams = attempts.mapNotNull { it.getOrNull() }
            .sortedByDescending { XtreamTitleMatcher.preferenceScore(it.title.orEmpty()) }
        return attempts.toResolution(streams)
    }

    private suspend fun getSeriesDetail(seriesId: Int): XtreamSeriesInfoResponse =
        seriesDetails[seriesId] ?: detailSemaphore.withPermit {
            seriesDetails[seriesId] ?: dataSource.getSeriesInfo(seriesId).also {
                seriesDetails[seriesId] = it
            }
        }

    private fun normalizeTitles(titles: Collection<String>): List<String> = titles.asSequence()
        .map(XtreamTitleMatcher::normalize)
        .filter(String::isNotBlank)
        .distinct()
        .toList()

    @Synchronized
    private fun resetCachesIfNeeded(generation: Long) {
        if (cacheGeneration == generation) return
        cacheGeneration = generation
        vodDetails.clear()
        seriesDetails.clear()
        resolutions.clear()
    }

    private fun cacheResolution(key: String, resolution: XtreamResolution) {
        if (resolution !is XtreamResolution.Failure) resolutions[key] = resolution
    }

    private fun <T> List<Result<T?>>.toResolution(streams: List<Stream>): XtreamResolution = when {
        streams.isNotEmpty() -> XtreamResolution.Available(AddonStreams("Lume", null, streams))
        any { it.isSuccess } -> XtreamResolution.Unavailable
        else -> XtreamResolution.Failure("Nao foi possivel acessar a fonte de reproducao.")
    }

    private fun buildUrl(kind: String, id: String, extension: String): String {
        val credentials = credentialsProvider()
        return credentials.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
        .addPathSegment(kind)
        .addPathSegment(credentials.username)
        .addPathSegment(credentials.password)
        .addPathSegment("$id.${extension.trimStart('.')}")
        .build()
        .toString()
    }

    fun clearRuntimeCaches() {
        cacheGeneration = Long.MIN_VALUE
        vodDetails.clear()
        seriesDetails.clear()
        resolutions.clear()
    }

    private fun XtreamSeriesInfoResponse.episodeAt(
        season: Int,
        episode: Int,
        releaseYear: Int?
    ): XtreamEpisode? = normalizedEpisodes(releaseYear)
        .firstOrNull { (normalizedSeason, normalizedEpisode) ->
            normalizedSeason == season && normalizedEpisode == episode
        }
        ?.third

    private fun XtreamSeriesInfoResponse.normalizedEpisodes(
        releaseYear: Int?
    ): Sequence<Triple<Int, Int, XtreamEpisode>> {
        val parsed = episodes.asSequence().flatMap { (seasonKey, seasonEpisodes) ->
            val fallbackSeason = seasonKey.toIntOrNull()
            seasonEpisodes.asSequence().mapNotNull { item ->
                val season = item.parsedSeason ?: fallbackSeason
                val episode = item.parsedEpisodeNumber
                if (season == null || episode == null) null else Triple(season, episode, item)
            }
        }.toList()
        val providerSeasons = parsed.map { it.first }.toSet()
        val yearSuffix = releaseYear?.rem(100)
        val mislabeledYearSeason = providerSeasons.singleOrNull()?.takeIf { providerSeason ->
            providerSeason != 1 && yearSuffix != null && providerSeason == yearSuffix
        }
        return parsed.asSequence().map { (season, episode, item) ->
            Triple(if (season == mislabeledYearSeason) 1 else season, episode, item)
        }
    }

    private fun XtreamVodItem.toStream(
        url: String,
        categoryNames: List<String>,
        siblingHasLegendado: Boolean
    ): Stream {
        val entryTitle = displayTitle
        val audioGroup = audioGroupFor(entryTitle, categoryNames)
        val tag = streamTagFor(entryTitle, categoryNames, siblingHasLegendado, audioGroup)
        return directStream(
            label = withTag(canonicalRow(entryTitle, tag), tag),
            url = url,
            audioBingeGroup = audioGroup
        )
    }

    private fun XtreamSeriesItem.toStream(
        episode: XtreamEpisode,
        url: String,
        categoryNames: List<String>,
        siblingHasLegendado: Boolean
    ): Stream {
        val entryTitle = displayTitle
        val audioGroup = audioGroupFor(entryTitle, categoryNames)
        val tag = streamTagFor(entryTitle, categoryNames, siblingHasLegendado, audioGroup)
        return directStream(
            label = withTag(
                canonicalRow(episode.title?.takeIf { it.isNotBlank() } ?: entryTitle, tag),
                tag
            ),
            url = url,
            audioBingeGroup = audioGroup
        )
    }

    /** Strips the matching audio-marker text when the row is about to carry a
     *  canonical audio suffix; category tags such as "4K"/"CINEMA" keep the
     *  original text untouched. */
    private fun canonicalRow(label: String, tag: XtreamStreamTag?): String = when (tag) {
        XtreamStreamTag.LEGENDADO -> stripAudioMarkers(label, LEGENDADO_WORD)
        XtreamStreamTag.DUBLADO -> stripAudioMarkers(label, DUBLADO_WORD)
        else -> label
    }

    private fun directStream(label: String, url: String, audioBingeGroup: String): Stream = Stream(
        name = "Lume",
        title = label,
        description = null,
        url = url,
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = StreamBehaviorHints(
            notWebReady = null,
            bingeGroup = audioBingeGroup,
            countryWhitelist = null,
            proxyHeaders = null
        ),
        addonName = "Lume",
        addonLogo = null,
        quality = XtreamTitleMatcher.qualityValue(label).takeIf { it > 0 }?.let { "${it}p" },
        qualityValue = XtreamTitleMatcher.qualityValue(label)
    )

    private fun isLegendadoEntry(displayTitle: String, categoryNames: List<String>): Boolean =
        audioGroupFor(displayTitle, categoryNames) == XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG

    private fun audioGroupFor(displayTitle: String, categoryNames: List<String>): String {
        if (XtreamTitleMatcher.audioBingeGroup(displayTitle) == XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG) {
            return XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG
        }
        val inLegendadoCategory = categoryNames.any { categoryName ->
            XtreamTitleMatcher.tagFromCategoryName(categoryName) == XtreamStreamTag.LEGENDADO
        }
        return if (inLegendadoCategory) {
            XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG
        } else {
            XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB
        }
    }

    /** Provider-category tag, "Legendado" for entries whose name carried a
     *  stripped "[L]"-style marker, or "Dublado" for the untagged default copy
     *  when a legendado sibling exists in the same resolution. */
    private fun streamTagFor(
        displayTitle: String,
        categoryNames: List<String>,
        siblingHasLegendado: Boolean,
        audioGroup: String
    ): XtreamStreamTag? {
        categoryNames.forEach { categoryName ->
            XtreamTitleMatcher.tagFromCategoryName(categoryName)?.let { return it }
        }
        if (audioGroup == XtreamTitleMatcher.AUDIO_BINGE_GROUP_LEG &&
            LEGENDADO_MARKER.containsMatchIn(displayTitle)
        ) {
            return XtreamStreamTag.LEGENDADO
        }
        if (audioGroup == XtreamTitleMatcher.AUDIO_BINGE_GROUP_DUB &&
            DUBLADO_MARKER.containsMatchIn(displayTitle)
        ) {
            return XtreamStreamTag.DUBLADO
        }
        if (siblingHasLegendado &&
            !XtreamTitleMatcher.titleShowsTag(displayTitle, XtreamStreamTag.LEGENDADO) &&
            !XtreamTitleMatcher.titleShowsTag(displayTitle, XtreamStreamTag.DUBLADO)
        ) {
            return XtreamStreamTag.DUBLADO
        }
        return null
    }

    private fun withTag(title: String, tag: XtreamStreamTag?): String {
        if (tag == null || XtreamTitleMatcher.titleShowsTag(title, tag)) return title
        return "$title • ${tag.label}"
    }

}
