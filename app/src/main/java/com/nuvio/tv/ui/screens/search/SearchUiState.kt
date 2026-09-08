package com.nuvio.tv.ui.screens.search

import androidx.compose.runtime.Immutable
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.data.xtream.CatalogPlaybackAvailability

@Immutable
data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val catalogRows: List<CatalogRow> = emptyList(),
    val catalogAvailability: Map<String, CatalogPlaybackAvailability> = emptyMap(),
    val discoverLocation: DiscoverLocation = DiscoverLocation.IN_SEARCH,
    val discoverInitialized: Boolean = false,
    val discoverLoading: Boolean = false,
    val discoverLoadingMore: Boolean = false,
    val discoverCatalogs: List<DiscoverCatalog> = emptyList(),
    val selectedDiscoverType: String = "movie",
    val selectedDiscoverCatalogKey: String? = null,
    /** TMDB genre id (as string) currently applied to the Discover content, or null for the catalog default. */
    val selectedDiscoverGenre: String? = null,
    /** Localized TMDB genres offered by the genre picker for the current Discover type. */
    val discoverGenres: List<DiscoverGenre> = emptyList(),
    val discoverResults: List<MetaPreview> = emptyList(),
    val pendingDiscoverResults: List<MetaPreview> = emptyList(),
    val discoverHasMore: Boolean = true,
    val discoverPage: Int = 1,
    val posterLabelsEnabled: Boolean = true,
    val catalogAddonNameEnabled: Boolean = true,
    val catalogTypeSuffixEnabled: Boolean = true,
    val posterCardWidthDp: Int = 126,
    val posterCardHeightDp: Int = 189,
    val posterCardCornerRadiusDp: Int = 12,
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

@Immutable
data class DiscoverCatalog(
    val key: String,
    val addonId: String,
    val addonName: String,
    val addonBaseUrl: String,
    val catalogId: String,
    val catalogName: String,
    val type: String,
    /** False for catalogs that cannot be filtered by genre (trending, on-the-air, static lists). */
    val supportsGenreFilter: Boolean = true,
)

@Immutable
data class DiscoverGenre(
    /** TMDB genre id as string (used as the with_genres value). */
    val id: String,
    /** Genre name localized in the current app language by TMDB. */
    val name: String
)
