package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.components.PinDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun ParentalControlSettingsContent(
    initialFocusRequester: FocusRequester?,
    viewModel: ParentalControlViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showForgotConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.parental_title),
            subtitle = stringResource(R.string.parental_subtitle),
        )
        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SettingsActionRow(
                    title = stringResource(R.string.parental_pin_status),
                    subtitle = stringResource(R.string.parental_title),
                    value = if (state.pinSet) {
                        stringResource(R.string.parental_pin_set)
                    } else {
                        stringResource(R.string.parental_pin_not_set)
                    },
                    onClick = viewModel::openPinEntry,
                    modifier = initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                )
                SettingsToggleRow(
                    title = stringResource(R.string.parental_show_adult),
                    subtitle = stringResource(R.string.parental_show_adult_subtitle),
                    checked = !state.adultContentHidden,
                    onToggle = { viewModel.setShowAdult(state.adultContentHidden) },
                )
                if (state.pinSet) {
                    SettingsActionRow(
                        title = stringResource(R.string.parental_forgot_pin),
                        subtitle = stringResource(R.string.parental_forgot_pin_desc),
                        onClick = { showForgotConfirm = true },
                    )
                }
            }
        }
    }

    state.dialog?.let { mode ->
        val title = when (mode) {
            PinDialogMode.DEFINE -> stringResource(R.string.parental_pin_define_title)
            PinDialogMode.CHANGE_CURRENT -> stringResource(R.string.parental_pin_confirm_title)
            PinDialogMode.CHANGE_NEW -> stringResource(R.string.parental_pin_define_title)
            PinDialogMode.SHOW_ADULT -> stringResource(R.string.parental_pin_confirm_title)
        }
        val subtitle = stringResource(R.string.parental_show_adult_subtitle)
        PinDialog(
            title = title,
            subtitle = subtitle,
            error = state.dialogErrorRes?.let { stringResource(it) },
            onDismiss = viewModel::onPinDismiss,
            onConfirm = viewModel::onPinConfirm
        )
    }

    if (showForgotConfirm) {
        NuvioDialog(
            onDismiss = { showForgotConfirm = false },
            title = stringResource(R.string.parental_reset_confirm_title),
            subtitle = stringResource(R.string.parental_forgot_pin_desc),
            width = 460.dp,
            suppressFirstKeyUp = false,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showForgotConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = {
                    showForgotConfirm = false
                    viewModel.forgotPin()
                }) {
                    Text(stringResource(R.string.parental_forgot_pin))
                }
            }
        }
    }
}
