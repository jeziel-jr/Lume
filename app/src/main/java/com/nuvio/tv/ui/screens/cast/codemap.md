# app/src/main/java/com/nuvio/tv/ui/screens/cast/

## Responsibility

Displays a TMDB person's biography and movie or series filmography, with the same playback availability classification used by other non-Home catalog surfaces.

## Design

- `CastDetailScreen` renders `CastDetailUiState` from `CastDetailViewModel`.
- The ViewModel obtains the person ID, name, and optional crew preference from `SavedStateHandle`.
- Loading, retry, success, and error are modeled as a sealed UI state.
- The shared `CatalogAvailabilityTracker` observes the returned movie and TV credits without blocking the initial metadata response.

## Flow

Navigation supplies a TMDB person route. The ViewModel reads the configured TMDB language, calls `TmdbMetadataService.fetchPersonDetail`, publishes the person detail, and submits credits to Xtream availability classification. Focused cards navigate to the common detail route.

## Integration

Uses `TmdbMetadataService`, `TmdbSettingsDataStore`, `CatalogAvailabilityTracker`, `XtreamCatalogAvailabilityService`, poster option controls, and the shared detail navigation contract. Filmography keeps unavailable content discoverable and presents availability badges instead of filtering the list.
