package com.nuvio.tv.core.tmdb

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbDiscoverResponse
import com.nuvio.tv.data.remote.api.TmdbDiscoverResult
import com.nuvio.tv.data.remote.api.TmdbGenre
import com.nuvio.tv.data.remote.api.TmdbListItem
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class TmdbHomeCatalogDefinition(
    val id: String,
    val title: String,
    val contentType: ContentType,
    /** True when the underlying catalog supports a user genre filter (TMDB discover with_genres). */
    val supportsGenreFilter: Boolean,
)

private enum class CatalogMedia {
    MOVIE,
    TV,
}

private sealed interface HomeCatalogSpec {
    val id: String
    val title: String
}

private data class TrendingSpec(
    override val id: String,
    override val title: String,
    val media: CatalogMedia,
) : HomeCatalogSpec

private data class OnTheAirSpec(
    override val id: String,
    override val title: String,
) : HomeCatalogSpec

private data class TmdbListSpec(
    override val id: String,
    override val title: String,
    val listId: Int,
) : HomeCatalogSpec

private data class DiscoverSpec(
    override val id: String,
    override val title: String,
    val media: Set<CatalogMedia>,
    val movieGenres: String? = null,
    val tvGenres: String? = null,
    val movieSort: String = "popularity.desc",
    val tvSort: String = "popularity.desc",
    val releaseDateGte: String? = null,
    val releaseDateLte: String? = null,
    val recentMonths: Long? = null,
    val classicYearsAgo: Long? = null,
    val voteAverageGte: Double? = null,
    val voteCountGte: Int? = 80,
    val originalLanguage: String? = null,
    val originCountry: String? = null,
    val keywords: String? = null,
    val companies: String? = null,
    val networks: String? = null,
    val watchProviderId: String? = null,
) : HomeCatalogSpec

private val MOVIES = setOf(CatalogMedia.MOVIE)
private val SERIES = setOf(CatalogMedia.TV)
private val MOVIES_AND_SERIES = setOf(CatalogMedia.MOVIE, CatalogMedia.TV)

private val HOME_CATALOGS: List<HomeCatalogSpec> = listOf(
    TrendingSpec("trending-movies", "Filmes em alta nesta semana", CatalogMedia.MOVIE),
    TrendingSpec("trending-series", "Series em alta nesta semana", CatalogMedia.TV),
    DiscoverSpec("popular-movies", "Filmes populares", MOVIES, voteCountGte = 100),
    DiscoverSpec("popular-series", "Series populares", SERIES, voteCountGte = 100),
    DiscoverSpec(
        id = "top-movies",
        title = "Filmes mais bem avaliados",
        media = MOVIES,
        movieSort = "vote_average.desc",
        voteAverageGte = 7.0,
        voteCountGte = 500,
    ),
    DiscoverSpec(
        id = "top-series",
        title = "Series mais bem avaliadas",
        media = SERIES,
        tvSort = "vote_average.desc",
        voteAverageGte = 7.0,
        voteCountGte = 300,
    ),
    DiscoverSpec(
        id = "recent-releases",
        title = "Lancamentos recentes",
        media = MOVIES,
        movieSort = "primary_release_date.desc",
        recentMonths = 4,
        voteCountGte = 20,
    ),
    OnTheAirSpec("new-tv-episodes", "Series com episodios novos"),
    DiscoverSpec(
        id = "action-adventure",
        title = "Acao e aventura",
        media = MOVIES_AND_SERIES,
        movieGenres = "28|12",
        tvGenres = "10759",
    ),
    DiscoverSpec("comedy", "Comedia", MOVIES_AND_SERIES, movieGenres = "35", tvGenres = "35"),
    DiscoverSpec(
        id = "horror-thriller",
        title = "Terror e suspense",
        media = MOVIES_AND_SERIES,
        movieGenres = "27|53",
        tvGenres = "9648|80",
    ),
    DiscoverSpec(
        id = "scifi-fantasy",
        title = "Ficcao cientifica e fantasia",
        media = MOVIES_AND_SERIES,
        movieGenres = "878|14",
        tvGenres = "10765",
    ),
    DiscoverSpec("animation", "Animacoes", MOVIES_AND_SERIES, movieGenres = "16", tvGenres = "16"),
    DiscoverSpec("documentaries", "Documentarios", MOVIES_AND_SERIES, movieGenres = "99", tvGenres = "99"),
    DiscoverSpec(
        id = "brazilian-productions",
        title = "Producoes brasileiras",
        media = MOVIES_AND_SERIES,
        originCountry = "BR",
        originalLanguage = "pt",
        voteCountGte = 10,
    ),
    DiscoverSpec(
        id = "acclaimed-classics",
        title = "Classicos bem avaliados",
        media = MOVIES,
        movieSort = "vote_average.desc",
        classicYearsAgo = 25,
        voteAverageGte = 7.0,
        voteCountGte = 500,
    ),

    DiscoverSpec("netflix", "Na Netflix", MOVIES_AND_SERIES, watchProviderId = "8", voteCountGte = 30),
    DiscoverSpec("prime-video", "No Prime Video", MOVIES_AND_SERIES, watchProviderId = "119", voteCountGte = 30),
    DiscoverSpec("disney-plus", "No Disney+", MOVIES_AND_SERIES, watchProviderId = "337", voteCountGte = 30),
    DiscoverSpec("apple-tv", "Na Apple TV", MOVIES_AND_SERIES, watchProviderId = "350", voteCountGte = 20),
    DiscoverSpec("hbo-max", "Na HBO Max", MOVIES_AND_SERIES, watchProviderId = "1899", voteCountGte = 30),
    DiscoverSpec("paramount-plus", "No Paramount+", MOVIES_AND_SERIES, watchProviderId = "531", voteCountGte = 20),
    DiscoverSpec("globoplay", "No Globoplay", MOVIES_AND_SERIES, watchProviderId = "307", voteCountGte = 10),

    TmdbListSpec("oscar-best-picture", "Vencedores do Oscar de Melhor Filme", 3728),
    TmdbListSpec("marvel-universe", "Universo Cinematografico Marvel", 84979),
    DiscoverSpec("pixar", "Mundo Pixar", MOVIES, companies = "3", voteCountGte = 50),
    DiscoverSpec("marvel-studios", "Marvel Studios", MOVIES_AND_SERIES, companies = "420", voteCountGte = 50),
    DiscoverSpec("studio-ghibli", "Studio Ghibli", MOVIES, companies = "10342", voteCountGte = 20),
    DiscoverSpec("a24", "Cinema A24", MOVIES, companies = "41077", voteCountGte = 30),
    DiscoverSpec("lucasfilm", "Lucasfilm", MOVIES_AND_SERIES, companies = "1", voteCountGte = 30),

    DiscoverSpec("crime-mystery", "Crime e misterio", MOVIES_AND_SERIES, movieGenres = "80|9648", tvGenres = "80|9648"),
    DiscoverSpec("romance", "Romances", MOVIES_AND_SERIES, movieGenres = "10749", tvGenres = "18", voteCountGte = 50),
    DiscoverSpec("family", "Para ver em familia", MOVIES_AND_SERIES, movieGenres = "10751|16", tvGenres = "10762|16"),
    DiscoverSpec("war-history", "Guerra e historia", MOVIES_AND_SERIES, movieGenres = "10752|36", tvGenres = "10768"),
    DiscoverSpec("westerns", "Faroeste", MOVIES_AND_SERIES, movieGenres = "37", tvGenres = "37", voteCountGte = 30),
    DiscoverSpec("music", "Musica e musicais", MOVIES, movieGenres = "10402", voteCountGte = 30),
    DiscoverSpec("reality", "Reality shows", SERIES, tvGenres = "10764", voteCountGte = 20),
    DiscoverSpec(
        id = "anime",
        title = "Animes",
        media = MOVIES_AND_SERIES,
        movieGenres = "16",
        tvGenres = "16",
        originalLanguage = "ja",
        voteCountGte = 30,
    ),
    DiscoverSpec(
        id = "korean-dramas",
        title = "Dramas coreanos",
        media = SERIES,
        tvGenres = "18",
        originalLanguage = "ko",
        voteCountGte = 30,
    ),
    DiscoverSpec(
        id = "eighties",
        title = "Classicos dos anos 80",
        media = MOVIES,
        movieSort = "vote_average.desc",
        releaseDateGte = "1980-01-01",
        releaseDateLte = "1989-12-31",
        voteAverageGte = 6.5,
        voteCountGte = 300,
    ),
    DiscoverSpec(
        id = "nineties",
        title = "Classicos dos anos 90",
        media = MOVIES,
        movieSort = "vote_average.desc",
        releaseDateGte = "1990-01-01",
        releaseDateLte = "1999-12-31",
        voteAverageGte = 6.5,
        voteCountGte = 300,
    ),
    DiscoverSpec(
        id = "two-thousands",
        title = "Sucessos dos anos 2000",
        media = MOVIES,
        movieSort = "vote_count.desc",
        releaseDateGte = "2000-01-01",
        releaseDateLte = "2009-12-31",
        voteCountGte = 500,
    ),
)

@Singleton
class TmdbCatalogService @Inject constructor(
    private val api: TmdbApi,
) {
    @Volatile private var cachedHome: List<CatalogRow> = emptyList()
    private val homeRequestSemaphore = Semaphore(4)

    val homeCatalogDefinitions: List<TmdbHomeCatalogDefinition> = HOME_CATALOGS.map { spec ->
        TmdbHomeCatalogDefinition(
            id = spec.id,
            title = spec.title,
            contentType = spec.contentType(),
            supportsGenreFilter = spec is DiscoverSpec,
        )
    }

    suspend fun search(query: String, language: String = "pt-BR", page: Int = 1): List<CatalogRow> = coroutineScope {
        val movies = async { api.searchMovies(BuildConfig.TMDB_API_KEY, query, language, page).body() }
        val series = async { api.searchTv(BuildConfig.TMDB_API_KEY, query, language, page).body() }
        listOfNotNull(
            movies.await()?.toRow("search-movies", "Filmes", ContentType.MOVIE),
            series.await()?.toRow("search-series", "Series", ContentType.SERIES),
        ).filter { it.items.isNotEmpty() }
    }

    suspend fun home(language: String = "pt-BR"): List<CatalogRow> = try {
        fetchHome(language).also { rows ->
            if (rows.isNotEmpty()) cachedHome = rows
        }.ifEmpty {
            cachedHome.ifEmpty { error("TMDB returned an empty catalog") }
        }
    } catch (error: Exception) {
        cachedHome.ifEmpty { throw error }
    }

    private suspend fun fetchHome(language: String): List<CatalogRow> = coroutineScope {
        HOME_CATALOGS.map { spec ->
            async {
                runCatching { fetch(spec, page = 1, language = language) }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }

    /**
     * Loads one page of a home catalog.
     *
     * @param genre When set, DiscoverSpec catalogs replace their fixed genre filter with this one
     *   (single TMDB genre id, e.g. "878"). Catalogs that cannot be genre-filtered (trending,
     *   on-the-air, static lists) ignore it. Null keeps the catalog definition unchanged.
     */
    suspend fun homePage(
        catalogId: String,
        page: Int,
        language: String = "pt-BR",
        genre: String? = null,
    ): CatalogRow? {
        require(page > 0) { "page must be positive" }
        val spec = HOME_CATALOGS.firstOrNull { it.id == catalogId } ?: return null
        return fetch(spec, page, language, genre)
    }

    suspend fun movieGenres(language: String = "pt-BR"): List<TmdbGenre> =
        homeRequestSemaphore.withPermit {
            api.getMovieGenres(BuildConfig.TMDB_API_KEY, language).body()
        }?.genres.orEmpty()

    suspend fun tvGenres(language: String = "pt-BR"): List<TmdbGenre> =
        homeRequestSemaphore.withPermit {
            api.getTvGenres(BuildConfig.TMDB_API_KEY, language).body()
        }?.genres.orEmpty()

    private fun HomeCatalogSpec.contentType(): ContentType = when (this) {
        is TrendingSpec -> media.toContentType()
        is OnTheAirSpec -> ContentType.SERIES
        is TmdbListSpec -> ContentType.MOVIE
        is DiscoverSpec -> if (media == SERIES) ContentType.SERIES else ContentType.MOVIE
    }

    private suspend fun fetch(
        spec: HomeCatalogSpec,
        page: Int,
        language: String,
        genre: String? = null,
    ): CatalogRow? = when (spec) {
        is TrendingSpec -> fetchTrending(spec, page, language)
        is OnTheAirSpec -> homeRequestSemaphore.withPermit {
            api.getTvOnTheAir(BuildConfig.TMDB_API_KEY, language, page).body()
        }
            ?.toRow(spec.id, spec.title, ContentType.SERIES)
        is TmdbListSpec -> fetchList(spec, page, language)
        is DiscoverSpec -> fetchDiscover(spec, page, language, genre)
    }

    private suspend fun fetchTrending(spec: TrendingSpec, page: Int, language: String): CatalogRow? {
        val response = when (spec.media) {
            CatalogMedia.MOVIE -> homeRequestSemaphore.withPermit {
                api.getTrendingMovies(BuildConfig.TMDB_API_KEY, language, page)
            }
            CatalogMedia.TV -> homeRequestSemaphore.withPermit {
                api.getTrendingTv(BuildConfig.TMDB_API_KEY, language, page)
            }
        }.body() ?: return null
        return response.toRow(
            id = spec.id,
            name = spec.title,
            type = spec.media.toContentType(),
        )
    }

    private suspend fun fetchList(spec: TmdbListSpec, page: Int, language: String): CatalogRow? {
        val response = homeRequestSemaphore.withPermit {
            api.getListDetails(spec.listId, BuildConfig.TMDB_API_KEY, language, page)
        }.body() ?: return null
        val items = response.items.orEmpty().mapNotNull { it.toPreview() }.distinctBy { "${it.type}:${it.id}" }
        return catalogRow(
            id = spec.id,
            name = spec.title,
            type = ContentType.MOVIE,
            items = items,
            page = response.page ?: page,
            totalPages = response.totalPages ?: page,
            hideTypeSuffix = items.map { it.type }.distinct().size > 1,
        )
    }

    private suspend fun fetchDiscover(
        spec: DiscoverSpec,
        page: Int,
        language: String,
        genre: String?,
    ): CatalogRow? = coroutineScope {
        val today = LocalDate.now()
        val dateGte = spec.recentMonths?.let { today.minusMonths(it).toString() } ?: spec.releaseDateGte
        val dateLte = spec.classicYearsAgo?.let { today.minusYears(it).toString() }
            ?: spec.releaseDateLte
            ?: today.toString()
        // A user-selected genre replaces (does not accumulate with) the genre the rail
        // defines itself. Non-genre params (media, sort, provider, dates, ...) are kept.
        val movieGenres = genre ?: spec.movieGenres
        val tvGenres = genre ?: spec.tvGenres
        val movieRequest = if (CatalogMedia.MOVIE in spec.media) async {
            homeRequestSemaphore.withPermit { api.discoverMovies(
                apiKey = BuildConfig.TMDB_API_KEY,
                language = language,
                page = page,
                sortBy = spec.movieSort,
                withCompanies = spec.companies,
                releaseDateLte = dateLte,
                voteCountGte = spec.voteCountGte,
                withGenres = movieGenres,
                releaseDateGte = dateGte,
                voteAverageGte = spec.voteAverageGte,
                withOriginalLanguage = spec.originalLanguage,
                withOriginCountry = spec.originCountry,
                withKeywords = spec.keywords,
                watchRegion = spec.watchProviderId?.let { "BR" },
                withWatchProviders = spec.watchProviderId,
                withWatchMonetizationTypes = spec.watchProviderId?.let { "flatrate" },
            ).body() }
        } else null
        val tvRequest = if (CatalogMedia.TV in spec.media) async {
            homeRequestSemaphore.withPermit { api.discoverTv(
                apiKey = BuildConfig.TMDB_API_KEY,
                language = language,
                page = page,
                sortBy = spec.tvSort,
                withCompanies = spec.companies,
                withNetworks = spec.networks,
                firstAirDateLte = dateLte,
                voteCountGte = spec.voteCountGte,
                withGenres = tvGenres,
                firstAirDateGte = dateGte,
                voteAverageGte = spec.voteAverageGte,
                withOriginalLanguage = spec.originalLanguage,
                withOriginCountry = spec.originCountry,
                withKeywords = spec.keywords,
                watchRegion = spec.watchProviderId?.let { "BR" },
                withWatchProviders = spec.watchProviderId,
                withWatchMonetizationTypes = spec.watchProviderId?.let { "flatrate" },
            ).body() }
        } else null

        val movieResponse = movieRequest?.await()
        val tvResponse = tvRequest?.await()
        if (movieResponse == null && tvResponse == null) return@coroutineScope null
        val items = buildList {
            movieResponse?.results.orEmpty().forEach { add(TypedResult(it, CatalogMedia.MOVIE)) }
            tvResponse?.results.orEmpty().forEach { add(TypedResult(it, CatalogMedia.TV)) }
        }.sortedByDescending { it.result.popularity ?: 0.0 }
            .mapNotNull { it.result.toPreview(it.media.toContentType()) }
            .distinctBy { "${it.type}:${it.id}" }
        val currentPage = maxOf(movieResponse?.page ?: page, tvResponse?.page ?: page)
        val totalPages = maxOf(movieResponse?.totalPages ?: page, tvResponse?.totalPages ?: page)
        catalogRow(
            id = spec.id,
            name = spec.title,
            type = when (spec.media) {
                SERIES -> ContentType.SERIES
                else -> ContentType.MOVIE
            },
            items = items,
            page = currentPage,
            totalPages = totalPages,
            hideTypeSuffix = spec.media.size > 1,
        )
    }

    private fun TmdbDiscoverResponse.toRow(id: String, name: String, type: ContentType): CatalogRow = catalogRow(
        id = id,
        name = name,
        type = type,
        items = results.orEmpty().mapNotNull { it.toPreview(type) }.distinctBy { it.id },
        page = page ?: 1,
        totalPages = totalPages ?: 1,
    )

    private fun catalogRow(
        id: String,
        name: String,
        type: ContentType,
        items: List<MetaPreview>,
        page: Int,
        totalPages: Int,
        hideTypeSuffix: Boolean = false,
    ): CatalogRow = CatalogRow(
        addonId = "tmdb",
        addonName = "TMDB",
        addonBaseUrl = "https://api.themoviedb.org/3/",
        catalogId = id,
        catalogName = name,
        type = type,
        items = items,
        hasMore = page < totalPages && items.isNotEmpty(),
        currentPage = page,
        supportsSkip = false,
        extraArgs = if (hideTypeSuffix) mapOf("hideTypeSuffix" to "true") else emptyMap(),
    )

    private fun TmdbDiscoverResult.toPreview(type: ContentType): MetaPreview? {
        val displayName = (title ?: name)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val date = if (type == ContentType.SERIES) firstAirDate else releaseDate
        return MetaPreview(
            id = "tmdb:$id",
            type = type,
            rawType = if (type == ContentType.SERIES) "series" else "movie",
            name = displayName,
            poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            posterShape = PosterShape.POSTER,
            background = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            logo = null,
            description = overview,
            releaseInfo = date?.take(4),
            imdbRating = voteAverage?.toFloat(),
            voteCount = voteCount,
            genres = emptyList(),
            released = date,
            alternativeTitles = listOfNotNull(originalTitle, originalName)
                .map(String::trim)
                .filter { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
                .distinct(),
        )
    }

    private fun TmdbListItem.toPreview(): MetaPreview? {
        val type = if (mediaType?.lowercase(Locale.US) == "tv") ContentType.SERIES else ContentType.MOVIE
        val displayName = (title ?: name ?: originalTitle ?: originalName)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val date = if (type == ContentType.SERIES) firstAirDate else releaseDate
        return MetaPreview(
            id = "tmdb:$id",
            type = type,
            rawType = if (type == ContentType.SERIES) "series" else "movie",
            name = displayName,
            poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            posterShape = PosterShape.POSTER,
            background = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            logo = null,
            description = overview,
            releaseInfo = date?.take(4),
            imdbRating = voteAverage?.toFloat(),
            voteCount = voteCount,
            genres = emptyList(),
            released = date,
            alternativeTitles = listOfNotNull(originalTitle, originalName)
                .map(String::trim)
                .filter { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
                .distinct(),
        )
    }

    private fun CatalogMedia.toContentType(): ContentType = when (this) {
        CatalogMedia.MOVIE -> ContentType.MOVIE
        CatalogMedia.TV -> ContentType.SERIES
    }

    private data class TypedResult(
        val result: TmdbDiscoverResult,
        val media: CatalogMedia,
    )
}
