package com.nuvio.tv.ui.screens.xtream

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun XtreamSetupScreen(
    onConfigured: () -> Unit,
    viewModel: XtreamSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showManual by rememberSaveable { mutableStateOf(false) }
    var manualServer by rememberSaveable(state.serverEndpoint) { mutableStateOf(state.serverEndpoint) }
    var manualUsername by rememberSaveable { mutableStateOf("") }
    var manualPassword by rememberSaveable { mutableStateOf("") }
    var operatorTaps by remember { mutableIntStateOf(0) }
    var lastOperatorTapMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.applied) { if (state.applied) onConfigured() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 42.dp)
            // Hidden operator gesture: five OK presses reveal the editable endpoint field. End users
            // never need it — the app always ships an operator-defined server.
            .onPreviewKeyEvent { event ->
                val activate = event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                if (!activate) return@onPreviewKeyEvent false
                val now = android.os.SystemClock.elapsedRealtime()
                operatorTaps = if (now - lastOperatorTapMillis <= OPERATOR_TAP_WINDOW_MILLIS) {
                    operatorTaps + 1
                } else {
                    1
                }
                lastOperatorTapMillis = now
                if (operatorTaps < OPERATOR_TAPS_REQUIRED) return@onPreviewKeyEvent false
                operatorTaps = 0
                showManual = true
                viewModel.revealAdvancedServer()
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showManual && state.pendingCredentials == null) {
            ManualSetupContent(
                server = manualServer,
                onServerChange = { manualServer = it },
                advancedServer = state.advancedServer,
                username = manualUsername,
                onUsernameChange = { manualUsername = it },
                password = manualPassword,
                onPasswordChange = { manualPassword = it },
                validating = state.validating,
                error = state.error,
                onBackToQr = { showManual = false },
                onSubmit = { viewModel.submitManual(manualUsername, manualPassword, manualServer) },
            )
            return@Box
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.qrCode?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_qr_code),
                    modifier = Modifier.size(300.dp),
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(R.string.xtream_setup_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = NuvioTheme.colors.TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.xtream_setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextSecondary,
                )
                state.serverUrl?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = NuvioTheme.colors.Primary)
                }
                if (state.resolvingEndpoint) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.xtream_setup_preparing), color = NuvioTheme.colors.TextSecondary)
                } else if (state.serverEndpoint.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    ManagedServerRow(
                        label = stringResource(R.string.xtream_setup_server_label),
                        hint = stringResource(R.string.xtream_setup_server_managed),
                        value = state.serverEndpoint,
                    )
                }
                if (state.pendingCredentials == null && !state.validating) {
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { showManual = true }) {
                        Text(stringResource(R.string.xtream_setup_manual_action))
                    }
                }
                if (state.validating) {
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.xtream_setup_validating), color = NuvioTheme.colors.TextSecondary)
                }
                state.pendingCredentials?.let { pending ->
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.xtream_setup_confirm_title), fontWeight = FontWeight.Bold)
                    Text(pending.baseUrl, color = NuvioTheme.colors.TextSecondary)
                    Text(maskUsername(pending.username), color = NuvioTheme.colors.TextSecondary)
                    Text(
                        stringResource(R.string.xtream_setup_playback_not_checked),
                        color = NuvioTheme.colors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp)) {
                        Button(onClick = viewModel::confirm, shape = ButtonDefaults.shape(RoundedCornerShape(50))) {
                            Text(stringResource(R.string.action_confirm))
                        }
                        Button(onClick = viewModel::reject, shape = ButtonDefaults.shape(RoundedCornerShape(50))) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
                state.error?.let { error ->
                    Spacer(Modifier.height(18.dp))
                    Text(error, color = NuvioTheme.colors.Error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::start) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun ManualSetupContent(
    server: String,
    onServerChange: (String) -> Unit,
    advancedServer: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    validating: Boolean,
    error: String?,
    onBackToQr: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.xtream_setup_manual_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = NuvioTheme.colors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.xtream_setup_manual_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        if (advancedServer) {
            ManualField(
                value = server,
                onValueChange = onServerChange,
                label = stringResource(R.string.xtream_setup_advanced_server_label),
                imeAction = ImeAction.Next,
            )
        } else {
            ManagedServerRow(
                label = stringResource(R.string.xtream_setup_server_label),
                hint = stringResource(R.string.xtream_setup_server_managed),
                value = server,
            )
        }
        ManualField(
            value = username,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.xtream_setup_username_label),
            imeAction = ImeAction.Next,
        )
        ManualField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.xtream_setup_password_label),
            password = true,
            imeAction = ImeAction.Done,
        )
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = NuvioTheme.colors.Error)
        }
        if (validating) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.xtream_setup_validating), color = NuvioTheme.colors.TextSecondary)
        }
        Row(
            modifier = Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                onClick = onBackToQr,
                enabled = !validating,
                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                // Static geometry: the default focus scale (1.1) settling back to
                // 1.0 when validating disables both buttons reads as a flattening
                // glitch right before the screen returns to the QR view.
                scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
            ) {
                Text(stringResource(R.string.xtream_setup_back_to_qr))
            }
            Button(
                onClick = onSubmit,
                enabled = !validating,
                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }
}

@Composable
private fun ManagedServerRow(label: String, hint: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = NuvioTheme.colors.TextSecondary,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.Primary,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(62.dp)
                .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp))
                .border(1.dp, NuvioTheme.colors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ManualField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    imeAction: ImeAction,
) {
    var focused by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editing) {
        if (editing) keyboardController?.show()
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = NuvioTheme.colors.TextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !editing,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = {
                    editing = false
                    keyboardController?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                },
                onDone = {
                    editing = false
                    keyboardController?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                },
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = NuvioTheme.colors.TextPrimary),
            cursorBrush = SolidColor(NuvioTheme.colors.Primary),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                }
            },
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(62.dp)
                .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.Border,
                    shape = RoundedCornerShape(12.dp),
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.hasFocus) {
                        editing = false
                        keyboardController?.hide()
                    }
                }
                .onPreviewKeyEvent { event ->
                    val activate = event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                    if (activate && !editing) {
                        editing = true
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

private fun maskUsername(value: String): String = when (value.length) {
    0 -> ""
    1, 2 -> "••••"
    else -> value.take(2) + "•".repeat((value.length - 2).coerceAtMost(8))
}

/** Hidden operator gesture: consecutive OK presses that reveal the editable endpoint field. */
private const val OPERATOR_TAPS_REQUIRED = 5
private const val OPERATOR_TAP_WINDOW_MILLIS = 1_200L
