package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.local.XtreamCatalogDao
import com.nuvio.tv.data.local.XtreamMovieIdentityEntity
import com.nuvio.tv.data.remote.api.XtreamCategory
import com.nuvio.tv.data.remote.api.XtreamEpisode
import com.nuvio.tv.data.remote.api.XtreamSeriesItem
import com.nuvio.tv.data.remote.api.XtreamVodItem
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.Video
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException

private const val PROVIDER_ADDON_ID = "xtream"
private const val PROVIDER_NAME = "Lume"
private const val PAGE_SIZE = 40

data class XtreamProviderCatalogState(
    val ready: Boolean = false,
    val refreshing: Boolean = false,
    val indexedMovies: Int = 0,
    val totalMovies: Int = 0,
    val error: String? = null,
)

private data class ProviderMovie(
    val id: String,
    val variants: List<XtreamVodItem>,
    val tmdbId: Int?,
) {
    val primary = variants.maxByOrNull { XtreamTitleMatcher.preferenceScore(it.displayTitle) }!!
}

private data class ProviderSeries(
    val id: String,
    val variants: List<XtreamSeriesItem>,
) {
    val primary = variants.maxByOrNull { XtreamTitleMatcher.preferenceScore(it.displayTitle) }!!
}

@Singleton
class XtreamProviderCatalogRepository @Inject constructor(
    private val catalogRepository: XtreamCatalogRepository,
    private val dataSource: XtreamDataSource,
    private val credentialsStore: XtreamCredentialsStore,
    private val dao: XtreamCatalogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hydrateSemaphore = Semaphore(2)
    private val _state = MutableStateFlow(XtreamProviderCatalogState())
    val state: StateFlow<XtreamProviderCatalogState> = _state.asStateFlow()

    @Volatile private var movies: List<ProviderMovie> = emptyList()
    @Volatile private var series: List<ProviderSeries> = emptyList()
    @Volatile private var vodCategories: List<XtreamCategory> = emptyList()
    @Volatile private var seriesCategories: List<XtreamCategory> = emptyList()
    private var hydrationJob: Job? = null

    init {
        scope.launch {
            credentialsStore.credentials.collectLatest { credentials ->
                hydrationJob?.cancel()
                movies = emptyList()
                series = emptyList()
                if (credentials == null) {
                    _state.value = XtreamProviderCatalogState(error = "Xtream nao configurado.")
                } else {
                    bootstrap(credentials)
                }
            }
        }
    }

    suspend fun homeRows(): List<CatalogRow> {
        awaitReady()
        val recentMovies = movies.sortedByDescending { it.primary.added?.toLongOrNull() ?: 0L }
        val recentSeries = series.sortedByDescending { it.primary.lastModified?.toLongOrNull() ?: 0L }
        val rows = mutableListOf(
            movieRow("recent-movies", "Filmes adicionados recentemente", recentMovies.take(80)),
            seriesRow("recent-series", "Series adicionadas recentemente", recentSeries.take(80)),
        )
        val movieCounts = categoryCounts(movies.flatMap { movie -> movie.primary.allCategoryIds().map { it to movie } })
        val seriesCounts = categoryCounts(series.flatMap { show -> show.primary.allCategoryIds().map { it to show } })
        vodCategories.asSequence().filterNot(::excluded).sortedByDescending { movieCounts[it.categoryId] ?: 0 }
            .take(5).forEach { category ->
                rows += movieRow(
                    "movie-${category.categoryId}",
                    category.categoryName,
                    movies.filter { category.categoryId in it.primary.allCategoryIds() },
                )
            }
        seriesCategories.asSequence().filterNot(::excluded).sortedByDescending { seriesCounts[it.categoryId] ?: 0 }
            .take(3).forEach { category ->
                rows += seriesRow(
                    "series-${category.categoryId}",
                    category.categoryName,
                    series.filter { category.categoryId in it.primary.allCategoryIds() },
                )
            }
        return rows.filter { it.items.isNotEmpty() }
    }

    suspend fun allCategoryRows(): List<CatalogRow> {
        awaitReady()
        return buildList {
            vodCategories.filterNot(::excluded).forEach { category ->
                add(movieRow(
                    "movie-${category.categoryId}",
                    category.categoryName,
                    movies.filter { category.categoryId in it.primary.allCategoryIds() },
                ))
            }
            seriesCategories.filterNot(::excluded).forEach { category ->
                add(seriesRow(
                    "series-${category.categoryId}",
                    category.categoryName,
                    series.filter { category.categoryId in it.primary.allCategoryIds() },
                ))
            }
        }.filter { it.items.isNotEmpty() }
    }

    suspend fun search(query: String): List<CatalogRow> {
        awaitReady()
        val normalized = XtreamTitleMatcher.normalize(query)
        if (normalized.isBlank()) return emptyList()
        val movieMatches = movies.filter { XtreamTitleMatcher.normalize(it.primary.displayTitle).contains(normalized) }
            .sortedBy { XtreamTitleMatcher.normalize(it.primary.displayTitle).indexOf(normalized) }
        val seriesMatches = series.filter { XtreamTitleMatcher.normalize(it.primary.displayTitle).contains(normalized) }
            .sortedBy { XtreamTitleMatcher.normalize(it.primary.displayTitle).indexOf(normalized) }
        return listOf(
            movieRow("search-movies", "Filmes", movieMatches),
            seriesRow("search-series", "Series", seriesMatches),
        ).filter { it.items.isNotEmpty() }
    }

    suspend fun searchPage(query: String, catalogId: String, offset: Int): CatalogRow? {
        awaitReady()
        val normalized = XtreamTitleMatcher.normalize(query)
        if (normalized.isBlank()) return null
        return when (catalogId) {
            "search-movies" -> movieRow(
                catalogId,
                "Filmes",
                movies.filter { XtreamTitleMatcher.normalize(it.primary.displayTitle).contains(normalized) }
                    .sortedBy { XtreamTitleMatcher.normalize(it.primary.displayTitle).indexOf(normalized) },
                offset,
            )
            "search-series" -> seriesRow(
                catalogId,
                "Series",
                series.filter { XtreamTitleMatcher.normalize(it.primary.displayTitle).contains(normalized) }
                    .sortedBy { XtreamTitleMatcher.normalize(it.primary.displayTitle).indexOf(normalized) },
                offset,
            )
            else -> null
        }
    }

    suspend fun page(catalogId: String, offset: Int): CatalogRow? {
        awaitReady()
        val categoryId = catalogId.substringAfter('-', "")
        return when {
            catalogId == "recent-movies" -> movieRow(catalogId, "Filmes adicionados recentemente", movies.sortedByDescending { it.primary.added?.toLongOrNull() ?: 0L }, offset)
            catalogId == "recent-series" -> seriesRow(catalogId, "Series adicionadas recentemente", series.sortedByDescending { it.primary.lastModified?.toLongOrNull() ?: 0L }, offset)
            catalogId.startsWith("movie-") -> vodCategories.firstOrNull { it.categoryId == categoryId }?.let { category ->
                movieRow(catalogId, category.categoryName, movies.filter { categoryId in it.primary.allCategoryIds() }, offset)
            }
            catalogId.startsWith("series-") -> seriesCategories.firstOrNull { it.categoryId == categoryId }?.let { category ->
                seriesRow(catalogId, category.categoryName, series.filter { categoryId in it.primary.allCategoryIds() }, offset)
            }
            else -> null
        }
    }

    suspend fun meta(contentId: String): Meta? {
        awaitReady()
        movie(contentId)?.let { movie ->
            val item = movie.primary
            val tmdbId = movie.tmdbId ?: runCatching {
                val resolved = dataSource.getVodInfo(item.streamId).info?.tmdbId?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                val credentials = credentialsStore.current()
                if (credentials != null) {
                    dao.upsertIdentity(
                        XtreamMovieIdentityEntity(
                            sourceFingerprint = XtreamCatalogRepository.sourceFingerprint(credentials.baseUrl, credentials.username),
                            streamId = item.streamId,
                            tmdbId = resolved,
                            hydratedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                resolved
            }.getOrNull()
            return Meta(
                id = tmdbId?.let { "tmdb:$it" } ?: movie.id, type = ContentType.MOVIE, name = cleanTitle(item.displayTitle),
                poster = item.streamIcon, posterShape = PosterShape.POSTER, background = item.streamIcon,
                logo = null, description = item.plot, releaseInfo = item.releaseYear?.toString(),
                imdbRating = item.rating?.toFloatOrNull(), genres = splitGenres(item.genre), runtime = null,
                director = emptyList(), cast = emptyList(), videos = emptyList(), country = null,
                awards = null, language = null, links = emptyList(), released = item.releaseDate,
                rawPosterUrl = item.streamIcon,
            )
        }
        series(contentId)?.let { show ->
            val item = show.primary
            val detail = runCatching { dataSource.getSeriesInfo(item.seriesId) }.getOrNull()
            val videos = detail?.episodes.orEmpty().flatMap { (seasonKey, episodes) ->
                episodes.mapNotNull { episode -> episode.toVideo(seasonKey.toIntOrNull()) }
            }
            return Meta(
                id = show.id, type = ContentType.SERIES, name = cleanTitle(item.displayTitle),
                poster = item.cover, posterShape = PosterShape.POSTER, background = item.cover,
                logo = null, description = item.plot, releaseInfo = item.releaseYear?.toString(),
                imdbRating = item.rating?.toFloatOrNull(), genres = splitGenres(item.genre), runtime = null,
                director = emptyList(), cast = emptyList(), videos = videos, country = null,
                awards = null, language = null, links = emptyList(), released = item.releaseDate ?: item.releaseDateSnakeCase,
                rawPosterUrl = item.cover,
            )
        }
        return null
    }

    suspend fun streams(contentId: String, season: Int?, episode: Int?): AddonStreams? {
        awaitReady()
        val credentials = credentialsStore.current() ?: return null
        val streams = movie(contentId)?.variants?.map { item ->
            directStream(item.displayTitle, buildUrl(credentials, "movie", item.streamId.toString(), item.containerExtension ?: "mp4"))
        } ?: series(contentId)?.variants?.mapNotNull { item ->
            val match = dataSource.getSeriesInfo(item.seriesId).findEpisode(season, episode) ?: return@mapNotNull null
            directStream(match.title ?: item.displayTitle, buildUrl(credentials, "series", match.id, match.containerExtension ?: "mp4"))
        }.orEmpty()
        return streams.sortedByDescending { XtreamTitleMatcher.preferenceScore(it.title.orEmpty()) }
            .takeIf { it.isNotEmpty() }
            ?.let { AddonStreams(PROVIDER_NAME, null, it) }
    }

    private suspend fun bootstrap(credentials: XtreamCredentials) {
        _state.value = XtreamProviderCatalogState(refreshing = true)
        runCatching {
            val snapshot = catalogRepository.currentSnapshot()
            val fingerprint = XtreamCatalogRepository.sourceFingerprint(credentials.baseUrl, credentials.username)
            dao.deleteOtherSources(fingerprint)
            val identities = dao.identities(fingerprint).associateBy { it.streamId }
            val categories = supervisorScope {
                listOf(async { dataSource.getVodCategories() }, async { dataSource.getSeriesCategories() }).awaitAll()
            }
            vodCategories = categories[0]
            seriesCategories = categories[1]
            rebuild(snapshot, identities)
            _state.value = XtreamProviderCatalogState(
                ready = true,
                indexedMovies = identities.size,
                totalMovies = eligibleVod(snapshot.vod).size,
            )
            hydrationJob = scope.launch { hydrateMovies(fingerprint, snapshot) }
        }.onFailure { error ->
            _state.value = XtreamProviderCatalogState(error = error.message ?: "Nao foi possivel carregar o catalogo.")
        }
    }

    private fun rebuild(snapshot: XtreamCatalogSnapshot, identities: Map<Int, XtreamMovieIdentityEntity>) {
        val eligibleMovies = eligibleVod(snapshot.vod)
        movies = eligibleMovies.groupBy { item ->
            providerMovieGroupKey(item, identities[item.streamId]?.tmdbId)
        }.values.map { variants ->
            ProviderMovie("xtream:movie:${variants.minOf { it.streamId }}", variants, variants.firstNotNullOfOrNull { identities[it.streamId]?.tmdbId })
        }
        series = snapshot.series.filterNot { item -> item.allCategoryIds().any(::isExcludedCategoryId) }
            .groupBy { "${XtreamTitleMatcher.normalize(it.displayTitle)}:${it.releaseYear ?: 0}" }
            .values.map { variants -> ProviderSeries("xtream:series:${variants.minOf { it.seriesId }}", variants) }
    }

    private suspend fun hydrateMovies(fingerprint: String, snapshot: XtreamCatalogSnapshot) {
        val known = dao.identities(fingerprint).mapTo(hashSetOf()) { it.streamId }
        val pending = eligibleVod(snapshot.vod).filterNot { it.streamId in known }
            .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
        var completed = known.size
        pending.chunked(20).forEach { batch ->
            supervisorScope {
                batch.map { item ->
                    async {
                        hydrateSemaphore.withPermit {
                            val detail = retryDetail(item.streamId) ?: return@withPermit
                            dao.upsertIdentity(
                                XtreamMovieIdentityEntity(
                                    sourceFingerprint = fingerprint,
                                    streamId = item.streamId,
                                    tmdbId = detail.info?.tmdbId?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                                    hydratedAtMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                }.awaitAll()
            }
            completed += batch.size
            _state.value = _state.value.copy(indexedMovies = completed.coerceAtMost(_state.value.totalMovies))
            delay(100)
        }
    }

    private suspend fun retryDetail(streamId: Int): com.nuvio.tv.data.remote.api.XtreamVodInfoResponse? {
        repeat(3) { attempt ->
            try {
                return dataSource.getVodInfo(streamId)
            } catch (error: Throwable) {
                val retryable = error !is HttpException || error.code() == 429 || error.code() >= 500
                if (!retryable || attempt == 2) return null
                delay(500L shl attempt)
            }
        }
        return null
    }

    private suspend fun awaitReady() {
        while (!_state.value.ready && _state.value.error == null) delay(50)
        check(_state.value.ready) { _state.value.error ?: "Xtream catalog is unavailable" }
    }

    private fun movie(id: String) = movies.firstOrNull { it.id == id }
    private fun series(id: String) = series.firstOrNull { it.id == id }
    private fun eligibleVod(items: List<XtreamVodItem>) = items.filterNot { item -> item.allCategoryIds().any(::isExcludedCategoryId) }
    private fun isExcludedCategoryId(id: String): Boolean =
        (vodCategories + seriesCategories).firstOrNull { it.categoryId == id }?.let(::excluded) == true

    private fun excluded(category: XtreamCategory): Boolean {
        return isExcludedProviderCategory(category.categoryName)
    }

    private fun <T> categoryCounts(values: List<Pair<String, T>>): Map<String, Int> = values.groupingBy { it.first }.eachCount()

    private fun movieRow(id: String, name: String, values: List<ProviderMovie>, offset: Int = 0) = row(
        id, name, ContentType.MOVIE, values.drop(offset).take(PAGE_SIZE).map(::moviePreview), values.size, offset,
    )

    private fun seriesRow(id: String, name: String, values: List<ProviderSeries>, offset: Int = 0) = row(
        id, name, ContentType.SERIES, values.drop(offset).take(PAGE_SIZE).map(::seriesPreview), values.size, offset,
    )

    private fun row(id: String, name: String, type: ContentType, items: List<MetaPreview>, total: Int, offset: Int) = CatalogRow(
        addonId = PROVIDER_ADDON_ID, addonName = PROVIDER_NAME, addonBaseUrl = "xtream://catalog",
        catalogId = id, catalogName = name, type = type, items = items,
        hasMore = offset + items.size < total, currentPage = offset / PAGE_SIZE,
        supportsSkip = true, skipStep = PAGE_SIZE, nextSkip = offset + items.size,
    )

    private fun moviePreview(movie: ProviderMovie): MetaPreview = movie.primary.let { item ->
        MetaPreview(
            id = movie.id, type = ContentType.MOVIE, name = cleanTitle(item.displayTitle), poster = item.streamIcon,
            posterShape = PosterShape.POSTER, background = item.streamIcon, logo = null, description = item.plot,
            releaseInfo = item.releaseYear?.toString(), imdbRating = item.rating?.toFloatOrNull(), genres = splitGenres(item.genre),
            released = item.releaseDate, rawPosterUrl = item.streamIcon, sourceAddonBaseUrl = "xtream://catalog",
        )
    }

    private fun seriesPreview(show: ProviderSeries): MetaPreview = show.primary.let { item ->
        MetaPreview(
            id = show.id, type = ContentType.SERIES, name = cleanTitle(item.displayTitle), poster = item.cover,
            posterShape = PosterShape.POSTER, background = item.cover, logo = null, description = item.plot,
            releaseInfo = item.releaseYear?.toString(), imdbRating = item.rating?.toFloatOrNull(), genres = splitGenres(item.genre),
            released = item.releaseDate ?: item.releaseDateSnakeCase, rawPosterUrl = item.cover, sourceAddonBaseUrl = "xtream://catalog",
        )
    }

    private fun XtreamEpisode.toVideo(fallbackSeason: Int?): Video? {
        val parsedEpisode = parsedEpisodeNumber ?: return null
        val parsedSeason = parsedSeason ?: fallbackSeason ?: return null
        return Video(
            id = "xtream:episode:$id", title = title ?: "Episodio $parsedEpisode", released = null,
            thumbnail = null, season = parsedSeason, episode = parsedEpisode, overview = null, available = true,
        )
    }

    private fun com.nuvio.tv.data.remote.api.XtreamSeriesInfoResponse.findEpisode(season: Int?, episode: Int?): XtreamEpisode? {
        if (season == null || episode == null) return null
        return episodes.asSequence().flatMap { (key, items) ->
            items.asSequence().map { key.toIntOrNull() to it }
        }.firstOrNull { (fallback, item) ->
            (item.parsedSeason ?: fallback) == season && item.parsedEpisodeNumber == episode
        }?.second
    }

    private fun directStream(label: String, url: String) = Stream(
        name = PROVIDER_NAME, title = label, description = null, url = url, ytId = null, infoHash = null,
        fileIdx = null, externalUrl = null, behaviorHints = null, addonName = PROVIDER_NAME, addonLogo = null,
        quality = XtreamTitleMatcher.qualityValue(label).takeIf { it > 0 }?.let { "${it}p" },
        qualityValue = XtreamTitleMatcher.qualityValue(label),
    )

    private fun buildUrl(credentials: XtreamCredentials, kind: String, id: String, extension: String): String =
        credentials.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment(kind).addPathSegment(credentials.username).addPathSegment(credentials.password)
            .addPathSegment("$id.${extension.trimStart('.')}").build().toString()
}

private fun XtreamVodItem.allCategoryIds(): Set<String> = (categoryIds + listOfNotNull(categoryId)).toSet()
private fun XtreamSeriesItem.allCategoryIds(): Set<String> = (categoryIds + listOfNotNull(categoryId)).toSet()
private fun splitGenres(value: String?): List<String> = value.orEmpty().split(',', '/', '|').map(String::trim).filter(String::isNotBlank)
private fun cleanTitle(value: String): String = value.replace(Regex("\\s*\\((?:19|20)\\d{2}\\)\\s*$"), "").trim()
private fun normalizeText(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

internal fun providerMovieGroupKey(item: XtreamVodItem, tmdbId: Int?): String =
    tmdbId?.let { "tmdb:$it" }
        ?: "title:${XtreamTitleMatcher.normalize(item.displayTitle)}:${item.releaseYear ?: 0}"

internal fun isExcludedProviderCategory(name: String): Boolean {
    val value = normalizeText(name)
    return value.contains("adult") || value.contains("xxx") ||
        value.contains("reelshort") || value.contains("reelsshort")
}
