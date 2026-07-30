# app/src/main/java/com/nuvio/tv/ui/components/posteroptions/

## Responsibility
Owns the reusable long-press poster menu. It exposes library membership, watched state, local or Trakt list membership, pending states, and errors for any screen that displays a `MetaPreview`.

## Design

- `PosterOptionsController` is the feature-neutral state owner. It can be injected into a screen ViewModel or hosted by `PosterOptionsViewModel` when the screen has no suitable ViewModel.
- Items are canonicalized to IMDb where possible before library and watch-history operations, avoiding duplicate identities across TMDB and addon IDs.
- Movie watched state uses watch progress. Series watched state uses the watched-series holder and batch episode operations. Library actions support the default library and Trakt list picker modes.
- `PosterOptionsState` is immutable and separates target selection, membership, pending mutations, and list-picker state.

## Flow

The host calls `bind` with a coroutine scope and `show` with the selected preview. The controller observes library source, list tabs, library membership, and watched state, then exposes `StateFlow`. User actions toggle library or watched state, load or edit list membership, and dismiss the menu. Repository results update state, with optimistic series watched updates reverted on failure.

## Integration

Depends on `LibraryRepository`, `WatchProgressRepository`, `MetaRepository`, `TmdbService`, `WatchedSeriesStateHolder`, and domain library models. `PosterOptionsDialog` renders the state and is used by poster/card surfaces in Home, Search, Detail, Library, and collection flows.
