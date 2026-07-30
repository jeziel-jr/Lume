# app/src/main/java/com/nuvio/tv/ui/screens/detail/

## Responsibility

Presents the full metadata page for a movie or series, including hero metadata, playback actions, seasons and episodes, cast, trailers, collection links, related titles, comments, ratings, and local library or watch state.

## Design

- `MetaDetailsScreen` is driven by `MetaDetailsViewModel` and `MetaDetailsUiState`, with feature sections kept as focused Compose functions.
- Route arguments arrive through `SavedStateHandle` as item ID, type, optional addon base URL, and return-focus information.
- Independent jobs load metadata, seasons, episode ratings, trailers, related titles, collection data, comments, library membership, watch progress, and availability.
- `CatalogAvailabilityTracker` reclassifies related and collection items when Xtream readiness or exact playback-cache revisions change.

## Flow

The ViewModel loads metadata through `MetaRepository` and TMDB services, resolves the effective TMDB playback identity for `tmdb:` and IMDb inputs, and observes local preferences and watch state. Series episode actions route through stream selection or direct playback. Movie and episode play actions pass canonical IDs, season, and episode into the stream or player routes.

## Integration

Uses TMDB metadata and settings, `MetaRepository`, `LibraryRepository`, watch progress, trailers, Trakt comments and related titles, MDBList ratings, `XtreamPlaybackService`, and the Xtream availability service. It is the main bridge from discovery to playback while preserving unavailable details outside Home and showing the approved availability state.
