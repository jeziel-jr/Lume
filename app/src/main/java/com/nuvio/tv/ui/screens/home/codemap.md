# app/src/main/java/com/nuvio/tv/ui/screens/home/

## Responsibility

Owns Home's orchestration and its Classic, Grid, and Modern TV presentations.
It combines TMDB catalog discovery, installed addon catalogs, local
collections, Continue Watching, watch state, playback availability, hero
metadata, trailers, and Home personalization controls into one navigable
surface.

## Design and patterns

- `HomeViewModel` is a Hilt ViewModel with immutable `HomeUiState` and separate
  focus states. Large responsibilities are split into catalog, presentation,
  Continue Watching, and library-action pipeline files.
- Catalog data is kept in synchronized keyed maps and an item index, then
  transformed into ordered `HomeRow` and `GridItem` models. Saved catalog order,
  hidden rows, custom titles, pinned collections, and addon order are applied
  before rendering.
- Loading is progressive. TMDB starts with six core definitions, addon rows
  have a bounded eager load and viewport-triggered placeholders, and catalog
  requests use a semaphore. Failed rows do not discard successful rows.
- `ModernHomePresentation` maps rows into cached stable carousel models. The
  three renderers share `CatalogRowSection`, card components, availability
  badges, and D-pad focus restoration, while each keeps its own scroll state.
- Focused-item work is debounced or cancelled. TMDB and external metadata,
  trailer lookup, image prefetch, stable backdrop state, and placeholder to
  real-row focus restoration avoid unnecessary recomposition and stale UI.

## Data and control flow

After the active profile is ready, the ViewModel observes layout preferences,
addons, collections, TMDB settings, library state, auth notices, and progress.
It restores Continue Watching from disk, then combines local or remote progress
with next-up seeds, watched episodes, and settings. Next-up resolution and rich
metadata run off the UI thread with caches, bounded concurrency, partial
publishes, release-aware filtering, and persisted snapshots.

Catalog loading rebuilds order from addon descriptors and collections. The
TMDB playable loader fills the initial six rows and later refilters loaded rows
when the Xtream index becomes ready. Addon catalogs publish successful rows
incrementally; lazy rows load when near the viewport. Each update applies
custom titles and unreleased filtering, computes hero candidates, collection
rows, grid items, and the modern presentation.

The screen holds a startup stability gate, then selects Classic, Grid, or
Modern. Classic renders a vertical list of hero, Continue Watching, catalogs,
and collections. Grid renders a sectioned adaptive grid with sticky headers.
Modern renders a cached vertical list of horizontal carousel rows, selected
hero metadata, optional expanded cards, trailers, and collection hero videos.
Clicks navigate to details or See All, long press opens poster options, and
library or watched actions update state optimistically where appropriate.

## Integration

TMDB services own Home discovery, metadata, localization, and playable catalog
loading. `AddonRepository` and `CatalogRepository` supply installed addon
catalogs. Xtream playback and `CatalogAvailabilityTracker` classify visible
non-Home items, while Home rows remain filtered and badge-free. Local stores
provide layout, collections, library, watch progress, profile, player, TMDB,
Trakt, MDBList, and Continue Watching cache state. Meta and trailer services
enrich focused content, `TvRecommendationManager` updates Watch Next, and
shared Compose components render cards, heroes, rows, dialogs, and poster
options.
