package com.nuvio.tv.domain.repository

import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.LiveChannel
import com.nuvio.tv.domain.model.LiveTvSnapshot

/**
 * Repository for live TV channels from the authorized Xtream account.
 */
interface LiveTvRepository {

    /**
     * Loads the live channel snapshot. Throws when there is no cache and the
     * remote request fails; returns the stale cache when a refresh fails.
     */
    suspend fun loadSnapshot(): LiveTvSnapshot

    /** Short EPG for a channel. Returns an empty list on error/absence. */
    suspend fun epg(streamId: Int, limit: Int = 6): List<EpgProgram>

    /** Playback URL for the channel, or null when Xtream is not configured. */
    fun playbackUrl(channel: LiveChannel): String?
}
