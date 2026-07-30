# app/src/main/java/com/nuvio/tv/ui/screens/collection/

## Responsibility

Provides the TV UI for managing local collections, editing their folders and
sources, and browsing one folder's catalog content. Collections are local
personal state. A collection contains ordered folders, and a folder contains
ordered addon, TMDB, or Trakt sources plus presentation preferences.

## Design and patterns

- `CollectionManagementViewModel` and `CollectionEditorViewModel` expose
  immutable `StateFlow` UI state to Compose screens. Mutations use
  `CollectionsDataStore`, then notify `CollectionSyncService`.
- The editor is a state-driven stack of sub-screens. Folder editing, addon
  catalog selection, genre selection, emoji selection, TMDB source building,
  and Trakt source selection are mutually exclusive picker states.
- Source models remain polymorphic. Addon sources can carry a genre extra;
  TMDB sources support presets, lists, collections, companies, networks,
  people, directors, and Discover filters; Trakt sources retain list sorting
  and direction.
- Compose focus requesters, saved IDs, keyed lazy items, and focus restorers
  preserve D-pad focus after edits, reordering, deletion, and return from a
  picker. Folder detail reuses Home row, grid, and modern presentation code.

## Data and control flow

Management collects the datastore collection flow, renders ordered items, and
routes create or edit actions. Delete and move operations update the datastore
and push a sync request. Import accepts pasted, downloaded, or URL JSON,
validates it before merging by collection ID, and export writes the datastore
JSON to Downloads.

The editor loads installed enabled addons and an optional saved collection.
User input updates a draft `CollectionEditorUiState`; source resolvers search
or resolve metadata, and valid sources are added, replaced, deduplicated, or
reordered in the draft. Saving normalizes blank fields, persists the complete
collection, triggers sync, and returns to management.

Folder detail loads the collection and folder from `SavedStateHandle` IDs,
constructs source tabs plus an optional round-robin All tab, and loads each
source independently. Addon sources use `CatalogRepository`; TMDB and Trakt
sources use their resolvers. Pagination merges unique items, applies the
unreleased-content preference, rebuilds rows or the selected layout, and
reports catalog availability from a shared tracker. Focus, watched state,
hero enrichment, trailer previews, adjacent prefetch, and poster options are
maintained alongside the catalog state.

## Integration

Uses `CollectionsDataStore`, `CollectionSyncService`, `AddonRepository`,
`CatalogRepository`, `TmdbCollectionSourceResolver`, and
`TraktPublicListSourceResolver`. Folder detail also integrates layout and
watch-progress stores, TMDB metadata and ID resolution, external metadata,
trailer playback, `XtreamCatalogAvailabilityService`, and the shared Home
presentation components. Navigation callbacks open title details or folder
details, while poster options provide library and watched actions.
