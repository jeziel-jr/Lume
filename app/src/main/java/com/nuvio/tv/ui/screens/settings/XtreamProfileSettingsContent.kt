@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.xtream.XtreamComponentHealth
import com.nuvio.tv.data.xtream.XtreamHealthReason
import com.nuvio.tv.data.xtream.XtreamHealthState
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun XtreamProfileSettingsContent(
    initialFocusRequester: FocusRequester?,
    viewModel: XtreamProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val credentials = state.credentials
    val info = state.accountInfo

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.xtream_profile_title),
            subtitle = stringResource(R.string.xtream_profile_subtitle),
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
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_user),
                    info?.username ?: credentials?.username.orEmpty(),
                )
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_status),
                    localizedStatus(info?.status),
                )
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_expiration),
                    formatExpiration(info?.expirationEpochSeconds),
                )
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_server),
                    credentials?.baseUrl.orEmpty(),
                )
                if (info?.maxConnections != null) {
                    XtreamAccountInfoRow(
                        stringResource(R.string.xtream_profile_connections),
                        "${info.activeConnections ?: 0} / ${info.maxConnections}",
                    )
                }
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_playback_server),
                    localizedHealth(state.serverHealth.playback),
                )
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_movies_health),
                    localizedHealth(state.serverHealth.movies),
                )
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_series_health),
                    localizedHealth(state.serverHealth.series),
                )
                XtreamAccountInfoRow(
                    stringResource(R.string.xtream_profile_last_check),
                    formatLastCheck(state.serverHealth.checkedAtMillis),
                )
            }
        }
        if (state.error) {
            Text(
                text = stringResource(R.string.xtream_profile_refresh_error),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.Error,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                onClick = viewModel::refresh,
                enabled = !state.refreshing,
                modifier = (initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).weight(1f),
            ) {
                Text(
                    if (state.refreshing) {
                        stringResource(R.string.xtream_profile_checking_server)
                    } else {
                        stringResource(R.string.xtream_profile_check_server)
                    },
                )
            }
            Button(
                onClick = { showSignOutConfirmation = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.xtream_profile_sign_out))
            }
        }
    }

    if (showSignOutConfirmation) {
        NuvioDialog(
            onDismiss = { showSignOutConfirmation = false },
            title = stringResource(R.string.xtream_profile_sign_out_confirm_title),
            subtitle = stringResource(R.string.xtream_profile_sign_out_confirm_subtitle),
            width = 460.dp,
            suppressFirstKeyUp = false,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showSignOutConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = {
                    showSignOutConfirmation = false
                    viewModel.signOut()
                }) {
                    Text(stringResource(R.string.xtream_profile_sign_out))
                }
            }
        }
    }
}

@Composable
private fun XtreamAccountInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(label, color = NuvioTheme.colors.TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value.ifBlank { "—" }, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun localizedStatus(status: String?): String = when {
    status.equals("active", ignoreCase = true) -> stringResource(R.string.xtream_profile_status_active)
    status.isNullOrBlank() -> stringResource(R.string.xtream_profile_status_unknown)
    else -> status
}

@Composable
private fun localizedHealth(health: XtreamComponentHealth): String = when (health.state) {
    XtreamHealthState.CHECKING -> stringResource(R.string.xtream_health_checking)
    XtreamHealthState.HEALTHY -> stringResource(R.string.xtream_health_healthy)
    XtreamHealthState.DEGRADED -> stringResource(R.string.xtream_health_degraded)
    XtreamHealthState.PROVIDER_FAILURE -> when (health.reason) {
        XtreamHealthReason.HTTP_UNAUTHORIZED, XtreamHealthReason.HTTP_FORBIDDEN ->
            stringResource(R.string.xtream_health_access_denied)
        XtreamHealthReason.HTTP_RATE_LIMITED -> stringResource(R.string.xtream_health_rate_limited)
        else -> stringResource(R.string.xtream_health_provider_failure)
    }
    XtreamHealthState.UNAUTHORIZED -> stringResource(R.string.xtream_health_access_denied)
    XtreamHealthState.LOCAL_NETWORK_FAILURE -> stringResource(R.string.xtream_health_local_network)
    XtreamHealthState.APP_FORMAT_FAILURE -> stringResource(R.string.xtream_health_app_format)
    XtreamHealthState.UNKNOWN -> when (health.reason) {
        XtreamHealthReason.CATALOG_NOT_READY -> stringResource(R.string.xtream_health_waiting_catalog)
        XtreamHealthReason.NO_SAMPLE -> stringResource(R.string.xtream_health_no_sample)
        else -> stringResource(R.string.xtream_health_not_checked)
    }
}

@Composable
private fun formatLastCheck(epochMillis: Long?): String {
    if (epochMillis == null) return stringResource(R.string.xtream_health_never_checked)
    return runCatching {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime())
    }.getOrElse { stringResource(R.string.xtream_health_never_checked) }
}

@Composable
private fun formatExpiration(epochSeconds: Long?): String {
    if (epochSeconds == null) return stringResource(R.string.xtream_profile_no_expiration)
    return runCatching {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate())
    }.getOrElse { stringResource(R.string.xtream_profile_status_unknown) }
}
