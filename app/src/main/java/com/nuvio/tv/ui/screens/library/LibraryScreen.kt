package com.nuvio.tv.ui.screens.library

import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.core.cloud.CloudLibraryFile
import com.nuvio.tv.core.cloud.CloudLibraryItem
import com.nuvio.tv.core.cloud.CloudLibraryItemType
import com.nuvio.tv.core.cloud.CloudLibraryPlaybackInfo
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.data.xtream.catalogAvailabilityKey

private const val KEY_REPEAT_THROTTLE_MS = 80L

private enum class LibraryViewMode {
    Saved,
    Cloud
}

@Composable
private fun localizedTypeLabel(key: String): String = when (key.lowercase()) {
    LibraryTypeTab.ALL_KEY -> stringResource(R.string.library_type_all)
    else -> localizedContentType(key)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    showBuiltInHeader: Boolean = true,
    onNavigateToDetail: (String, String, String?) -> Unit,
    onCloudPlaybackResolved: (CloudLibraryPlaybackInfo) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val catalogAvailability by viewModel.catalogAvailability.collectAsState()
    val watchedMovieIds by viewModel.watchedMovieIds.collectAsState()
    val watchedSeriesIds by viewModel.watchedSeriesIds.collectAsState()
    var expandedPicker by remember { mutableStateOf<String?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.Saved) }
    var activeCloudItem by remember { mutableStateOf<CloudLibraryItem?>(null) }
    val primaryFocusRequester = remember { FocusRequester() }
    val selectorFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var pendingPrimaryFocus by remember { mutableStateOf(true) }
    var lastFocusedPosterKey by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleItemKeys = remember(uiState.visibleItems) {
        uiState.visibleItems.map { "${it.type}:${it.id}" }
    }
    val visibleItemIndexByKey = remember(visibleItemKeys) {
        visibleItemKeys.withIndex().associate { (index, key) -> key to index }
    }
    val posterFocusRequesters = remember(visibleItemKeys) {
        visibleItemKeys.associateWith { FocusRequester() }
    }
    val firstVisiblePosterKey = visibleItemKeys.firstOrNull()
    val posterCardStyle = PosterCardDefaults.Style

    LaunchedEffect(viewMode, uiState.cloudLibrary.isEnabled, uiState.cloudLibrary.isLoaded, uiState.cloudLibrarySettingsVersion) {
        if (viewMode == LibraryViewMode.Cloud) {
            viewModel.ensureCloudLibraryLoaded()
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            pendingPrimaryFocus = true
        }
    }

    LaunchedEffect(uiState.isLoading, uiState.allItems.size) {
        if (!uiState.isLoading && pendingPrimaryFocus) {
            val restoreKey = lastFocusedPosterKey
            val restoreIndex = restoreKey?.let { visibleItemIndexByKey[it] }
            val restoreRequester = restoreKey?.let { posterFocusRequesters[it] }

            var focused = false
            if (restoreIndex != null && restoreRequester != null) {
                runCatching { gridState.scrollToItem(restoreIndex) }
                focused = runCatching { restoreRequester.requestFocus() }.isSuccess
                if (!focused) {
                    delay(16)
                    focused = runCatching { restoreRequester.requestFocus() }.isSuccess
                }
            }

            if (!focused) {
                focused = runCatching { primaryFocusRequester.requestFocus() }.isSuccess
            }
            if (!focused) {
                delay(16)
                runCatching { primaryFocusRequester.requestFocus() }
            }
            pendingPrimaryFocus = false
        }
    }

    LaunchedEffect(uiState.sortSelectionVersion, firstVisiblePosterKey) {
        if (uiState.sortSelectionVersion <= 0L) return@LaunchedEffect
        val targetKey = firstVisiblePosterKey ?: return@LaunchedEffect
        runCatching { gridState.scrollToItem(0) }
        var focused = false
        repeat(6) {
            focused = posterFocusRequesters[targetKey]
                ?.let { requester -> runCatching { requester.requestFocus() }.isSuccess }
                ?: false
            if (focused) return@LaunchedEffect
            delay(24)
        }
    }

    if (uiState.isLoading) {
        val loadingFocusRequester = remember { FocusRequester() }
        LaunchedEffect(uiState.isLoading) {
            loadingFocusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(loadingFocusRequester)
                    .focusable()
            )
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LoadingIndicator()
            }
        }
        return
    }

    val lastKeyRepeatTime = remember { longArrayOf(0L) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = posterCardStyle.width),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount > 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastKeyRepeatTime[0] < KEY_REPEAT_THROTTLE_MS) {
                        return@onPreviewKeyEvent true
                    }
                    lastKeyRepeatTime[0] = now
                }
                false
            },
        contentPadding = PaddingValues(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl, top = NuvioTheme.spacing.xl, bottom = NuvioTheme.spacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (showBuiltInHeader) NuvioTheme.colors.TextPrimary else Color.Transparent,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = when {
                        viewMode == LibraryViewMode.Cloud -> stringResource(R.string.library_source_cloud).uppercase()
                        else -> stringResource(R.string.library_source_local)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showBuiltInHeader) NuvioTheme.colors.TextTertiary else Color.Transparent,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryViewModeRow(
                selectedMode = viewMode,
                primaryFocusRequester = primaryFocusRequester,
                onSelected = { mode ->
                    viewMode = mode
                    expandedPicker = null
                }
            )
        }

        if (viewMode == LibraryViewMode.Saved) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibrarySelectorsRow(
                    typeTabs = uiState.availableTypeTabs,
                    sortOptions = uiState.availableSortOptions,
                    genres = uiState.availableGenres,
                    years = uiState.availableYears,
                    selectedTypeTab = uiState.selectedTypeTab,
                    selectedSortOption = uiState.selectedSortOption,
                    selectedGenre = uiState.selectedGenre,
                    selectedYear = uiState.selectedYear,
                    primaryFocusRequester = selectorFocusRequester,
                    expandedPicker = expandedPicker,
                    onExpandedChange = { picker, shouldExpand ->
                        expandedPicker = if (shouldExpand) picker else null
                    },
                    onSelectType = { type ->
                        viewModel.onSelectTypeTab(type)
                        expandedPicker = null
                    },
                    onSelectSort = { sort ->
                        viewModel.onSelectSortOption(sort)
                        expandedPicker = null
                    },
                    onSelectGenre = { key ->
                        viewModel.onSelectGenre(key)
                        expandedPicker = null
                    },
                    onSelectYear = { key ->
                        viewModel.onSelectYear(key)
                        expandedPicker = null
                    }
                )
            }

            if (uiState.visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val selectedTypeLabel = uiState.selectedTypeTab?.let { localizedTypeLabel(it.key) }?.lowercase() ?: stringResource(R.string.library_type_items)
                    EmptyScreenState(
                        title = stringResource(R.string.library_empty_local_title, selectedTypeLabel),
                        subtitle = stringResource(R.string.library_empty_local_subtitle),
                        icon = Icons.Default.BookmarkBorder
                    )
                }
            }

            items(uiState.visibleItems, key = { "${it.type}:${it.id}" }) { item ->
                val focusKey = "${item.type}:${item.id}"
                val isSeries = item.type.equals("series", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)
                val previewForLongPress = remember(item) {
                    item.toMetaPreview().copy(posterShape = PosterShape.POSTER)
                }
                GridContentCard(
                    item = previewForLongPress,
                    catalogAvailability = catalogAvailability[previewForLongPress.catalogAvailabilityKey()],
                    posterCardStyle = posterCardStyle,
                    isWatched = if (isSeries) item.id in watchedSeriesIds else item.id in watchedMovieIds,
                    focusRequester = posterFocusRequesters[focusKey],
                    showLabel = true,
                    onFocused = {
                        lastFocusedPosterKey = focusKey
                    },
                    onClick = {
                        lastFocusedPosterKey = focusKey
                        onNavigateToDetail(item.id, item.type, item.addonBaseUrl)
                    },
                    onLongPress = {
                        lastFocusedPosterKey = focusKey
                        viewModel.posterOptions.show(previewForLongPress, item.addonBaseUrl)
                    }
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CloudLibrarySelectorsRow(
                    providerOptions = uiState.availableCloudProviders,
                    typeOptions = uiState.availableCloudTypes,
                    selectedProviderId = uiState.selectedCloudProviderId,
                    selectedType = uiState.selectedCloudType,
                    expandedPicker = expandedPicker,
                    onExpandedChange = { picker, shouldExpand ->
                        expandedPicker = if (shouldExpand) picker else null
                    },
                    onSelectProvider = { providerId ->
                        viewModel.onSelectCloudProvider(providerId)
                        expandedPicker = null
                    },
                    onSelectType = { type ->
                        viewModel.onSelectCloudType(type)
                        expandedPicker = null
                    }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                CloudLibraryActionsRow(
                    isRefreshing = uiState.cloudLibrary.isRefreshing,
                    onRefresh = viewModel::refreshCloudLibrary
                )
            }

            if (uiState.cloudLibrary.isRefreshing && uiState.visibleCloudItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CloudLibraryLoadingState()
                }
            } else if (!uiState.cloudLibrary.isEnabled) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyScreenState(
                        title = stringResource(R.string.cloud_library_disabled_title),
                        subtitle = stringResource(R.string.cloud_library_disabled_message),
                        icon = Icons.Default.BookmarkBorder,
                        height = 260.dp
                    )
                }
            } else if (!uiState.cloudLibrary.hasConnectedProvider) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyScreenState(
                        title = stringResource(R.string.cloud_library_connect_title),
                        subtitle = stringResource(R.string.cloud_library_connect_message),
                        icon = Icons.Default.BookmarkBorder,
                        height = 260.dp
                    )
                }
            } else if (uiState.visibleCloudItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyScreenState(
                        title = stringResource(R.string.cloud_library_empty_title),
                        subtitle = stringResource(R.string.cloud_library_empty_message),
                        icon = Icons.Default.BookmarkBorder,
                        height = 260.dp
                    )
                }
            }

            items(
                items = uiState.visibleCloudItems,
                key = { it.stableKey },
                span = { GridItemSpan(maxLineSpan) }
            ) { item ->
                CloudLibraryCard(
                    item = item,
                    isResolving = uiState.resolvingCloudFileKey?.startsWith(item.stableKey) == true,
                    onClick = {
                        val playableFiles = item.playableFiles
                        when (playableFiles.size) {
                            0 -> viewModel.onCloudItemHasNoPlayableFiles()
                            1 -> viewModel.resolveCloudPlayback(item, playableFiles.first(), onCloudPlaybackResolved)
                            else -> activeCloudItem = item
                        }
                    }
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm)) }
    }

    activeCloudItem?.let { item ->
        CloudFilePickerDialog(
            item = item,
            resolvingFileKey = uiState.resolvingCloudFileKey,
            onPlay = { file ->
                viewModel.resolveCloudPlayback(item, file) { info ->
                    activeCloudItem = null
                    onCloudPlaybackResolved(info)
                }
            },
            onDismiss = { activeCloudItem = null }
        )
    }

    val transientMessage = uiState.transientMessage
    if (!transientMessage.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter
        ) {
            Text(
                text = transientMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier
                    .padding(top = NuvioTheme.spacing.xl)
                    .background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    val posterOptionsState by viewModel.posterOptions.state.collectAsState()
    com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
        state = posterOptionsState,
        controller = viewModel.posterOptions,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            onNavigateToDetail(id, type, addonBaseUrl.takeIf { it.isNotBlank() })
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryViewModeRow(
    selectedMode: LibraryViewMode,
    primaryFocusRequester: FocusRequester,
    onSelected: (LibraryViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        LibraryViewMode.entries.forEachIndexed { index, mode ->
            val selected = mode == selectedMode
            Button(
                onClick = { onSelected(mode) },
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(primaryFocusRequester) else Modifier),
                colors = ButtonDefaults.colors(
                    containerColor = if (selected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(
                    text = when (mode) {
                        LibraryViewMode.Saved -> stringResource(R.string.library_source_saved)
                        LibraryViewMode.Cloud -> stringResource(R.string.library_source_cloud)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CloudLibrarySelectorsRow(
    providerOptions: List<FilterOption>,
    typeOptions: List<FilterOption>,
    selectedProviderId: String?,
    selectedType: CloudLibraryItemType?,
    expandedPicker: String?,
    onExpandedChange: (String, Boolean) -> Unit,
    onSelectProvider: (String?) -> Unit,
    onSelectType: (CloudLibraryItemType?) -> Unit
) {
    val allLabel = stringResource(R.string.cloud_library_provider_all)
    val typeAllLabel = stringResource(R.string.cloud_library_type_all)
    val selectedProviderLabel = providerOptions.firstOrNull { it.key == selectedProviderId }?.label ?: allLabel
    val selectedTypeLabel = selectedType?.localizedLabel() ?: typeAllLabel

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        LibraryDropdownPicker(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.cloud_library_select_provider),
            value = selectedProviderLabel,
            selectedValue = selectedProviderId ?: "__all__",
            expanded = expandedPicker == "cloud_provider",
            options = listOf(LibraryOption(allLabel, "__all__")) + providerOptions.map {
                LibraryOption("${it.label} (${it.count})", it.key)
            },
            onExpandedChange = { onExpandedChange("cloud_provider", it) },
            onSelect = { option ->
                onSelectProvider(if (option.value == "__all__") null else option.value)
            }
        )

        LibraryDropdownPicker(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.cloud_library_select_type),
            value = selectedTypeLabel,
            selectedValue = selectedType?.name ?: "__all__",
            expanded = expandedPicker == "cloud_type",
            options = listOf(LibraryOption(typeAllLabel, "__all__")) + typeOptions.map {
                LibraryOption("${it.label} (${it.count})", it.key)
            },
            onExpandedChange = { onExpandedChange("cloud_type", it) },
            onSelect = { option ->
                onSelectType(CloudLibraryItemType.entries.firstOrNull { it.name == option.value })
            }
        )
    }
}

@Composable
private fun CloudLibraryItemType.localizedLabel(): String =
    when (this) {
        CloudLibraryItemType.Torrent -> stringResource(R.string.cloud_library_type_torrents)
        CloudLibraryItemType.Usenet -> stringResource(R.string.cloud_library_type_usenet)
        CloudLibraryItemType.WebDownload -> stringResource(R.string.cloud_library_type_web)
        CloudLibraryItemType.File -> stringResource(R.string.cloud_library_type_files)
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CloudLibraryActionsRow(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        Button(
            onClick = onRefresh,
            enabled = !isRefreshing,
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(if (isRefreshing) stringResource(R.string.library_syncing_btn) else stringResource(R.string.cloud_library_refresh))
        }
    }
}

@Composable
private fun CloudLibraryLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator()
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.library_syncing_library),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CloudLibraryCard(
    item: CloudLibraryItem,
    isResolving: Boolean,
    onClick: () -> Unit
) {
    val fileLine = cloudLibraryFileLine(item)
    Card(
        onClick = { if (!isResolving) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = CardDefaults.border(
            border = Border(border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border), shape = RoundedCornerShape(10.dp)),
            focusedBorder = Border(border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing), shape = RoundedCornerShape(10.dp))
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            fileLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = cloudLibraryMetadata(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary
                )
                Text(
                    text = if (isResolving) {
                        stringResource(R.string.cloud_library_opening)
                    } else {
                        cloudLibraryPlayableLabel(item)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.playableFiles.isEmpty()) NuvioTheme.colors.TextTertiary else NuvioTheme.colors.Primary
                )
            }
        }
    }
}

@Composable
private fun cloudLibraryFileLine(item: CloudLibraryItem): String? =
    when (val count = item.playableFiles.size) {
        0 -> stringResource(R.string.cloud_library_no_playable_files)
        1 -> item.playableFiles.first().name.takeIf { it != item.name }
        else -> stringResource(R.string.cloud_library_playable_file_count, count)
    }

@Composable
private fun cloudLibraryPlayableLabel(item: CloudLibraryItem): String =
    when (val count = item.playableFiles.size) {
        0 -> stringResource(R.string.cloud_library_no_playable_files)
        1 -> stringResource(R.string.cloud_library_one_playable_file)
        else -> stringResource(R.string.cloud_library_playable_file_count, count)
    }

@Composable
private fun cloudLibraryMetadata(item: CloudLibraryItem): String {
    val parts = listOfNotNull(
        item.providerName.takeIf { it.isNotBlank() },
        item.type.localizedLabel(),
        item.status?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cloud_library_status_ready),
        formatCloudSize(item.sizeBytes)
    )
    return parts.joinToString(" • ")
}

private fun formatCloudSize(sizeBytes: Long?): String? {
    val bytes = sizeBytes ?: return null
    if (bytes <= 0L) return null
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes / 1_000_000.0
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CloudFilePickerDialog(
    item: CloudLibraryItem,
    resolvingFileKey: String?,
    onPlay: (CloudLibraryFile) -> Unit,
    onDismiss: () -> Unit
) {
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.cloud_library_file_picker_title),
        subtitle = item.name,
        width = 860.dp,
        suppressFirstKeyUp = false
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            items(item.playableFiles, key = { it.stableKey }) { file ->
                val resolving = resolvingFileKey == "${item.stableKey}:${file.stableKey}"
                Card(
                    onClick = { if (!resolving) onPlay(file) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundCard,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = CardDefaults.scale(focusedScale = 1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NuvioTheme.colors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        formatCloudSize(file.sizeBytes)?.let { size ->
                            Text(
                                text = size,
                                style = MaterialTheme.typography.bodySmall,
                                color = NuvioTheme.colors.TextSecondary
                            )
                        }
                        Text(
                            text = stringResource(R.string.cloud_library_play_file),
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioTheme.colors.Primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibrarySelectorsRow(
    typeTabs: List<LibraryTypeTab>,
    sortOptions: List<LibrarySortOption>,
    genres: List<FilterOption>,
    years: List<FilterOption>,
    selectedTypeTab: LibraryTypeTab?,
    selectedSortOption: LibrarySortOption,
    selectedGenre: String?,
    selectedYear: String?,
    primaryFocusRequester: FocusRequester,
    expandedPicker: String?,
    onExpandedChange: (String, Boolean) -> Unit,
    onSelectType: (LibraryTypeTab) -> Unit,
    onSelectSort: (LibrarySortOption) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSelectYear: (String?) -> Unit
) {
    val selectedTypeLabel = selectedTypeTab?.let {
        if (it.key == LibraryTypeTab.ALL_KEY) stringResource(R.string.library_type_all) else localizedTypeLabel(it.key)
    } ?: stringResource(R.string.library_type_all)
    val selectedSortLabel = stringResource(selectedSortOption.labelResId)
    val allLabel = stringResource(R.string.library_type_all)
    val selectedGenreLabel = selectedGenre?.let { localizedGenreLabel(it) } ?: allLabel
    val selectedYearLabel = selectedYear ?: allLabel

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            LibraryDropdownPicker(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(primaryFocusRequester),
                title = stringResource(R.string.library_filter_type),
                value = selectedTypeLabel,
                selectedValue = selectedTypeTab?.key,
                expanded = expandedPicker == "type",
                options = typeTabs.map {
                    val label = if (it.key == LibraryTypeTab.ALL_KEY) it.label else {
                        val countPart = it.label.substringAfterLast("(", "").removeSuffix(")")
                        val localizedName = localizedTypeLabel(it.key)
                        if (countPart.isNotBlank()) "$localizedName ($countPart)" else localizedName
                    }
                    LibraryOption(label, it.key)
                },
                onExpandedChange = { onExpandedChange("type", it) },
                onSelect = { option ->
                    typeTabs.firstOrNull { it.key == option.value }?.let(onSelectType)
                }
            )

            if (sortOptions.isNotEmpty()) {
                LibraryDropdownPicker(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.library_filter_sort),
                    value = selectedSortLabel,
                    selectedValue = selectedSortOption.key,
                    expanded = expandedPicker == "sort",
                    options = sortOptions.map { LibraryOption(stringResource(it.labelResId), it.key) },
                    onExpandedChange = { onExpandedChange("sort", it) },
                    onSelect = { option ->
                        sortOptions.firstOrNull { it.key == option.value }?.let(onSelectSort)
                    }
                )
            }
        }

        if (genres.isNotEmpty() || years.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
            ) {
                if (genres.isNotEmpty()) {
                    val genreAllOption = LibraryOption(allLabel, "__all__")
                    LibraryDropdownPicker(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.library_filter_genre),
                        value = selectedGenreLabel,
                        selectedValue = selectedGenre ?: "__all__",
                        expanded = expandedPicker == "genre",
                        options = listOf(genreAllOption) + genres.map {
                            LibraryOption("${localizedGenreLabel(it.label)} (${it.count})", it.key)
                        },
                        onExpandedChange = { onExpandedChange("genre", it) },
                        onSelect = { option ->
                            onSelectGenre(if (option.value == "__all__") null else option.value)
                        }
                    )
                }

                if (years.isNotEmpty()) {
                    val yearAllOption = LibraryOption(allLabel, "__all__")
                    LibraryDropdownPicker(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.library_filter_year),
                        value = selectedYearLabel,
                        selectedValue = selectedYear ?: "__all__",
                        expanded = expandedPicker == "year",
                        options = listOf(yearAllOption) + years.map {
                            LibraryOption("${it.label} (${it.count})", it.key)
                        },
                        onExpandedChange = { onExpandedChange("year", it) },
                        onSelect = { option ->
                            onSelectYear(if (option.value == "__all__") null else option.value)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryDropdownPicker(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    selectedValue: String?,
    expanded: Boolean,
    options: List<LibraryOption>,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (LibraryOption) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    var focusedOptionValue by remember(expanded) { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        Card(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { anchorSize = it }
                .onFocusChanged { isFocused = it.isFocused },
            shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                border = androidx.tv.material3.Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(14.dp)
                ),
                focusedBorder = androidx.tv.material3.Border(
                    border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            scale = CardDefaults.scale(
                focusedScale = 1.0f,
                pressedScale = 1.0f
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.TextTertiary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) stringResource(R.string.cd_collapse, title) else stringResource(R.string.cd_expand, title),
                        tint = if (isFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.TextSecondary
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                focusedOptionValue = null
                onExpandedChange(false)
            },
            modifier = Modifier
                .width(with(LocalDensity.current) { anchorSize.width.toDp() })
                .heightIn(max = 320.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = NuvioTheme.colors.BackgroundCard,
            tonalElevation = NuvioTheme.spacing.none,
            shadowElevation = NuvioTheme.spacing.sm,
            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                val isOptionFocused = option.value == focusedOptionValue
                val itemTextColor = when {
                    isOptionFocused -> NuvioTheme.colors.OnSecondary
                    isSelected -> NuvioTheme.colors.TextPrimary
                    else -> NuvioTheme.colors.TextPrimary
                }
                val itemBackgroundColor = when {
                    isOptionFocused -> NuvioTheme.colors.Secondary
                    isSelected -> NuvioTheme.colors.FocusBackground
                    else -> Color.Transparent
                }

                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = NuvioTheme.spacing.xxs)
                        .background(
                            color = itemBackgroundColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { state ->
                            val hasFocus = state.isFocused || state.hasFocus
                            focusedOptionValue = when {
                                hasFocus -> option.value
                                focusedOptionValue == option.value -> null
                                else -> focusedOptionValue
                            }
                        },
                    text = {
                        Text(
                            text = option.label,
                            color = itemTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = { onSelect(option) },
                    colors = MenuDefaults.itemColors(
                        textColor = itemTextColor,
                        disabledTextColor = NuvioTheme.colors.TextDisabled
                    )
                )
            }
        }
    }
}

private data class LibraryOption(
    val label: String,
    val value: String
)

