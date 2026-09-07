package com.nuvio.tv.ui.components.posteroptions

import androidx.compose.runtime.Immutable
import com.nuvio.tv.domain.model.MetaPreview

@Immutable
data class PosterOptionsState(
    val target: MetaPreview? = null,
    val addonBaseUrl: String = "",
    val isInLibrary: Boolean = false,
    val isWatched: Boolean = false,
    val isLibraryPending: Boolean = false,
    val isWatchedPending: Boolean = false
)
