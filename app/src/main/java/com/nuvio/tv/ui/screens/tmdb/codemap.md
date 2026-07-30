# app/src/main/java/com/nuvio/tv/ui/screens/tmdb/

## Responsibility

Browses a TMDB company or network entity as a localized hero followed by
popular, top-rated, and recent movie or series rails. It is the shared browse
surface for entity routes opened from catalogs, collections, or metadata links.

## Design and patterns

- `TmdbEntityBrowseViewModel` uses a sealed `Loading`, `Error`, or `Success`
  state. Route arguments come from `SavedStateHandle`, including entity kind,
  ID, display name, and source type.
- TMDB data is modeled as a header plus typed rails. Each rail owns page,
  loading, and has-more state. Per-rail in-flight keys prevent duplicate page
  requests, and appended results are deduplicated by item ID.
- The Compose screen keeps per-rail lazy list state and focused indexes,
  restores the item that opened details, and requests initial focus on the first
  rail. A shared availability tracker feeds card badges without changing the
  TMDB result set.

## Data and control flow

Initialization reads the configured TMDB language and calls
`TmdbMetadataService.fetchEntityBrowse`. A response becomes Success; a null or
exception becomes a localized Error with retry. Every successful state submits
all rail items to Xtream availability tracking.

When a rail approaches its end, the screen calls `loadMoreRail`. The ViewModel
marks that rail loading, fetches the next TMDB page in the configured language,
merges unique items, updates page and has-more flags, and leaves existing data
visible if the request fails. Back handling and item selection are delegated to
the route owner.

## Integration

Integrates `TmdbMetadataService`, `TmdbSettingsDataStore`,
`XtreamCatalogAvailabilityService`, and the shared poster-options controller.
The UI uses shared title cards, loading and error states, focus utilities, and
navigation to details. TMDB remains the metadata and discovery source, while
Xtream availability is only a playback diagnostic shown on this non-Home
surface.
