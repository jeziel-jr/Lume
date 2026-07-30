# app/src/main/java/com/nuvio/tv/data/repository/

## Responsibility

Implements domain repositories and coordinates remote providers, local stores, sync
services, and caches. It is the main policy layer between transport data and UI-facing
domain flows.

## Design

- `AddonRepositoryImpl`, `CatalogRepositoryImpl`, `MetaRepositoryImpl`,
  `StreamRepositoryImpl`, and `SubtitleRepositoryImpl` normalize dynamic addon resources,
  build encoded URLs, cache manifests or metadata, deduplicate in-flight work, and emit
  incremental `NetworkResult` values. Streams combine addons, local plugins, debrid
  presentation, and the Xtream-first playback route.
- `LibraryRepositoryImpl` switches between local library state and Trakt watchlists or
  personal lists. `TraktLibraryService` maintains a TTL snapshot, paginates lists,
  limits concurrent list loads, and applies optimistic mutations with rollback.
- `WatchProgressRepositoryImpl` joins local watch progress, watched items, Trakt state,
  Nuvio sync, metadata hydration, and profile-aware debounced writes. `TraktProgressService`
  polls activity, caches playback and watched pages, resolves episode mappings, merges
  optimistic state, and protects profile changes with generations.
- `TraktAuthService` owns device OAuth, refresh locking, GET rate limiting, retry rules,
  circuit breaking, remote credential recovery, and authorized request wrappers.
  Scrobbling, comments, related titles, ID parsing, and anime episode mapping are split
  into focused Trakt services and helpers.
- `SkipIntroRepository` bridges TMDB, IMDb, MAL, Kitsu, AniList, IntroDB, AniSkip, and
  Anime-Skip, merging segment categories by provider priority. MDBList and episode-rating
  repositories use settings, TTL caches, and in-flight deduplication.
- `PlaybackIssueReportRepository` and `AuthDiagnosticReportRepository` build bounded,
  sanitized reports. Playback URLs are hashed, sensitive text is redacted, and auth
  reports queue JSONL records for later upload. Supporter, sponsor, contributor, parental
  guide, and sync repositories map small auxiliary service responses.

## Flow

1. Repositories read settings and installed resources, select compatible provider
   candidates, issue bounded concurrent calls, and map successful responses to domain
   models.
2. Catalog and metadata flows preserve partial success. Stream and subtitle flows emit
   results as each source completes, then report aggregate failure only when no source
   produced usable data.
3. Local progress and library changes update the UI optimistically, persist immediately,
   and push to Supabase or Trakt after guarded debounce. Remote pulls merge by stable IDs,
   timestamps, and source compatibility, preserving unsent local work.
4. Trakt progress combines completed history, playback sessions, watched episode sets,
   metadata hydration, and episode remapping into Continue Watching and Next Up state.

## Integration

- Implements interfaces in `domain.repository` and consumes models from `domain.model`.
- Uses `remote.api`, `remote.dto`, `remote.supabase`, `mapper`, and `local` stores.
- Calls core auth, profile, TMDB, plugin, debrid, network, and sync services. Hilt injects
  most classes as singletons so in-memory caches and StateFlows survive screen changes.
- Xtream playback and availability are integrated through `XtreamPlaybackService`; TMDB
  remains the metadata and discovery source for the active product path.
