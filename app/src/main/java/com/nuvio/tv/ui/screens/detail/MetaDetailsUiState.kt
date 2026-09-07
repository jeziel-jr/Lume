package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.MetaTrailer
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress

enum class MoreLikeThisSource {
    TMDB
}

enum class PlaybackAvailabilityState {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}

data class MetaDetailsUiState(
    val isLoading: Boolean = true,
    val meta: Meta? = null,
    val error: String? = null,
    val selectedSeason: Int = 1,
    val seasons: List<Int> = emptyList(),
    val episodesForSeason: List<Video> = emptyList(),
    val moviePlaybackAvailability: PlaybackAvailabilityState = PlaybackAvailabilityState.CHECKING,
    val episodePlaybackAvailability: Map<Pair<Int, Int>, PlaybackAvailabilityState> = emptyMap(),
    val isInLibrary: Boolean = false,
    val nextToWatch: NextToWatch? = null,
    val episodeProgressMap: Map<Pair<Int, Int>, WatchProgress> = emptyMap(),
    val trailerUrl: String? = null,
    val trailerAudioUrl: String? = null,
    val isTrailerPlaying: Boolean = false,
    val isTrailerLoading: Boolean = false,
    val showTrailerControls: Boolean = false,
    val hideLogoDuringTrailer: Boolean = false,
    val isSharedTrailerOverlayVisible: Boolean = false,
    val isSharedTrailerLoading: Boolean = false,
    val sharedTrailerUrl: String? = null,
    val sharedTrailerAudioUrl: String? = null,
    val sharedTrailerErrorMessage: String? = null,
    val selectedSharedTrailer: MetaTrailer? = null,
    val trailerButtonEnabled: Boolean = false,
    val librarySourceMode: LibrarySourceMode = LibrarySourceMode.LOCAL,
    val isInWatchlist: Boolean = false,
    val showListPicker: Boolean = false,
    val pickerMembership: Map<String, Boolean> = emptyMap(),
    val pickerPending: Boolean = false,
    val pickerError: String? = null,
    val isMovieWatched: Boolean = false,
    val isMovieWatchedPending: Boolean = false,
    val watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    val episodeWatchedPendingKeys: Set<String> = emptySet(),
    val blurUnwatchedEpisodes: Boolean = false,
    val showFullReleaseDate: Boolean = true,
    val moreLikeThis: List<MetaPreview> = emptyList(),
    val moreLikeThisSource: MoreLikeThisSource? = null,
    val collection: List<MetaPreview> = emptyList(),
    val collectionName: String? = null,
    val tmdbRating: Float? = null,
    val userMessage: String? = null,
    val userMessageIsError: Boolean = false
)

sealed class MetaDetailsEvent {
    data class OnSeasonSelected(val season: Int) : MetaDetailsEvent()
    data class OnEpisodeClick(val video: Video) : MetaDetailsEvent()
    data object OnPlayClick : MetaDetailsEvent()
    data object OnToggleLibrary : MetaDetailsEvent()
    data object OnRetry : MetaDetailsEvent()
    data object OnBackPress : MetaDetailsEvent()
    data object OnUserInteraction : MetaDetailsEvent()
    data object OnPlayButtonFocused : MetaDetailsEvent()
    data object OnTrailerButtonClick : MetaDetailsEvent()
    data object OnTrailerEnded : MetaDetailsEvent()
    data class OnSharedTrailerSelected(val trailer: MetaTrailer) : MetaDetailsEvent()
    data object OnDismissSharedTrailer : MetaDetailsEvent()
    data object OnRetrySharedTrailer : MetaDetailsEvent()
    data object OnToggleMovieWatched : MetaDetailsEvent()
    data class OnToggleEpisodeWatched(val video: Video) : MetaDetailsEvent()
    data class OnMarkSeasonWatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkSeasonUnwatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkPreviousEpisodesWatched(val video: Video) : MetaDetailsEvent()
    data class OnMarkPreviousSeasonsWatched(val season: Int) : MetaDetailsEvent()
    data object OnLibraryLongPress : MetaDetailsEvent()
    data class OnPickerMembershipToggled(val listKey: String) : MetaDetailsEvent()
    data object OnPickerSave : MetaDetailsEvent()
    data object OnPickerDismiss : MetaDetailsEvent()
    data object OnClearMessage : MetaDetailsEvent()
    data object OnLifecyclePause : MetaDetailsEvent()
}
