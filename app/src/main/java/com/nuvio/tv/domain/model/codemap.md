# app/src/main/java/com/nuvio/tv/domain/model/

## Responsibility

Provides the shared domain vocabulary for addon catalogs, metadata, collections, playback, subtitles, watch state, library state, profiles, and app settings. These types are the boundary between remote DTOs, local stores, core services, and Compose UI.

## Design

- Catalog and source values include `Addon`, `CatalogDescriptor`, `CatalogRow`, `Collection`, `CollectionSource`, and `ContentType`. `ContentType` and catalog helpers normalize provider type strings, page sizes, stable row keys, skip offsets, and duplicate pages.
- Metadata values include `Meta`, `MetaPreview`, `PersonDetail`, `Video`, credits, companies, links, trailers, and release dates. `Meta.watchableEpisodes()` excludes specials, future episodes, and seasons whose first episode is unavailable. Language helpers normalize ISO and country values.
- Playback values include `Stream`, `AddonStreams`, `StreamBehaviorHints`, client/debrid resolution details, `Subtitle`, and `LocalScraperResult`. Stream helpers select direct URLs, detect torrent or YouTube sources, derive hashes and file indexes, and create stable list keys. Local scraper results convert directly to `Stream`.
- Personal state includes `WatchProgress`, `NextToWatch`, `WatchedItem`, `LibraryEntry`, `LibraryEntryInput`, `SavedLibraryItem`, list membership values, and Trakt review values. Progress derives percentages, completion, resume positions, and remaining time; library snapshots convert stored entries back to previews.
- Settings and presentation values include TMDB, MDBList, debrid, theme, font, layout, poster, discovery, experience, profile, and card-depth enums or data classes. Defaults and normalizers keep persisted or legacy values usable.
- `Plugin.kt` models native and external scraper repositories, manifests, scraper capabilities, and local results. `CollectionSource` is sealed so addon, TMDB, and Trakt sources remain explicit.
- Most UI-facing values are Compose `@Immutable` and use immutable lists or maps. No model performs network, storage, or navigation work, although a few models depend on core debrid constants and the shared language display helper.

## Flow

Remote mappers and local stores construct these values. Repository flows expose them to view models, which select rows, details, streams, library tabs, and continue-watching content for Compose. Core player and synchronization services update `Stream`, `WatchProgress`, `WatchedItem`, and related values. Derived helpers enforce local policy at the handoff: catalog pages deduplicate by type and ID, stream values distinguish playable and deferred sources, and progress switches between explicit remote percentages and local position/duration.

## Integration

`data.remote`, `data.local`, and `data.mapper` translate provider payloads into these types. Domain repositories expose them to Home, Search, Details, Library, Collection, Stream, Player, Settings, and Account view models. `core` services consume the same models for TMDB enrichment, Xtream playback, Trakt and Supabase synchronization, plugin execution, debrid presentation, and subtitle selection.
