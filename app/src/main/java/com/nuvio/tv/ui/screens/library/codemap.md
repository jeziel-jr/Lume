# app/src/main/java/com/nuvio/tv/ui/screens/library/

## Responsibility

Displays saved local or Trakt library entries and an optional cloud library.
It provides type, list, genre, year, and sort controls, Trakt personal-list
management, cloud provider and type filters, and cloud file playback
resolution.

## Design and patterns

- `LibraryViewModel` combines repository, authentication, preference, and
  settings flows into immutable `LibraryUiState`. Local and Trakt saved modes
  share a faceted filtering pipeline; Cloud is a separate view mode with lazy
  refresh.
- Filter options are derived from the current data with counts that respect
  the other active facets. Stable keys, adaptive grids, saved poster focus, and
  explicit focus requesters preserve D-pad navigation after sorting or sync.
- Dialog state is represented in the ViewModel for Trakt list management and in
  the screen for confirmation and cloud file selection. Mutating operations
  expose pending state and transient or error messages.

## Data and control flow

The ViewModel combines source mode, sync status, library entries, list tabs,
persisted sort, auth, and Trakt authentication. It selects valid list and type
defaults, computes visible items by list, type, genre, and year, then applies
Trakt rank or local deterministic sorting. Preference changes persist sort
selection and cause the grid to restore its first visible focus target.

Trakt actions refresh, open list management, create or edit a personal list,
reorder personal lists, or delete the selected list through `LibraryRepository`.
The screen renders dialogs and disables controls while operations are pending.

Cloud settings are derived from enabled Debrid providers with Cloud Library
capability. A refresh loads provider-backed items, rebuilds provider and type
facets, and preserves valid selections. Clicking an item opens directly when
one playable file exists, opens a picker for several files, or reports an empty
state. Selected files resolve through the cloud repository and return playback
info to the navigation owner.

## Integration

Uses `LibraryRepository`, `LibraryPreferences`, `AuthManager`, Trakt auth and
library services, `CloudLibraryRepository`, Debrid settings and provider
capabilities, and watch-progress state. `CatalogAvailabilityTracker` classifies
saved title cards against the Xtream catalog. Shared grid cards, poster options,
localized type and genre labels, TV dialogs, and navigation callbacks connect
the screen to the rest of the app.
