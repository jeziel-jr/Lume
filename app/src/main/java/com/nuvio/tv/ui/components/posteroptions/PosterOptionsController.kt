package com.nuvio.tv.ui.components.posteroptions

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reusable controller for the long-press / hold-down poster options menu.
 *
 * Inject into a ViewModel via Hilt and call [bind] from the VM's init with
 * `viewModelScope`. The screen renders [PosterOptionsHost] with [state] and
 * wires [show] to each card's `onLongPress`.
 */
class PosterOptionsController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val watchedSeriesStateHolder: WatchedSeriesStateHolder,
    private val tmdbService: TmdbService
) {
    private val _state = MutableStateFlow(PosterOptionsState())
    val state: StateFlow<PosterOptionsState> = _state.asStateFlow()

    private val targetFlow = MutableStateFlow<MetaPreview?>(null)

    private var scope: CoroutineScope? = null
    private var bound = false
    private var showJob: kotlinx.coroutines.Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(scope: CoroutineScope) {
        if (bound) return
        bound = true
        this.scope = scope

        targetFlow
            .flatMapLatest { item ->
                if (item == null) {
                    flowOf(false to false)
                } else {
                    val isSeries = item.apiType.equals("series", ignoreCase = true) ||
                        item.apiType.equals("tv", ignoreCase = true) ||
                        item.apiType.equals("anime", ignoreCase = true)
                    if (isSeries) {
                        combine(
                            libraryRepository.isInLibrary(item.id, item.apiType),
                            watchedSeriesStateHolder.fullyWatchedSeriesIds
                        ) { lib, watchedIds -> lib to (item.id in watchedIds) }
                    } else {
                        combine(
                            libraryRepository.isInLibrary(item.id, item.apiType),
                            watchProgressRepository.isWatched(item.id, videoId = item.imdbId)
                        ) { lib, watched -> lib to watched }
                    }
                }
            }
            .onEach { (isInLibrary, isWatched) ->
                _state.update { current ->
                    current.copy(
                        isInLibrary = isInLibrary,
                        isWatched = isWatched
                    )
                }
            }
            .launchIn(scope)
    }

    fun show(item: MetaPreview, addonBaseUrl: String?) {
        val launchScope = this.scope ?: return
        showJob?.cancel()
        showJob = launchScope.launch {
            val canonical = canonicalize(item)
            val isSeries = canonical.apiType.equals("series", ignoreCase = true) ||
                canonical.apiType.equals("tv", ignoreCase = true) ||
                canonical.apiType.equals("anime", ignoreCase = true)
            val initialIsInLibrary = runCatching {
                libraryRepository.isInLibrary(canonical.id, canonical.apiType).first()
            }.getOrDefault(false)
            val initialIsWatched = if (isSeries) {
                canonical.id in watchedSeriesStateHolder.fullyWatchedSeriesIds.value
            } else {
                runCatching {
                    watchProgressRepository.isWatched(canonical.id, videoId = canonical.imdbId).first()
                }.getOrDefault(false)
            }

            _state.update { current ->
                current.copy(
                    target = canonical,
                    addonBaseUrl = addonBaseUrl.orEmpty(),
                    isInLibrary = initialIsInLibrary,
                    isWatched = initialIsWatched,
                    isLibraryPending = false,
                    isWatchedPending = false
                )
            }
            targetFlow.value = canonical
        }
    }

    private suspend fun canonicalize(item: MetaPreview): MetaPreview {
        if (item.id.startsWith("tt", ignoreCase = false)) return item
        val tmdbNumber = contentTmdbId(item.id) ?: item.id.toIntOrNull() ?: return item
        val mediaType = if (item.apiType.equals("series", ignoreCase = true) ||
            item.apiType.equals("tv", ignoreCase = true) ||
            item.apiType.equals("anime", ignoreCase = true)
        ) "tv" else "movie"
        val imdb = runCatching { tmdbService.tmdbToImdb(tmdbNumber, mediaType) }.getOrNull()
        return if (!imdb.isNullOrBlank()) item.copy(id = imdb) else item
    }

    private suspend fun ensureCanonical(): MetaPreview? {
        val current = _state.value.target ?: return null
        val canonical = canonicalize(current)
        if (canonical.id != current.id && _state.value.target?.id == current.id) {
            _state.update { it.copy(target = canonical) }
            targetFlow.value = canonical
        }
        return canonical
    }

    fun dismiss() {
        showJob?.cancel()
        showJob = null
        targetFlow.value = null
        _state.update { it.copy(target = null) }
    }

    fun toggleLibrary() {
        val state = _state.value
        if (state.target == null) return
        if (state.isLibraryPending) return
        val scope = this.scope ?: return

        _state.update { it.copy(isLibraryPending = true) }
        scope.launch {
            val canonical = ensureCanonical() ?: return@launch
            runCatching {
                libraryRepository.toggleDefault(
                    canonical.toLibraryEntryInput(state.addonBaseUrl.takeIf { it.isNotBlank() })
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to toggle library for ${canonical.id}: ${error.message}")
            }
            _state.update { it.copy(isLibraryPending = false) }
        }
    }

    fun toggleMovieWatched() {
        val state = _state.value
        val item = state.target ?: return
        if (!item.apiType.equals("movie", ignoreCase = true)) return
        if (state.isWatchedPending) return
        val scope = this.scope ?: return

        _state.update { it.copy(isWatchedPending = true) }
        scope.launch {
            val canonical = ensureCanonical() ?: return@launch
            val currentlyWatched = _state.value.isWatched
            runCatching {
                if (currentlyWatched) {
                    watchProgressRepository.removeFromHistory(canonical.id, videoId = canonical.imdbId)
                } else {
                    watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(canonical))
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to toggle watched for ${canonical.id}: ${error.message}")
            }
            _state.update { it.copy(isWatchedPending = false) }
        }
    }

    fun toggleSeriesWatched() {
        val state = _state.value
        val item = state.target ?: return
        val isSeries = item.apiType.equals("series", ignoreCase = true) ||
            item.apiType.equals("tv", ignoreCase = true) ||
            item.apiType.equals("anime", ignoreCase = true)
        if (!isSeries) return
        if (state.isWatchedPending) return
        val scope = this.scope ?: return

        val currentlyWatched = state.isWatched

        // Optimistic UI update — badge changes immediately
        val currentIds = watchedSeriesStateHolder.fullyWatchedSeriesIds.value
        val optimisticIds = if (currentlyWatched) currentIds - item.id else currentIds + item.id
        watchedSeriesStateHolder.update(optimisticIds)

        _state.update { it.copy(isWatchedPending = true) }
        scope.launch {
            val canonical = ensureCanonical() ?: return@launch
            runCatching {
                if (currentlyWatched) {
                    unmarkSeriesWatched(canonical)
                } else {
                    markSeriesWatched(canonical)
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to toggle series watched for ${canonical.id}: ${error.message}")
                // Revert optimistic update on failure
                watchedSeriesStateHolder.update(currentIds)
            }
            _state.update { it.copy(isWatchedPending = false) }
        }
    }

    private suspend fun markSeriesWatched(item: MetaPreview) {
        watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(item))
    }

    private suspend fun unmarkSeriesWatched(item: MetaPreview) {
        watchProgressRepository.removeFromHistory(item.id, videoId = item.imdbId)
    }

    companion object {
        private const val TAG = "PosterOptionsCtrl"
    }
}

private fun buildCompletedMovieProgress(item: MetaPreview): WatchProgress {
    return WatchProgress(
        contentId = item.id,
        contentType = item.apiType,
        name = item.name,
        poster = item.poster,
        backdrop = item.backdropUrl,
        logo = item.logo,
        videoId = item.id,
        season = null,
        episode = null,
        episodeTitle = null,
        position = 1L,
        duration = 1L,
        lastWatched = System.currentTimeMillis(),
        progressPercent = 100f
    )
}

private fun MetaPreview.toLibraryEntryInput(addonBaseUrl: String?): LibraryEntryInput {
    val year = Regex("(\\d{4})").find(releaseInfo ?: "")
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val (imdbId, tmdbId) = contentIdsOf(id)
    // The library renders as portrait. If the source MetaPreview was built for landscape
    // display (e.g. TMDB collection / more-like-this), its `poster` field holds the backdrop.
    // Prefer `rawPosterUrl` (the proper portrait) when available so the saved entry isn't a
    // stretched/cropped landscape inside a portrait card.
    val isLandscapeSource = posterShape == com.nuvio.tv.domain.model.PosterShape.LANDSCAPE
    val portraitPoster = rawPosterUrl?.takeIf { it.isNotBlank() }
    val savedPoster = if (isLandscapeSource && portraitPoster != null) portraitPoster else poster
    val savedShape = if (isLandscapeSource) com.nuvio.tv.domain.model.PosterShape.POSTER else posterShape
    return LibraryEntryInput(
        itemId = id,
        itemType = apiType,
        title = name,
        year = year,
        imdbId = imdbId,
        tmdbId = tmdbId,
        poster = savedPoster,
        posterShape = savedShape,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
        addonBaseUrl = addonBaseUrl
    )
}

/** Returns the TMDB id embedded in a `tmdb:<id>` style content id, or null. */
private fun contentTmdbId(contentId: String): Int? {
    val raw = contentId.trim()
    return if (raw.startsWith("tmdb:", ignoreCase = true)) {
        raw.substringAfter(':').toIntOrNull()
    } else {
        null
    }
}

/** Extracts (imdbId, tmdbId) from a raw content id, mirroring the id shapes used across the app. */
private fun contentIdsOf(contentId: String): Pair<String?, Int?> {
    val raw = contentId.trim()
    val imdb = raw.takeIf { it.startsWith("tt", ignoreCase = false) }?.substringBefore(':')
    val tmdb = if (raw.startsWith("tmdb:", ignoreCase = true)) raw.substringAfter(':').toIntOrNull() else null
    return imdb to tmdb
}
