package com.nuvio.tv.ui.screens.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LiveChannel
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.PinDialog
import com.nuvio.tv.ui.theme.NuvioRadii
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.dpadVerticalFastScroll

private val CategoryColumnWidth = 420.dp
private val CategoryRowHeight = 56.dp
private val ChannelRowHeight = 72.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveChannelsScreen(
    viewModel: LiveChannelsViewModel = hiltViewModel(),
    onPlayChannel: (LiveChannel, String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categoriesState = rememberLazyListState()
    val channelsState = rememberLazyListState()

    val categoryRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val channelRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    fun selectedCategoryIndex(): Int? {
        val selectedId = state.selectedCategoryId
        if (selectedId == null) return 0
        val index = state.categories.indexOfFirst { it.id == selectedId }
        return if (index >= 0) index + 1 else null
    }

    LaunchedEffect(state.selectedCategoryId) {
        channelsState.scrollToItem(0)
    }

    // Restore focus to the previously selected category when returning from the
    // player (the ViewModel keeps the selection across the navigation round-trip).
    LaunchedEffect(Unit) {
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        val target = when (val selectedId = state.selectedCategoryId) {
            null -> categoryRequesters[0]
            else -> {
                val index = state.categories.indexOfFirst { it.id == selectedId }
                if (index >= 0) categoryRequesters[index + 1] else categoryRequesters[0]
            }
        }
        runCatching { target?.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(
                start = NuvioTheme.spacing.xxxl,
                end = NuvioTheme.spacing.xxxl,
                top = NuvioTheme.spacing.xl,
                bottom = NuvioTheme.spacing.md
            )
        ) {
            Text(
                text = stringResource(R.string.live_title),
                style = MaterialTheme.typography.headlineMedium,
                color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
            Text(
                text = stringResource(R.string.live_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = categoriesState,
                modifier = Modifier
                    .width(CategoryColumnWidth)
                    .fillMaxHeight()
                    .padding(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.md)
                    .dpadVerticalFastScroll(
                        scrollableState = categoriesState,
                        resolveVerticalLanding = { sign ->
                            val visible = categoriesState.layoutInfo.visibleItemsInfo
                            val target = if (sign < 0) {
                                visible.firstOrNull { it.offset >= 0 } ?: visible.firstOrNull()
                            } else {
                                visible.lastOrNull()
                            }
                            val requester = target?.let { categoryRequesters[it.index] }
                            runCatching { requester?.requestFocus() }
                            null
                        }
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key == Key.DirectionRight &&
                            state.channels.isNotEmpty() &&
                            !state.isLoading && state.loadError == null
                        ) {
                            runCatching { channelRequesters[0]?.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
                contentPadding = PaddingValues(bottom = NuvioTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
            ) {
                item(key = "all") {
                    val todosRequester = categoryRequesters.getOrPut(0) { FocusRequester() }
                    ChannelCategoryRow(
                        name = stringResource(R.string.live_all_channels),
                        isAdult = false,
                        isSelected = state.selectedCategoryId == null,
                        focusRequester = todosRequester,
                        onFocused = { viewModel.onCategoryFocused(null) },
                        onClick = {}
                    )
                }
                itemsIndexed(
                    items = state.categories,
                    key = { _, category -> category.id }
                ) { index, category ->
                    val requester = categoryRequesters.getOrPut(index + 1) { FocusRequester() }
                    ChannelCategoryRow(
                        name = category.name,
                        isAdult = category.isAdult,
                        isSelected = state.selectedCategoryId == category.id,
                        focusRequester = requester,
                        onFocused = { viewModel.onCategoryFocused(category) },
                        onClick = { viewModel.onCategoryClicked(category) }
                    )
                }
            }

            val panelModifier = Modifier
                .fillMaxSize()
                .padding(end = NuvioTheme.spacing.xxxl)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.DirectionLeft) {
                        val firstIndex = channelsState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                        if (firstIndex == 0) {
                            val target = selectedCategoryIndex()
                            val requester = target?.let { categoryRequesters[it] }
                            runCatching { requester?.requestFocus() }
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

            when {
                state.isLoading -> {
                    Box(modifier = panelModifier, contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                state.loadError != null -> {
                    Box(modifier = panelModifier, contentAlignment = Alignment.Center) {
                        ErrorState(
                            message = stringResource(R.string.live_error),
                            onRetry = viewModel::load
                        )
                    }
                }
                selectedCategoryIsLocked(state) -> {
                    Box(modifier = panelModifier, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = NuvioTheme.colors.TextTertiary
                            )
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
                            Text(
                                text = stringResource(R.string.live_adult_locked),
                                style = MaterialTheme.typography.titleMedium,
                                color = NuvioTheme.colors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                            Text(
                                text = stringResource(R.string.live_pin_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextSecondary
                            )
                        }
                    }
                }
                state.channels.isEmpty() -> {
                    Box(modifier = panelModifier, contentAlignment = Alignment.Center) {
                        EmptyScreenState(
                            title = stringResource(R.string.live_empty_category),
                            height = 260.dp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = channelsState,
                        modifier = panelModifier.dpadVerticalFastScroll(
                            scrollableState = channelsState,
                            resolveVerticalLanding = { sign ->
                                val visible = channelsState.layoutInfo.visibleItemsInfo
                                val target = if (sign < 0) {
                                    visible.firstOrNull { it.offset >= 0 } ?: visible.firstOrNull()
                                } else {
                                    visible.lastOrNull()
                                }
                                val requester = target?.let { channelRequesters[it.index] }
                                runCatching { requester?.requestFocus() }
                                null
                            }
                        ),
                        contentPadding = PaddingValues(bottom = NuvioTheme.spacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
                    ) {
                        itemsIndexed(
                            items = state.channels,
                            key = { _, channel -> channel.streamId }
                        ) { index, channel ->
                            val requester = channelRequesters.getOrPut(index) { FocusRequester() }
                            ChannelRow(
                                channel = channel,
                                focusRequester = requester,
                                onClick = {
                                    viewModel.playbackUrl(channel)?.let { url ->
                                        onPlayChannel(channel, url)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.pinPrompt) {
        PinDialog(
            title = stringResource(R.string.live_pin_title),
            subtitle = stringResource(R.string.live_pin_hint),
            error = state.pinErrorRes?.let { stringResource(it) },
            onDismiss = viewModel::onPinDismissed,
            onConfirm = viewModel::onPinConfirmed
        )
    }
}

private fun selectedCategoryIsLocked(state: LiveChannelsUiState): Boolean {
    val selected = state.categories.firstOrNull { it.id == state.selectedCategoryId }
    return selected != null && selected.isAdult && !state.adultUnlocked
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCategoryRow(
    name: String,
    isAdult: Boolean,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(NuvioRadii.tokens.md)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(CategoryRowHeight)
            .focusRequester(focusRequester)
            .onFocusChanged {
                val nowFocused = it.isFocused
                if (focused != nowFocused) {
                    focused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) {
                NuvioTheme.colors.BackgroundCard
            } else {
                NuvioTheme.colors.Background
            },
            focusedContainerColor = NuvioTheme.colors.SurfaceVariant
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                shape = rowShape
            )
        ),
        shape = CardDefaults.shape(shape = rowShape),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FocusMarqueeText(
                    text = name,
                    focused = focused,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) {
                        NuvioTheme.colors.TextPrimary
                    } else {
                        NuvioTheme.colors.TextSecondary
                    }
                )
            }
            if (isAdult) {
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = NuvioTheme.colors.TextTertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(NuvioRadii.tokens.md)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(ChannelRowHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.SurfaceVariant
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                shape = rowShape
            )
        ),
        shape = CardDefaults.shape(shape = rowShape),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(NuvioRadii.tokens.xs))
                    .background(NuvioTheme.colors.BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = NuvioTheme.colors.TextTertiary
                )
                channel.iconUrl?.let { iconUrl ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(NuvioRadii.tokens.xs))
                    )
                }
            }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
            Box(modifier = Modifier.weight(1f)) {
                FocusMarqueeText(
                    text = channel.name,
                    focused = focused,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary
                )
            }
            if (channel.number != null) {
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                Text(
                    text = channel.number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextTertiary
                )
            }
        }
    }
}
