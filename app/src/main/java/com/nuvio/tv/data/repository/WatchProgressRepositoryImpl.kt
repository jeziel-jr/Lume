package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.core.tmdb.TmdbService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device watch progress backed by [WatchProgressPreferences] /
 * [WatchedItemsPreferences], per profile.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressRepositoryImpl @Inject constructor(
    private val watchProgressPreferences: WatchProgressPreferences,
    private val layoutPreferenceDataStore: com.nuvio.tv.data.local.LayoutPreferenceDataStore,
    private val watchedItemsPreferences: WatchedItemsPreferences,
    private val tmdbService: TmdbService,
    private val profileManager: com.nuvio.tv.core.profile.ProfileManager,
) : WatchProgressRepository {
    companion object {
        private const val TAG = "WatchProgressRepo"
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hydratedProgressIds = mutableSetOf<String>()

    private val optimisticContinueWatchingUpdates = MutableSharedFlow<WatchProgress>(
        replay = 1,
        extraBufferCapacity = 16
    )
    private val metadataMutex = Mutex()
    private val inFlightArtworkKeys = mutableSetOf<String>()
    private val artworkHydrationLimit = 10

    override fun observeRemoteProgressLoaded(): Flow<Boolean> = flowOf(true)

    override val allProgress: Flow<List<WatchProgress>>
        get() = watchProgressPreferences.allProgress
            .onEach { items ->
                val needsArtwork = items.filter {
                    it.poster == null && it.backdrop == null && it.contentId !in hydratedProgressIds
                }
                if (needsArtwork.isNotEmpty()) {
                    syncScope.launch { hydrateProgressArtwork(needsArtwork) }
                }
            }

    override val continueWatching: Flow<List<WatchProgress>>
        get() = allProgress.map { list -> list.filter { it.isInProgress() } }

    override fun getProgress(contentId: String): Flow<WatchProgress?> {
        return watchProgressPreferences.getProgress(contentId)
    }

    override fun getEpisodeProgress(contentId: String, season: Int, episode: Int): Flow<WatchProgress?> {
        return watchProgressPreferences.getEpisodeProgress(contentId, season, episode)
    }

    override fun getAllEpisodeProgress(contentId: String): Flow<Map<Pair<Int, Int>, WatchProgress>> {
        return watchProgressPreferences.getAllEpisodeProgress(contentId)
    }

    override fun getAiredEpisodeOrder(contentId: String): Flow<List<Pair<Int, Int>>> {
        return flowOf(emptyList())
    }

    override fun observeNextUpSeeds(): Flow<List<WatchProgress>> {
        // Use watched items (fully synced with pagination) to build seeds
        // instead of watch progress (limited to 1000 entries).
        return combine(
            watchedItemsPreferences.allItems,
            layoutPreferenceDataStore.nextUpFromFurthestEpisode
        ) { items, useFurthest ->
            items
                .filter { item ->
                    (item.contentType.equals("series", ignoreCase = true) ||
                        item.contentType.equals("tv", ignoreCase = true)) &&
                        item.season != null &&
                        item.episode != null &&
                        item.season != 0 &&
                        !isMalformedNextUpSeedContentId(item.contentId)
                }
                .groupBy { it.contentId }
                .mapNotNull { (_, episodes) ->
                    val latest = episodes.maxWithOrNull(
                        if (useFurthest) {
                            compareBy<WatchedItem> { it.season ?: 0 }
                                .thenBy { it.episode ?: 0 }
                                .thenBy { it.watchedAt }
                        } else {
                            compareBy<WatchedItem> { it.watchedAt }
                                .thenBy { it.season ?: 0 }
                                .thenBy { it.episode ?: 0 }
                        }
                    ) ?: return@mapNotNull null
                    WatchProgress(
                        contentId = latest.contentId,
                        contentType = latest.contentType,
                        name = latest.title,
                        poster = null,
                        backdrop = null,
                        logo = null,
                        videoId = latest.contentId,
                        season = latest.season,
                        episode = latest.episode,
                        episodeTitle = null,
                        position = 1L,
                        duration = 1L,
                        lastWatched = latest.watchedAt,
                        progressPercent = 100f
                    )
                }
        }.distinctUntilChanged()
    }

    private fun isMalformedNextUpSeedContentId(contentId: String?): Boolean {
        val trimmed = contentId?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        val lowered = trimmed.lowercase()
        return lowered == "tmdb" ||
            lowered == "imdb" ||
            lowered == "tmdb:" ||
            lowered == "imdb:"
    }

    override fun observeOptimisticContinueWatchingUpdates(): Flow<WatchProgress> {
        return optimisticContinueWatchingUpdates
    }

    override suspend fun remapEpisodeSeed(progress: WatchProgress): WatchProgress {
        return progress
    }

    override fun observeWatchedMovieIds(): Flow<Set<String>> {
        return combine(
            watchProgressPreferences.allProgress,
            watchedItemsPreferences.allItems
        ) { progressList, watchedItems ->
            val completedIds = mutableSetOf<String>()
            val replayingIds = mutableSetOf<String>()
            for (progress in progressList) {
                if (progress.isCompleted()) {
                    completedIds.add(progress.contentId)
                } else if (progress.position > 0L ||
                    progress.progressPercent?.let { it > 0f } == true
                ) {
                    replayingIds.add(progress.contentId)
                }
            }
            val watchedItemIds = watchedItems
                .filter { it.season == null && it.episode == null }
                .map { it.contentId }
                .toSet()
            (completedIds + watchedItemIds) - replayingIds
        }.debounce(500)
            .distinctUntilChanged()
    }

    override suspend fun getWatchedShowEpisodes(): Map<String, Set<Pair<Int, Int>>> {
        return watchedItemsPreferences.allItems.first()
            .filter { it.season != null && it.episode != null }
            .groupBy { it.contentId }
            .mapValues { (_, items) ->
                items.map { it.season!! to it.episode!! }.toSet()
            }
    }

    override suspend fun getShowIdSiblings(): Map<String, Set<String>> = emptyMap()

    override fun isWatched(contentId: String, videoId: String?, season: Int?, episode: Int?): Flow<Boolean> {
        val progressFlow = if (season != null && episode != null) {
            watchProgressPreferences.getEpisodeProgress(contentId, season, episode)
        } else {
            watchProgressPreferences.getProgress(contentId)
        }
        return combine(
            progressFlow,
            watchedItemsPreferences.isWatched(contentId, season, episode)
        ) { progressEntry, itemWatched ->
            val hasStartedReplay = progressEntry?.let { entry ->
                !entry.isCompleted() &&
                    (entry.position > 0L || entry.progressPercent?.let { it > 0f } == true)
            } == true

            if (hasStartedReplay) {
                false
            } else {
                (progressEntry?.isCompleted() == true) || itemWatched
            }
        }
    }

    override suspend fun saveProgress(progress: WatchProgress, syncRemote: Boolean) {
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.saveProgress(progress, profileId = profileId)

        if (progress.isCompleted()) {
            val watchedItem = progress.toWatchedItem()
            watchedItemsPreferences.markAsWatched(watchedItem, profileId = profileId)
        }
    }

    override suspend fun saveProgressBatch(progressList: List<WatchProgress>, syncRemote: Boolean) {
        if (progressList.isEmpty()) return
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.saveProgressBatch(progressList, profileId = profileId)

        val completedWatchedItems = progressList
            .filter { it.isCompleted() }
            .map { progress -> progress.toWatchedItem() }
        if (completedWatchedItems.isNotEmpty()) {
            watchedItemsPreferences.markAsWatchedBatch(completedWatchedItems, profileId = profileId)
        }
    }

    override suspend fun removeProgress(contentId: String, season: Int?, episode: Int?) {
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.removeProgress(contentId, season, episode)
    }

    override suspend fun removeFromHistory(contentId: String, videoId: String?, season: Int?, episode: Int?) {
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.removeProgress(contentId, season, episode)
        watchedItemsPreferences.unmarkAsWatched(contentId, season, episode, profileId = profileId)
    }

    override suspend fun removeFromHistoryBatch(
        contentId: String,
        videoId: String?,
        episodes: List<Pair<Int, Int>>
    ) {
        if (episodes.isEmpty()) return
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.removeProgressBatch(contentId, episodes)
        watchedItemsPreferences.unmarkAsWatchedBatch(contentId, episodes, profileId = profileId)
    }

    override suspend fun markAsCompleted(progress: WatchProgress, syncRemoteToTrakt: Boolean) {
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.markAsCompleted(progress, profileId = profileId)
        val watchedItem = progress.toWatchedItem()
        watchedItemsPreferences.markAsWatched(watchedItem, profileId = profileId)
    }

    override suspend fun markAsCompletedBatch(progressList: List<WatchProgress>) {
        if (progressList.isEmpty()) return
        val profileId = profileManager.activeProfileId.value
        watchProgressPreferences.markAsCompletedBatch(progressList, profileId = profileId)
        val watchedItems = progressList.map { progress ->
            progress.toWatchedItem()
        }
        watchedItemsPreferences.markAsWatchedBatch(watchedItems, profileId = profileId)
    }

    private fun WatchProgress.toWatchedItem(watchedAt: Long = System.currentTimeMillis()): WatchedItem =
        WatchedItem(
            contentId = contentId,
            contentType = contentType,
            title = name,
            season = season,
            episode = episode,
            watchedAt = watchedAt
        )

    override suspend fun clearAll() {
        watchProgressPreferences.clearAll()
    }

    override fun isDroppedShow(contentId: String): Boolean = false

    override suspend fun isTraktProgressActive(): Boolean = false

    private suspend fun hydrateProgressArtwork(items: List<WatchProgress>) {
        items.take(artworkHydrationLimit).forEach { progress ->
            val shouldFetch = metadataMutex.withLock {
                if (hydratedProgressIds.contains(progress.contentId)) return@withLock false
                if (inFlightArtworkKeys.contains(progress.contentId)) return@withLock false
                inFlightArtworkKeys.add(progress.contentId)
                true
            }
            if (!shouldFetch) return@forEach
            runCatching {
                if (progress.poster == null || progress.backdrop == null) {
                    val tmdbImages = tmdbService.fetchImdbImages(progress.contentId, progress.contentType)
                    val backdropToSave = progress.backdrop ?: tmdbImages?.backdropUrl
                    val posterToSave = progress.poster ?: tmdbImages?.posterUrl
                    if (backdropToSave != null || posterToSave != null) {
                        watchProgressPreferences.saveProgress(
                            progress.copy(
                                poster = posterToSave,
                                backdrop = backdropToSave
                            )
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Progress artwork hydration failed for ${progress.contentId}", it) }
            hydratedProgressIds.add(progress.contentId)
            metadataMutex.withLock {
                inFlightArtworkKeys.remove(progress.contentId)
            }
        }
    }
}
