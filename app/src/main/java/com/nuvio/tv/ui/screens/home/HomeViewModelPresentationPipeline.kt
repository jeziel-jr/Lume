package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.core.tmdb.TmdbEnrichment
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.TmdbSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CoreLayoutPrefs(
    val layout: HomeLayout,
    val heroCatalogKeys: List<String>,
    val heroSectionEnabled: Boolean,
    val posterLabelsEnabled: Boolean,
    val catalogAddonNameEnabled: Boolean,
    val catalogTypeSuffixEnabled: Boolean,
    val classicFocusGradientEnabled: Boolean,
    val hideUnreleasedContent: Boolean,
    val showFullReleaseDate: Boolean
)

private data class FocusedBackdropPrefs(
    val expandEnabled: Boolean,
    val expandDelaySeconds: Int,
    val trailerEnabled: Boolean,
    val trailerMuted: Boolean,
    val trailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget
)

private data class LayoutUiPrefs(
    val layout: HomeLayout,
    val heroCatalogKeys: List<String>,
    val heroSectionEnabled: Boolean,
    val posterLabelsEnabled: Boolean,
    val catalogAddonNameEnabled: Boolean,
    val catalogTypeSuffixEnabled: Boolean,
    val classicFocusGradientEnabled: Boolean,
    val hideUnreleasedContent: Boolean,
    val showFullReleaseDate: Boolean,
    val modernLandscapePostersEnabled: Boolean,
    val modernHeroFullScreenBackdropEnabled: Boolean,
    val focusedBackdropExpandEnabled: Boolean,
    val focusedBackdropExpandDelaySeconds: Int,
    val focusedBackdropTrailerEnabled: Boolean,
    val focusedBackdropTrailerMuted: Boolean,
    val focusedBackdropTrailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget,
    val posterCardWidthDp: Int,
    val posterCardHeightDp: Int,
    val posterCardCornerRadiusDp: Int
)

@OptIn(FlowPreview::class)
internal fun HomeViewModel.observeLayoutPreferencesPipeline() {
    val coreLayoutPrefsFlow = combine(
        combine(
            layoutPreferenceDataStore.selectedLayout,
            layoutPreferenceDataStore.heroCatalogSelections,
            layoutPreferenceDataStore.heroSectionEnabled,
            layoutPreferenceDataStore.posterLabelsEnabled,
            layoutPreferenceDataStore.catalogAddonNameEnabled
        ) { layout, heroCatalogKeys, heroSectionEnabled, posterLabelsEnabled, catalogAddonNameEnabled ->
            CoreLayoutPrefs(
                layout = layout,
                heroCatalogKeys = heroCatalogKeys,
                heroSectionEnabled = heroSectionEnabled,
                posterLabelsEnabled = posterLabelsEnabled,
                catalogAddonNameEnabled = catalogAddonNameEnabled,
                catalogTypeSuffixEnabled = true,
                classicFocusGradientEnabled = false,
                hideUnreleasedContent = false,
                showFullReleaseDate = true
            )
        },
        layoutPreferenceDataStore.catalogTypeSuffixEnabled,
        layoutPreferenceDataStore.hideUnreleasedContent,
        layoutPreferenceDataStore.showFullReleaseDate,
        layoutPreferenceDataStore.classicFocusGradientEnabled
    ) { corePrefs, catalogTypeSuffixEnabled, hideUnreleasedContent, showFullReleaseDate, classicFocusGradientEnabled ->
        corePrefs.copy(
            catalogTypeSuffixEnabled = catalogTypeSuffixEnabled,
            classicFocusGradientEnabled = classicFocusGradientEnabled,
            hideUnreleasedContent = hideUnreleasedContent,
            showFullReleaseDate = showFullReleaseDate
        )
    }

    val focusedBackdropPrefsFlow = combine(
        layoutPreferenceDataStore.focusedPosterBackdropExpandEnabled,
        layoutPreferenceDataStore.focusedPosterBackdropExpandDelaySeconds,
        layoutPreferenceDataStore.focusedPosterBackdropTrailerEnabled,
        layoutPreferenceDataStore.focusedPosterBackdropTrailerMuted,
        layoutPreferenceDataStore.focusedPosterBackdropTrailerPlaybackTarget
    ) { expandEnabled, expandDelaySeconds, trailerEnabled, trailerMuted, trailerPlaybackTarget ->
        FocusedBackdropPrefs(
            expandEnabled = expandEnabled,
            expandDelaySeconds = expandDelaySeconds,
            trailerEnabled = trailerEnabled,
            trailerMuted = trailerMuted,
            trailerPlaybackTarget = trailerPlaybackTarget
        )
    }

    val modernLayoutPrefsFlow = combine(
        layoutPreferenceDataStore.modernLandscapePostersEnabled,
        layoutPreferenceDataStore.modernHeroFullScreenBackdropEnabled
    ) { landscapePosters, fullScreenBackdrop ->
        landscapePosters to fullScreenBackdrop
    }

    val baseLayoutUiPrefsFlow = combine(
        coreLayoutPrefsFlow,
        focusedBackdropPrefsFlow,
        layoutPreferenceDataStore.posterCardWidthDp,
        layoutPreferenceDataStore.posterCardHeightDp,
        layoutPreferenceDataStore.posterCardCornerRadiusDp
    ) { corePrefs, focusedBackdropPrefs, posterCardWidthDp, posterCardHeightDp, posterCardCornerRadiusDp ->
        LayoutUiPrefs(
            layout = corePrefs.layout,
            heroCatalogKeys = corePrefs.heroCatalogKeys,
            heroSectionEnabled = corePrefs.heroSectionEnabled,
            posterLabelsEnabled = corePrefs.posterLabelsEnabled,
            catalogAddonNameEnabled = corePrefs.catalogAddonNameEnabled,
            catalogTypeSuffixEnabled = corePrefs.catalogTypeSuffixEnabled,
            classicFocusGradientEnabled = corePrefs.classicFocusGradientEnabled,
            hideUnreleasedContent = corePrefs.hideUnreleasedContent,
            showFullReleaseDate = corePrefs.showFullReleaseDate,
            modernLandscapePostersEnabled = false,
            modernHeroFullScreenBackdropEnabled = false,
            focusedBackdropExpandEnabled = focusedBackdropPrefs.expandEnabled,
            focusedBackdropExpandDelaySeconds = focusedBackdropPrefs.expandDelaySeconds,
            focusedBackdropTrailerEnabled = focusedBackdropPrefs.trailerEnabled &&
                AppFeaturePolicy.inAppTrailerPlaybackEnabled,
            focusedBackdropTrailerMuted = focusedBackdropPrefs.trailerMuted,
            focusedBackdropTrailerPlaybackTarget = focusedBackdropPrefs.trailerPlaybackTarget,
            posterCardWidthDp = posterCardWidthDp,
            posterCardHeightDp = posterCardHeightDp,
            posterCardCornerRadiusDp = posterCardCornerRadiusDp
        )
    }

    viewModelScope.launch {
        combine(
            baseLayoutUiPrefsFlow,
            modernLayoutPrefsFlow
        ) { basePrefs, modernPrefs ->
            basePrefs.copy(
                modernLandscapePostersEnabled = modernPrefs.first,
                modernHeroFullScreenBackdropEnabled = modernPrefs.second
            )
        }
            .distinctUntilChanged()
            .debounce(300)
            .collectLatest { prefs ->
                val effectivePosterLabelsEnabled = if (prefs.layout == HomeLayout.MODERN) {
                    false
                } else {
                    prefs.posterLabelsEnabled
                }
                val previousState = _uiState.value
                val heroKeysChanged = currentHeroCatalogKeys != prefs.heroCatalogKeys
                val shouldRefreshCatalogPresentation =
                    heroKeysChanged ||
                        previousState.heroSectionEnabled != prefs.heroSectionEnabled ||
                        previousState.homeLayout != prefs.layout ||
                        previousState.hideUnreleasedContent != prefs.hideUnreleasedContent ||
                        previousState.posterCardWidthDp != prefs.posterCardWidthDp
                currentHeroCatalogKeys = prefs.heroCatalogKeys
                // Reset focus state when layout changes so the outgoing
                // layout's onDispose doesn't poison the incoming layout
                // (e.g., Modern dispose saves hasSavedFocus=true right
                // before Classic composes, preventing hero initial focus).
                if (previousState.layoutPreferencesReady && previousState.homeLayout != prefs.layout) {
                    // Suppress the outgoing layout's onDispose from saving
                    // stale focus state before the incoming layout composes.
                    suppressFocusSave = true
                    clearFocusState()
                }
                _uiState.update {
                    it.copy(
                        layoutPreferencesReady = true,
                        homeLayout = prefs.layout,
                        heroCatalogKeys = prefs.heroCatalogKeys,
                        heroSectionEnabled = prefs.heroSectionEnabled,
                        posterLabelsEnabled = effectivePosterLabelsEnabled,
                        catalogAddonNameEnabled = prefs.catalogAddonNameEnabled,
                        catalogTypeSuffixEnabled = prefs.catalogTypeSuffixEnabled,
                        classicFocusGradientEnabled = prefs.classicFocusGradientEnabled && prefs.layout == HomeLayout.CLASSIC,
                        hideUnreleasedContent = prefs.hideUnreleasedContent,
                        showFullReleaseDate = prefs.showFullReleaseDate,
                        modernLandscapePostersEnabled = prefs.modernLandscapePostersEnabled,
                        modernHeroFullScreenBackdropEnabled = prefs.modernHeroFullScreenBackdropEnabled,
                        focusedPosterBackdropExpandEnabled = prefs.focusedBackdropExpandEnabled,
                        focusedPosterBackdropExpandDelaySeconds = prefs.focusedBackdropExpandDelaySeconds,
                        focusedPosterBackdropTrailerEnabled = prefs.focusedBackdropTrailerEnabled,
                        focusedPosterBackdropTrailerMuted = prefs.focusedBackdropTrailerMuted,
                        focusedPosterBackdropTrailerPlaybackTarget = prefs.focusedBackdropTrailerPlaybackTarget,
                        posterCardWidthDp = prefs.posterCardWidthDp,
                        posterCardHeightDp = prefs.posterCardHeightDp,
                        posterCardCornerRadiusDp = prefs.posterCardCornerRadiusDp
                    )
                }
                if (shouldRefreshCatalogPresentation) {
                    // When switching to GRID layout, load all pending lazy catalogs
                    // since grid doesn't support placeholder shimmer rows.
                    if (prefs.layout == HomeLayout.GRID) {
                        loadAllPendingLazyCatalogs()
                    }
                    // When hero catalog keys change, hero rows react through the
                    // published uiState.heroCatalogKeys; no legacy addon-catalog
                    // hero loader exists anymore.
                    if (!(heroKeysChanged && prefs.heroCatalogKeys.isNotEmpty())) {
                        scheduleUpdateCatalogRows()
                    }
                }
            }
    }
}

@OptIn(FlowPreview::class)
internal fun HomeViewModel.observeModernHomePresentationPipeline() {
    viewModelScope.launch {
        combine(uiState, _currentLocaleTag) { state, localeTag ->
                ModernHomePresentationInput(
                    homeRows = state.homeRows,
                    catalogRows = state.catalogRows,
                    continueWatchingItems = state.continueWatchingItems,
                    useLandscapePosters = state.modernLandscapePostersEnabled,
                    showCatalogTypeSuffix = state.catalogTypeSuffixEnabled,
                    showFullReleaseDate = state.showFullReleaseDate,
                    localeTag = localeTag
                )
            }
            // Compare by row structure only (keys + item counts), not by
            // item content.  TMDB/meta enrichment changes item fields but
            // not the row structure — the hero section reads enriched data
            // via lastEnrichedPreview instead.
            .distinctUntilChanged { old, new ->
                old.homeRows === new.homeRows
                    && old.continueWatchingItems == new.continueWatchingItems
                    && old.useLandscapePosters == new.useLandscapePosters
                    && old.showCatalogTypeSuffix == new.showCatalogTypeSuffix
                    && old.showFullReleaseDate == new.showFullReleaseDate
                    && old.localeTag == new.localeTag
                    && old.catalogRows.size == new.catalogRows.size
            }
            .debounce {
                // Use a longer debounce while catalogs are still loading to
                // avoid repeated expensive presentation builds during the
                // initial burst of catalog arrivals.
                if (catalogsLoadInProgress) 300L else 80L
            }
            .collectLatest { input ->
                val shouldWarmStart = uiState.value.modernHomePresentation.rows.list.isEmpty()
                val visibleCatalogRowCount = input.catalogRows.count { it.items.isNotEmpty() }
                val warmStartCatalogRowCount = if (input.continueWatchingItems.isNotEmpty()) 2 else 3

                if (shouldWarmStart && visibleCatalogRowCount > warmStartCatalogRowCount) {
                    val warmStartPresentation = withContext(Dispatchers.Default) {
                        buildModernHomePresentation(
                            input = input,
                            cache = modernCarouselRowBuildCache,
                            context = appContext,
                            maxCatalogRows = warmStartCatalogRowCount
                        )
                    }
                    _uiState.update { state ->
                        if (state.modernHomePresentation == warmStartPresentation) {
                            state
                        } else {
                            state.copy(modernHomePresentation = warmStartPresentation)
                        }
                    }
                }

                val presentation = withContext(Dispatchers.Default) {
                    buildModernHomePresentation(
                        input = input,
                        cache = modernCarouselRowBuildCache,
                        context = appContext
                    )
                }
                _uiState.update { state ->
                    if (state.modernHomePresentation == presentation) {
                        state
                    } else {
                        state.copy(modernHomePresentation = presentation)
                    }
                }
            }
    }
}

internal fun HomeViewModel.requestTrailerPreviewPipeline(item: MetaPreview) {
    requestTrailerPreviewPipeline(
        itemId = item.id,
        title = item.name,
        releaseInfo = item.releaseInfo,
        apiType = item.apiType,
        fallbackYtId = item.trailerYtIds.firstOrNull()
    )
}

internal fun HomeViewModel.requestTrailerPreviewPipeline(
    itemId: String,
    title: String,
    releaseInfo: String?,
    apiType: String,
    fallbackYtId: String? = null
) {
    if (!AppFeaturePolicy.inAppTrailerPlaybackEnabled) return
    if (startupGracePeriodActive) return

    // Resolve fallbackYtId from catalog item if not provided
    val resolvedFallbackYtId = fallbackYtId ?: findCatalogItemById(itemId)?.trailerYtIds?.firstOrNull()

    // Always bump version — only the latest request (highest version) will proceed after debounce
    activeTrailerPreviewItemId = itemId
    trailerPreviewRequestVersion++
    val requestVersion = trailerPreviewRequestVersion

    if (trailerPreviewNegativeCache.contains(itemId)) return
    if (trailerPreviewUrlsState.containsKey(itemId)) return
    if (!trailerPreviewLoadingIds.add(itemId)) return

    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Debounce: wait for focus to settle before hitting network
            delay(180)

            // Only the LATEST request proceeds — all earlier ones are stale
            if (trailerPreviewRequestVersion != requestVersion) {
                return@launch
            }

            val tmdbId = try {
                tmdbService.ensureTmdbId(itemId, apiType)
            } catch (_: Exception) {
                null
            }

            val trailerSource = trailerService.getTrailerPlaybackSource(
                title = title,
                year = extractYear(releaseInfo),
                tmdbId = tmdbId,
                type = apiType
            )

            withContext(Dispatchers.Main) {
                if (trailerSource?.videoUrl.isNullOrBlank()) {
                    val fallbackSource = resolvedFallbackYtId?.let { ytId ->
                        trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                            youtubeUrl = "https://www.youtube.com/watch?v=$ytId",
                            title = title,
                            year = extractYear(releaseInfo)
                        )
                    }
                    if (fallbackSource?.videoUrl != null) {
                        if (trailerPreviewUrlsState[itemId] != fallbackSource.videoUrl) {
                            trailerPreviewUrlsState[itemId] = fallbackSource.videoUrl
                        }
                        val fallbackAudio = fallbackSource.audioUrl
                        if (fallbackAudio.isNullOrBlank()) {
                            trailerPreviewAudioUrlsState.remove(itemId)
                        } else if (trailerPreviewAudioUrlsState[itemId] != fallbackAudio) {
                            trailerPreviewAudioUrlsState[itemId] = fallbackAudio
                        }
                    } else {
                        trailerPreviewNegativeCache.add(itemId)
                        trailerPreviewUrlsState.remove(itemId)
                        trailerPreviewAudioUrlsState.remove(itemId)
                    }
                } else {
                    val videoUrl = trailerSource.videoUrl
                    if (trailerPreviewUrlsState[itemId] != videoUrl) {
                        trailerPreviewUrlsState[itemId] = videoUrl
                    }
                    val audioUrl = trailerSource.audioUrl
                    if (audioUrl.isNullOrBlank()) {
                        trailerPreviewAudioUrlsState.remove(itemId)
                    } else if (trailerPreviewAudioUrlsState[itemId] != audioUrl) {
                        trailerPreviewAudioUrlsState[itemId] = audioUrl
                    }
                }
            }
        } finally {
            trailerPreviewLoadingIds.remove(itemId)
        }
    }
}

internal fun HomeViewModel.onItemFocusPipeline(item: MetaPreview) {
    if (startupGracePeriodActive) {
        deferredEnrichItem = item
        return
    }
    if (item.id in prefetchedTmdbIds) {
        // Ensure enrichedPreviews contains this item so the UI can display
        // hero data immediately (e.g. when adjacent prefetch resolved it
        // before the user focused on it).
        if (item.id !in _enrichedPreviews.value) {
            _enrichedPreviews.update { it + (item.id to item) }
        }
        if (_enrichingItemId.value == item.id) setEnrichingItemId(null)
        return
    }
    if (pendingTmdbEnrichItemId == item.id) return

    // Clear enriching for previous item immediately when focus moves away
    if (_enrichingItemId.value != null && _enrichingItemId.value != item.id) {
        setEnrichingItemId(null)
    }

    val tmdbEnabledForCurrentLayout = currentTmdbSettings.enabled &&
        (_uiState.value.homeLayout != HomeLayout.MODERN || currentTmdbSettings.modernHomeEnabled)

    if (tmdbEnabledForCurrentLayout) setEnrichingItemId(item.id)

    pendingTmdbEnrichItemId = item.id
    tmdbEnrichFocusJob?.cancel()
    tmdbEnrichFocusJob = viewModelScope.launch(Dispatchers.IO) {
        delay(HomeViewModel.EXTERNAL_META_PREFETCH_FOCUS_DEBOUNCE_MS)
        if (pendingTmdbEnrichItemId != item.id) {
            if (_enrichingItemId.value == item.id) setEnrichingItemId(null)
            return@launch
        }
        if (item.id in prefetchedTmdbIds) {
            if (_enrichingItemId.value == item.id) setEnrichingItemId(null)
            return@launch
        }

        try {
            if (tmdbEnabledForCurrentLayout) {
                val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, item.apiType) }.getOrNull()

                val enrichmentDeferred = if (tmdbId != null) async {
                    runCatching {
                        tmdbMetadataService.fetchEnrichment(
                            tmdbId = tmdbId,
                            contentType = item.type,
                            language = currentTmdbSettings.language
                        )
                    }.getOrNull()
                } else null

                val enrichment = enrichmentDeferred?.await()

                if (enrichment != null) {
                    prefetchedTmdbIds.add(item.id)
                    updateCatalogItemWithTmdb(item.id, enrichment)
                }
            }
        } finally {
            if (_enrichingItemId.value == item.id) {
                setEnrichingItemId(null)
                // If enrichment completed but no enriched data exists for this item,
                // mark it as failed so the UI can show catalog data immediately.
                if (item.id !in _enrichedPreviews.value &&
                    item.id !in prefetchedTmdbIds) {
                    _failedEnrichmentIds.value = _failedEnrichmentIds.value + item.id
                }
            }
        }
    }
}

internal fun HomeViewModel.preloadAdjacentItemPipeline(item: MetaPreview) {
    if (startupGracePeriodActive) return
    if (item.id in prefetchedTmdbIds) return
    if (pendingTmdbEnrichItemId == item.id || pendingAdjacentPrefetchItemId == item.id) return

    pendingAdjacentPrefetchItemId = item.id
    adjacentItemPrefetchJob?.cancel()
    adjacentItemPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
        val tmdbEnabledForCurrentLayout = currentTmdbSettings.enabled &&
            (_uiState.value.homeLayout != HomeLayout.MODERN || currentTmdbSettings.modernHomeEnabled)
        delay(HomeViewModel.EXTERNAL_META_PREFETCH_ADJACENT_DEBOUNCE_MS)
        if (pendingAdjacentPrefetchItemId != item.id) return@launch

        if (item.id in prefetchedTmdbIds) return@launch

        try {
            if (tmdbEnabledForCurrentLayout) {
                val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, item.apiType) }.getOrNull()
                val enrichment = if (tmdbId != null) runCatching {
                    tmdbMetadataService.fetchEnrichment(
                        tmdbId = tmdbId,
                        contentType = item.type,
                        language = currentTmdbSettings.language
                    )
                }.getOrNull() else null
                if (enrichment != null) {
                    prefetchedTmdbIds.add(item.id)
                    updateCatalogItemWithTmdb(item.id, enrichment)
                    _enrichedPreviews.update { it + (item.id to item) }
                }
            }
        } finally {
            if (pendingAdjacentPrefetchItemId == item.id) {
                pendingAdjacentPrefetchItemId = null
            }
        }
    }
}

private fun HomeViewModel.updateCatalogItemWithTmdb(itemId: String, enrichment: TmdbEnrichment) {
    val isModernLayout = _uiState.value.homeLayout == HomeLayout.MODERN
    fun mergeItem(currentItem: MetaPreview): MetaPreview {
        var merged = currentItem
        if (currentTmdbSettings.useBasicInfo) {
            merged = merged.copy(
                name = if (isModernLayout) enrichment.localizedTitle ?: merged.name else merged.name,
                description = enrichment.description ?: merged.description,
                genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else merged.genres
            )
        }
        if (currentTmdbSettings.useArtwork) {
            merged = merged.copy(
                background = enrichment.backdrop ?: merged.background,
                logo = enrichment.logo ?: merged.logo
            )
        }
        if (currentTmdbSettings.useDetails) {
            merged = merged.copy(
                runtime = enrichment.runtimeMinutes?.toString() ?: merged.runtime,
                ageRating = enrichment.ageRating ?: merged.ageRating,
                status = enrichment.status ?: merged.status
            )
        }
        if (currentTmdbSettings.useReleaseDates) {
            merged = merged.copy(
                releaseInfo = enrichment.releaseInfo ?: merged.releaseInfo
            )
        }
        return merged
    }

    updateIndexedCatalogItem(itemId, ::mergeItem)

    // Modern layout reads enrichment via enrichedPreviews / lastEnrichedPreview.
    // Rebuilding catalogRows here triggers a useless full-home recomposition.
    if (!isModernLayout) {
        _uiState.update { state ->
            var changed = false
            val updatedRows = state.catalogRows.map { row ->
                val idx = row.items.indexOfFirst { it.id == itemId }
                if (idx < 0) row
                else {
                    val mergedItem = mergeItem(row.items[idx])
                    if (mergedItem == row.items[idx]) row
                    else {
                        changed = true
                        val mutableItems = row.items.toMutableList()
                        mutableItems[idx] = mergedItem
                        row.copy(items = mutableItems)
                    }
                }
            }
            if (changed) state.copy(catalogRows = updatedRows) else state
        }
    }

    findCatalogItemById(itemId)?.let { enriched ->
        _lastEnrichedPreview.value = enriched
        _enrichedPreviews.update { it + (itemId to enriched) }
    }
}

internal suspend fun HomeViewModel.enrichHeroItemsPipeline(
    items: List<MetaPreview>,
    settings: TmdbSettings
): List<MetaPreview> {
    if (items.isEmpty()) return items

    return coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                try {
                    val enrichment = async {
                        val tmdbId = tmdbService.ensureTmdbId(item.id, item.apiType) ?: return@async null
                        tmdbId.toIntOrNull()?.let { numericId ->
                            runCatching { tmdbService.tmdbToImdb(numericId, item.apiType) }
                        }
                        tmdbMetadataService.fetchEnrichment(
                            tmdbId = tmdbId,
                            contentType = item.type,
                            language = settings.language
                        )
                    }.await() ?: return@async item

                    var enriched = item

                    if (settings.useArtwork) {
                        enriched = enriched.copy(
                            background = enrichment.backdrop ?: enriched.background,
                            logo = enrichment.logo ?: enriched.logo,
                            poster = enrichment.poster ?: enriched.poster
                        )
                    }

                    if (settings.useBasicInfo) {
                        enriched = enriched.copy(
                            name = enrichment.localizedTitle ?: enriched.name,
                            description = enrichment.description ?: enriched.description,
                            genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else enriched.genres
                        )
                    }

                    if (settings.useDetails) {
                        enriched = enriched.copy(
                            runtime = enrichment.runtimeMinutes?.toString() ?: enriched.runtime,
                            status = enrichment.status ?: enriched.status,
                            ageRating = enrichment.ageRating ?: enriched.ageRating,
                            country = enrichment.countries?.joinToString(", ") ?: enriched.country,
                            language = enrichment.language ?: enriched.language
                        )
                    }

                    if (settings.useReleaseDates) {
                        enriched = enriched.copy(
                            releaseInfo = enrichment.releaseInfo ?: enriched.releaseInfo
                        )
                    }

                    enriched
                } catch (e: Exception) {
                    Log.w(HomeViewModel.TAG, "Hero enrichment failed for ${item.id}: ${e.message}")
                    item
                }
            }
        }.awaitAll()
    }
}

internal fun HomeViewModel.replaceGridHeroItemsPipeline(
    gridItems: List<GridItem>,
    heroItems: List<MetaPreview>
): List<GridItem> {
    if (gridItems.isEmpty()) return gridItems
    return gridItems.map { item ->
        if (item is GridItem.Hero) {
            item.copy(items = heroItems)
        } else {
            item
        }
    }
}

internal fun HomeViewModel.heroEnrichmentSignaturePipeline(
    items: List<MetaPreview>,
    settings: TmdbSettings
): String {
    val itemSignature = items.joinToString(separator = "|") { item ->
        "${item.id}:${item.apiType}:${item.name}:${item.backdropUrl}:${item.logo}:${item.poster}"
    }
    return buildString {
        append(settings.enabled)
        append(':')
        append(settings.language)
        append(':')
        append(settings.useArtwork)
        append(':')
        append(settings.useBasicInfo)
        append(':')
        append(settings.useDetails)
        append("::")
        append(itemSignature)
    }
}
