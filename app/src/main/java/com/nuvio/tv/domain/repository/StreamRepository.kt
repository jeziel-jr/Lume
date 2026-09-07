package com.nuvio.tv.domain.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.AddonStreams
import kotlinx.coroutines.flow.Flow

interface StreamRepository {
    /**
     * Resolves the available playable stream groups (Xtream etc.) for a content id.
     * @param type The content type (movie, series, etc.)
     * @param videoId The content id (for series: TMDB/IMDB id with season:episode suffix)
     * @param season Optional season number for TV shows
     * @param episode Optional episode number for TV shows
     * @return Flow of stream groups emitted as they resolve
     */
    fun getStreamsFromAllAddons(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null
    ): Flow<NetworkResult<List<AddonStreams>>>
}
