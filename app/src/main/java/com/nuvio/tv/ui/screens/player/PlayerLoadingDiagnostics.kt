package com.nuvio.tv.ui.screens.player

import kotlinx.coroutines.flow.update

/**
 * Updates the loading overlay UI (message/progress/visibility).
 * Kept intentionally small: the report/telemetry pipeline that consumed
 * per-phase diagnostic events was removed.
 */
internal fun PlayerRuntimeController.setLoadingStatus(
    message: String?,
    progress: Float? = null,
    showOverlay: Boolean? = null
) {
    _uiState.update { state ->
        state.copy(
            showLoadingOverlay = showOverlay ?: state.showLoadingOverlay,
            loadingMessage = message,
            loadingProgress = progress
        )
    }
}
