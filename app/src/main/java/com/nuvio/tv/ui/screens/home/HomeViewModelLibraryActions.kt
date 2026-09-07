package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.WatchProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun HomeViewModel.refreshPosterLibraryStatus(item: MetaPreview) {
    val statusKey = homeItemStatusKey(item.id, item.apiType)
    viewModelScope.launch {
        runCatching {
            val isInLibrary = libraryRepository.isInLibrary(item.id, item.apiType).first()
            _uiState.update { state ->
                if (state.posterLibraryMembership[statusKey] == isInLibrary) state
                else state.copy(
                    posterLibraryMembership = state.posterLibraryMembership + (statusKey to isInLibrary)
                )
            }
        }
    }
}

fun HomeViewModel.togglePosterLibrary(item: MetaPreview, addonBaseUrl: String?) {
    val statusKey = homeItemStatusKey(item.id, item.apiType)
    if (statusKey in _uiState.value.posterLibraryPending) return

    _uiState.update { state ->
        state.copy(posterLibraryPending = state.posterLibraryPending + statusKey)
    }

    viewModelScope.launch {
        runCatching {
            libraryRepository.toggleDefault(item.toLibraryEntryInput(addonBaseUrl))
        }.onFailure { error ->
            Log.w(HomeViewModel.TAG, "Failed to toggle poster library for ${item.id}: ${error.message}")
        }
        runCatching {
            val isNowInLibrary = libraryRepository.isInLibrary(item.id, item.apiType).first()
            _uiState.update { state ->
                state.copy(
                    posterLibraryMembership = state.posterLibraryMembership + (statusKey to isNowInLibrary)
                )
            }
        }
        _uiState.update { state ->
            state.copy(posterLibraryPending = state.posterLibraryPending - statusKey)
        }
    }
}

fun HomeViewModel.togglePosterMovieWatched(item: MetaPreview) {
    if (!item.apiType.equals("movie", ignoreCase = true)) return
    val statusKey = homeItemStatusKey(item.id, item.apiType)
    if (statusKey in _uiState.value.movieWatchedPending) return

    _uiState.update { state ->
        state.copy(movieWatchedPending = state.movieWatchedPending + statusKey)
    }

    viewModelScope.launch {
        val currentlyWatched = _uiState.value.movieWatchedStatus[statusKey] == true
        runCatching {
            if (currentlyWatched) {
                watchProgressRepository.removeFromHistory(item.id, videoId = item.imdbId)
            } else {
                watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(item))
            }
        }.onFailure { error ->
            Log.w(HomeViewModel.TAG, "Failed to toggle poster watched status for ${item.id}: ${error.message}")
        }
        _uiState.update { state ->
            state.copy(movieWatchedPending = state.movieWatchedPending - statusKey)
        }
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

fun HomeViewModel.togglePosterSeriesWatched(item: MetaPreview) {
    val isSeries = item.apiType.equals("series", ignoreCase = true) ||
        item.apiType.equals("tv", ignoreCase = true)
    if (!isSeries) return
    val statusKey = homeItemStatusKey(item.id, item.apiType)
    if (statusKey in _uiState.value.movieWatchedPending) return

    val currentlyWatched = _uiState.value.movieWatchedStatus[statusKey] == true

    // Optimistically update the UI immediately
    _uiState.update { state ->
        state.copy(
            movieWatchedPending = state.movieWatchedPending + statusKey,
            movieWatchedStatus = state.movieWatchedStatus + (statusKey to !currentlyWatched)
        )
    }

    viewModelScope.launch {
        runCatching {
            if (currentlyWatched) {
                unmarkSeriesWatched(item)
            } else {
                markSeriesWatched(item)
            }
        }.onFailure { error ->
            Log.w(HomeViewModel.TAG, "Failed to toggle series watched for ${item.id}: ${error.message}")
            // Revert optimistic update on failure
            _uiState.update { state ->
                state.copy(
                    movieWatchedStatus = state.movieWatchedStatus + (statusKey to currentlyWatched)
                )
            }
        }
        _uiState.update { state ->
            state.copy(movieWatchedPending = state.movieWatchedPending - statusKey)
        }
    }
}

private suspend fun HomeViewModel.markSeriesWatched(item: MetaPreview) {
    val episodes = fetchSeriesEpisodes(item)
    if (episodes.isEmpty()) {
        watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(item))
        return
    }

    val progressList = episodes.map { video ->
        WatchProgress(
            contentId = item.id,
            contentType = item.apiType,
            name = item.name,
            poster = item.poster,
            backdrop = item.backdropUrl,
            logo = item.logo,
            videoId = video.id,
            season = video.season,
            episode = video.episode,
            episodeTitle = video.title,
            position = 1L,
            duration = 1L,
            lastWatched = System.currentTimeMillis(),
            progressPercent = 100f
        )
    }
    watchProgressRepository.markAsCompletedBatch(progressList)
    fullyWatchedSeriesIds.updateWithValidation(
        fullyWatchedSeriesIds.fullyWatchedSeriesIds.value + item.id,
        setOf(item.id)
    )
}

private suspend fun HomeViewModel.unmarkSeriesWatched(item: MetaPreview) {
    val episodes = fetchSeriesEpisodes(item)
    if (episodes.isEmpty()) {
        watchProgressRepository.removeFromHistory(item.id, videoId = item.imdbId)
        return
    }

    val episodePairs = episodes.map { it.season!! to it.episode!! }
    watchProgressRepository.removeFromHistoryBatch(
        contentId = item.id,
        videoId = item.imdbId,
        episodes = episodePairs
    )
    fullyWatchedSeriesIds.updateWithValidation(
        fullyWatchedSeriesIds.fullyWatchedSeriesIds.value - item.id,
        setOf(item.id)
    )
}

private suspend fun HomeViewModel.fetchSeriesEpisodes(item: MetaPreview): List<com.nuvio.tv.domain.model.Video> {
    val type = if (item.apiType.equals("tv", ignoreCase = true)) "series" else item.apiType
    val contentType = ContentType.fromString(type)
    if (contentType != ContentType.SERIES && contentType != ContentType.TV) return emptyList()
    val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, type) }.getOrNull()
        ?: return emptyList()
    val today = java.time.LocalDate.now()
    return runCatching {
        tmdbMetadataService.fetchSeriesVideos(tmdbId, currentTmdbSettings.language)
    }.getOrDefault(emptyList())
        .filter { video -> video.season != null && video.episode != null && (video.season ?: 0) > 0 }
        .filter { video ->
            if (video.available == false) return@filter false
            val released = video.released?.substringBefore('T')?.trim()
            if (released.isNullOrBlank()) return@filter true
            val isFuture = runCatching {
                java.time.LocalDate.parse(released, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE).isAfter(today)
            }.getOrDefault(false)
            !isFuture
        }
}

private fun MetaPreview.toLibraryEntryInput(addonBaseUrl: String?): LibraryEntryInput {
    val year = Regex("(\\d{4})").find(releaseInfo ?: "")
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val raw = id.trim()
    val imdbId = raw.takeIf { it.startsWith("tt", ignoreCase = false) }?.substringBefore(':')
    val tmdbId = if (raw.startsWith("tmdb:", ignoreCase = true)) raw.substringAfter(':').toIntOrNull() else null
    return LibraryEntryInput(
        itemId = id,
        itemType = apiType,
        title = name,
        year = year,
        imdbId = imdbId,
        tmdbId = tmdbId,
        poster = poster,
        posterShape = posterShape,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
        addonBaseUrl = addonBaseUrl
    )
}
