package com.nuvio.tv.data.remote.api

import com.nuvio.tv.data.xtream.XtreamTitleMatcher
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApi {
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String,
    ): XtreamAuthResponse

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): List<XtreamVodItem>

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Int,
        @Query("action") action: String = "get_vod_info"
    ): XtreamVodInfoResponse

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): List<XtreamSeriesItem>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Int,
        @Query("action") action: String = "get_series_info"
    ): XtreamSeriesInfoResponse
}

@JsonClass(generateAdapter = true)
data class XtreamAuthResponse(
    @Json(name = "user_info") val userInfo: XtreamUserInfo? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    @Json(name = "auth") val auth: Any? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "exp_date") val expirationDate: Any? = null,
    @Json(name = "active_cons") val activeConnections: Any? = null,
    @Json(name = "max_connections") val maxConnections: Any? = null,
) {
    val isAuthorized: Boolean
        get() = when (auth) {
            is Boolean -> auth
            is Number -> auth.toInt() == 1
            is String -> auth == "1" || auth.equals("true", ignoreCase = true)
            else -> status.equals("active", ignoreCase = true)
        }

    val expirationEpochSeconds: Long?
        get() = expirationDate.asLongOrNull()?.takeIf { it > 0L }

    val activeConnectionCount: Int?
        get() = activeConnections.asLongOrNull()?.toInt()

    val maximumConnectionCount: Int?
        get() = maxConnections.asLongOrNull()?.toInt()
}

private fun Any?.asLongOrNull(): Long? = when (this) {
    is Number -> toLong()
    is String -> trim().toLongOrNull()
    else -> null
}

@JsonClass(generateAdapter = true)
data class XtreamVodItem(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "year") val year: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    val releaseYear: Int? get() = year?.toIntOrNull() ?: XtreamTitleMatcher.extractYear(displayTitle)
}

@JsonClass(generateAdapter = true)
data class XtreamVodInfoResponse(
    @Json(name = "info") val info: XtreamVodInfo? = null,
    @Json(name = "movie_data") val movieData: XtreamMovieData? = null
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfo(
    @Json(name = "tmdb_id") val tmdbId: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamMovieData(
    @Json(name = "stream_id") val streamId: Int? = null,
    @Json(name = "container_extension") val containerExtension: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesItem(
    @Json(name = "series_id") val seriesId: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "year") val year: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "release_date") val releaseDateSnakeCase: String? = null
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    val releaseYear: Int?
        get() = year?.toIntOrNull()
            ?: releaseDate?.take(4)?.toIntOrNull()
            ?: releaseDateSnakeCase?.take(4)?.toIntOrNull()
            ?: XtreamTitleMatcher.extractYear(displayTitle)
}

@JsonClass(generateAdapter = true)
data class XtreamSeriesInfoResponse(
    @Json(name = "episodes") val episodes: Map<String, List<XtreamEpisode>> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class XtreamEpisode(
    @Json(name = "id") val id: String,
    @Json(name = "episode_num") val episodeNumber: Any,
    @Json(name = "season") val season: Any? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    @Json(name = "title") val title: String? = null
) {
    val parsedEpisodeNumber: Int?
        get() = (episodeNumber as? Number)?.toInt() ?: episodeNumber.toString().toIntOrNull()

    val parsedSeason: Int?
        get() = (season as? Number)?.toInt() ?: season?.toString()?.toIntOrNull()
}
