package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.xtream.XtreamPlaybackService
import com.nuvio.tv.data.xtream.XtreamResolution
import com.nuvio.tv.data.xtream.resolveTmdbPlaybackId
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Resolves playable streams for a content item.
 *
 * The item's TMDB id is matched against the configured Xtream catalog; any
 * available movies/series/episode groups are returned as the stream sources.
 */
class StreamRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbService: TmdbService,
    private val xtreamPlaybackService: XtreamPlaybackService
) : StreamRepository {

    override fun getStreamsFromAllAddons(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?
    ): Flow<NetworkResult<List<AddonStreams>>> = flow {
        emit(NetworkResult.Loading)

        val xtreamTmdbId = resolveTmdbPlaybackId(
            candidates = listOf(videoId),
            mediaType = type,
            lookup = tmdbService::ensureTmdbId,
        )
        if (xtreamTmdbId == null) {
            emit(NetworkResult.Error(context.getString(R.string.stream_error_coming_soon)))
            return@flow
        }

        val contentType = ContentType.fromString(type)
        when (val resolution = xtreamPlaybackService.resolve(
            tmdbId = xtreamTmdbId,
            contentType = contentType,
            season = season,
            episode = episode
        )) {
            is XtreamResolution.Available ->
                emit(NetworkResult.Success(listOf(resolution.source)))
            XtreamResolution.Unavailable ->
                emit(NetworkResult.Error(context.getString(R.string.stream_error_coming_soon)))
            is XtreamResolution.Failure -> emit(NetworkResult.Error(resolution.message))
        }
    }
}
