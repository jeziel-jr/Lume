package com.nuvio.tv.ui.screens.addon

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AddonManagerScreen(
    viewModel: AddonManagerViewModel = hiltViewModel(),
    showBuiltInHeader: Boolean = true,
    onBackPress: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstAddonToggleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val surfaceFocusRequester = remember { FocusRequester() }
    val installButtonFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }

    BackHandler { onBackPress() }

    // When isEditing changes to true, focus the text field and show keyboard
    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val requestInputBarFocus = {
        coroutineScope.launch {
            repeat(2) { withFrameNanos { } }
            runCatching { surfaceFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isEditing) {
        if (!isEditing) {
            requestInputBarFocus()
        }
    }

    LaunchedEffect(uiState.transientMessage) {
        if (uiState.transientMessage != null) {
            delay(3200)
            viewModel.clearTransientMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.addon_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (showBuiltInHeader) NuvioTheme.colors.TextPrimary else Color.Transparent
                )
            }

            if (viewModel.isReadOnly) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3A5C)),
                        shape = RoundedCornerShape(NuvioTheme.radii.md)
                    ) {
                        Text(
                            text = stringResource(R.string.addon_readonly_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextSecondary,
                            modifier = androidx.compose.ui.Modifier.padding(NuvioTheme.spacing.lg)
                        )
                    }
                }
            }

            if (!viewModel.isReadOnly) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = NuvioTheme.colors.BackgroundCard),
                        shape = RoundedCornerShape(NuvioTheme.radii.md)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.addon_install_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = NuvioTheme.colors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Surface always stays in the tree for stable D-pad focus
                                Surface(
                                    onClick = { isEditing = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(surfaceFocusRequester),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = NuvioTheme.colors.BackgroundElevated,
                                        focusedContainerColor = NuvioTheme.colors.BackgroundElevated
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(
                                            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                                        ),
                                        focusedBorder = Border(
                                            border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                                        )
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                ) {
                                    Box(modifier = Modifier.padding(NuvioTheme.spacing.md)) {
                                        BasicTextField(
                                            value = uiState.installUrl,
                                            onValueChange = viewModel::onInstallUrlChange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(textFieldFocusRequester)
                                                .onFocusChanged {
                                                    if (!it.isFocused && isEditing) {
                                                        isEditing = false
                                                        keyboardController?.hide()
                                                    }
                                                },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    viewModel.installAddon()
                                                    isEditing = false
                                                    keyboardController?.hide()
                                                    installButtonFocusRequester.requestFocus()
                                                }
                                            ),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = NuvioTheme.colors.TextPrimary
                                            ),
                                            cursorBrush = SolidColor(if (isEditing) NuvioTheme.colors.Primary else Color.Transparent),
                                            decorationBox = { innerTextField ->
                                                if (uiState.installUrl.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.addon_install_placeholder),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = NuvioTheme.colors.TextTertiary
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.installAddon()
                                        isEditing = false
                                        keyboardController?.hide()
                                        installButtonFocusRequester.requestFocus()
                                    },
                                    enabled = !uiState.isInstalling,
                                    modifier = Modifier.focusRequester(installButtonFocusRequester),
                                    colors = ButtonDefaults.colors(
                                        containerColor = NuvioTheme.colors.BackgroundCard,
                                        contentColor = NuvioTheme.colors.TextPrimary,
                                        focusedContainerColor = NuvioTheme.colors.FocusBackground,
                                        focusedContentColor = NuvioTheme.colors.Primary
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                                ) {
                                    Text(text = if (uiState.isInstalling) stringResource(R.string.addon_installing) else stringResource(R.string.addon_install_btn))
                                }
                            }

                            AnimatedVisibility(visible = uiState.error != null) {
                                Text(
                                    text = uiState.error.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NuvioTheme.colors.Error,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.addon_installed_section),
                        style = MaterialTheme.typography.titleLarge,
                        color = NuvioTheme.colors.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
                    if (uiState.isLoading && uiState.installedAddons.isEmpty()) {
                        LoadingIndicator(modifier = Modifier.height(NuvioTheme.spacing.xl))
                    }
                }
            }

            if (uiState.installedAddons.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.addon_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.installedAddons,
                    key = { _, addon -> addon.baseUrl }
                ) { index, addon ->
                    AddonCard(
                        modifier = Modifier,
                        addon = addon,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.installedAddons.lastIndex,
                        onMoveUp = { viewModel.moveAddonUp(addon.baseUrl) },
                        onMoveDown = { viewModel.moveAddonDown(addon.baseUrl) },
                        onRemove = { viewModel.removeAddon(addon.baseUrl) },
                        onEnabledChange = { enabled -> viewModel.setAddonEnabled(addon.baseUrl, enabled) },
                        isReadOnly = viewModel.isReadOnly,
                        showReorder = !viewModel.isReadOnly,
                        toggleFocusRequester = if (index == 0) firstAddonToggleFocusRequester else null
                    )
                }
            }
        }

        AddonMessageOverlay(
            message = uiState.transientMessage,
            isError = uiState.transientMessageIsError
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AddonMessageOverlay(
    message: String?,
    isError: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(NuvioTheme.spacing.xl),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val visibleMessage = message ?: return@AnimatedVisibility
            Surface(
                onClick = { },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isError) {
                        Color(0xFFC62828).copy(alpha = 0.92f)
                    } else {
                        Color(0xFF2E7D32).copy(alpha = 0.92f)
                    }
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = NuvioTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = visibleMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AddonCard(
    modifier: Modifier = Modifier,
    addon: Addon,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    isReadOnly: Boolean = false,
    showReorder: Boolean = true,
    toggleFocusRequester: FocusRequester? = null
) {
    if (isReadOnly) {
        Surface(
            onClick = { },
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.BackgroundCard
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                )
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            AddonCardContent(addon = addon, isReadOnly = true)
        }
    } else {
        val internalToggleFocusRequester = remember { FocusRequester() }
        val effectiveToggleFocusRequester = toggleFocusRequester ?: internalToggleFocusRequester

        Card(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize()
                .focusProperties {
                    enter = { effectiveToggleFocusRequester }
                },
            colors = CardDefaults.cardColors(containerColor = NuvioTheme.colors.BackgroundCard),
            shape = RoundedCornerShape(NuvioTheme.radii.md)
        ) {
            AddonCardContent(
                addon = addon,
                isReadOnly = false,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRemove = onRemove,
                onEnabledChange = onEnabledChange,
                showReorder = showReorder,
                toggleFocusRequester = effectiveToggleFocusRequester
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonCardContent(
    addon: Addon,
    isReadOnly: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemove: () -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
    showReorder: Boolean = true,
    toggleFocusRequester: FocusRequester? = null
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = addon.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NuvioTheme.colors.TextPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (addon.version.isNotBlank()) {
                        Text(
                            text = "v${addon.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                    if (!addon.enabled) {
                        Text(
                            text = stringResource(R.string.addons_badge_disabled),
                            style = MaterialTheme.typography.labelSmall,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                }
            }
            if (!isReadOnly) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onEnabledChange(!addon.enabled) },
                        modifier = Modifier
                            .focusRequester(toggleFocusRequester ?: remember { FocusRequester() }),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Switch(
                                checked = addon.enabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NuvioTheme.colors.Secondary,
                                    checkedTrackColor = NuvioTheme.colors.Secondary.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                    if (showReorder) {
                        Button(
                            onClick = onMoveUp,
                            enabled = canMoveUp,
                            colors = ButtonDefaults.colors(
                                containerColor = NuvioTheme.colors.BackgroundCard,
                                contentColor = NuvioTheme.colors.TextSecondary,
                                focusedContainerColor = NuvioTheme.colors.FocusBackground,
                                focusedContentColor = NuvioTheme.colors.Primary
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.cd_move_up))
                        }
                        Button(
                            onClick = onMoveDown,
                            enabled = canMoveDown,
                            colors = ButtonDefaults.colors(
                                containerColor = NuvioTheme.colors.BackgroundCard,
                                contentColor = NuvioTheme.colors.TextSecondary,
                                focusedContainerColor = NuvioTheme.colors.FocusBackground,
                                focusedContentColor = NuvioTheme.colors.Primary
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.cd_move_down))
                        }
                    }
                    Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            contentColor = NuvioTheme.colors.TextSecondary,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground,
                            focusedContentColor = NuvioTheme.colors.Error
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
                    ) {
                        Text(text = stringResource(R.string.addon_remove))
                    }
                }
            }
        }

        if (!addon.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
            Text(
                text = addon.description ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
        Text(
            text = addon.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary
        )

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
        Text(
            text = stringResource(R.string.addon_catalogs_types, addon.catalogs.size, addon.rawTypes.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary
        )
    }
}
