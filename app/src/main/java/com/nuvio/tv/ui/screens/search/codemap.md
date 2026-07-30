# app/src/main/java/com/nuvio/tv/ui/screens/search/

## Responsibility

Provides explicit TMDB search and addon Discover browsing. Search displays
catalog rows for a submitted query, suggestions and recent queries. Discover
lists installed addon catalogs, filters by type, catalog, and genre, and
normalizes IMDb previews through TMDB before they enter the grid.

## Design and patterns

- `SearchViewModel` owns a `StateFlow<SearchUiState>` and a sealed
  `SearchEvent` input API. New submissions cancel prior work, while suggestion
  lookup is debounced and limited.
- Search rows are keyed by stable catalog identity and updated incrementally.
  Discover uses a visible list plus a pending reveal list, with 30-item initial
  and reveal batches. Identity-based deduplication prevents duplicate IMDb or
   TMDB entries.
- `CatalogAvailabilityTracker` observes search and Discover items and exposes
  checking, playable, or unavailable status to cards. Focus requesters and
  saved row or grid positions support D-pad restoration after detail navigation.
- The screen supports remote keyboard entry, voice recognition with runtime
  microphone permission, native completion suggestions, recent-history
  selection, explicit submit, and focus transfer to results.

## Data and control flow

Query edits update draft state, cancel active search jobs, clear stale rows when
the submitted query differs, and request TMDB name suggestions after a short
debounce. Submit saves valid history, clears prior catalog maps, calls
`TmdbCatalogService.search` in `pt-BR`, publishes rows, and submits their items
for availability classification. Row pagination calls the catalog repository
with the search extra and merges pages by catalog identity.

When Discover is enabled, the screen ensures addon descriptors are loaded.
Changing type, catalog, or genre resets page and visible state, then fetches the
selected catalog. The first 30 deduplicated items are localized immediately;
additional raw items remain pending until the user chooses Show More or the
next page is fetched. IMDb IDs are resolved through `TmdbService`, producing a
`tmdb:` identity, configured-language metadata, artwork, and conservative
alternative titles. Navigation and poster options retain the selected addon
base URL for playback lookup.

## Integration

Search integrates TMDB catalog search, localized preview resolution and
settings, installed addons, `CatalogRepository`, search-history persistence,
watch-progress state, and Xtream availability classification. Compose cards
route to detail and See All, while the shared poster-options controller handles
library and watched actions. Discover is available in Search or as its own
screen and uses `HeroBackdropState` for seamless detail return transitions.
