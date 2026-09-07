package com.nuvio.tv.ui.components.posteroptions

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterOptionsDialog(
    title: String,
    isInLibrary: Boolean,
    isLibraryPending: Boolean,
    isMovie: Boolean,
    isSeries: Boolean = false,
    isWatched: Boolean,
    isWatchedPending: Boolean,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        primaryFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = stringResource(R.string.home_poster_dialog_subtitle)
    ) {
        Button(
            onClick = onDetails,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(stringResource(R.string.cw_action_go_to_details))
        }

        Button(
            onClick = onToggleLibrary,
            enabled = !isLibraryPending,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(
                if (isInLibrary) {
                    stringResource(R.string.hero_remove_from_library)
                } else {
                    stringResource(R.string.hero_add_to_library)
                }
            )
        }

        if (isMovie || isSeries) {
            Button(
                onClick = onToggleWatched,
                enabled = !isWatchedPending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(
                    if (isWatched) {
                        stringResource(R.string.hero_mark_unwatched)
                    } else {
                        stringResource(R.string.hero_mark_watched)
                    }
                )
            }
        }
    }
}

@Composable
fun PosterOptionsHost(
    state: PosterOptionsState,
    controller: PosterOptionsController,
    onNavigateToDetail: (id: String, type: String, addonBaseUrl: String) -> Unit
) {
    val target = state.target
    if (target != null) {
        val isMovie = target.apiType.equals("movie", ignoreCase = true)
        val isSeries = target.apiType.equals("series", ignoreCase = true) ||
            target.apiType.equals("tv", ignoreCase = true) ||
            target.apiType.equals("anime", ignoreCase = true)
        PosterOptionsDialog(
            title = target.name,
            isInLibrary = state.isInLibrary,
            isLibraryPending = state.isLibraryPending,
            isMovie = isMovie,
            isSeries = isSeries,
            isWatched = state.isWatched,
            isWatchedPending = state.isWatchedPending,
            onDismiss = { controller.dismiss() },
            onDetails = {
                onNavigateToDetail(target.id, target.apiType, state.addonBaseUrl)
                controller.dismiss()
            },
            onToggleLibrary = {
                controller.toggleLibrary()
                controller.dismiss()
            },
            onToggleWatched = {
                if (isMovie) {
                    controller.toggleMovieWatched()
                } else {
                    controller.toggleSeriesWatched()
                }
                controller.dismiss()
            }
        )
    }
}
