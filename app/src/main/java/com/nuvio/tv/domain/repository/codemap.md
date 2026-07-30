# app/src/main/java/com/nuvio/tv/domain/repository/

## Responsibility

Defines the application-facing ports for addon management, catalog and metadata retrieval, playback streams, subtitles, library state, remote account sync, and watch progress. Implementations are free to combine HTTP, local persistence, and provider services behind these contracts.

## Design

- `AddonRepository` exposes installed addons as a `Flow`, fetches manifests as `NetworkResult`, and mutates URL order, enabled state, and membership.
- `CatalogRepository` returns a `Flow<NetworkResult<CatalogRow>>` for one addon catalog page. Inputs carry type, skip, page size, extra arguments, and skip support so pagination stays outside Compose.
- `MetaRepository` supports one-addon, all-addon, and primary-addon lookup plus cache clearing. `StreamRepository` supports incremental grouped results from all addons and a direct single-addon lookup. `SubtitleRepository` returns aggregated subtitles with an optional per-addon progress callback.
- `LibraryRepository` exposes source mode, sync state, items, tabs, membership, local or Trakt list mutations, and explicit refresh. `WatchProgressRepository` exposes local or Trakt-backed flows, optimistic updates, completion/history mutations, episode mapping, and source diagnostics.
- `SyncRepository` wraps account-level sync-code, device-claim, unlink, and linked-device operations as `Result` values.
- All interfaces are provider-neutral ports. Hilt binds singleton data implementations through `RepositoryModule`; callers do not depend on implementation classes.

## Flow

Addon reads combine persisted URLs, names, enabled flags, and a manifest cache revision; manifest fetches update the cache and may trigger remote sync. Catalog calls emit loading, map API metadata to a `CatalogRow`, and preserve pagination state. Metadata lookup checks caches, prioritizes enabled addons by resource and ID prefix, and deduplicates in-flight requests.

Stream lookup first attempts the Xtream playback path when a TMDB identity resolves. Otherwise it queries compatible addons and local plugins concurrently, emits grouped results as each completes, merges duplicates, and annotates debrid availability. Subtitle lookup filters compatible resources and fetches addons in parallel with a per-addon timeout. Library and progress flows switch between local stores and authenticated Trakt services, while writes persist locally, emit optimistic state where needed, and debounce Supabase or Trakt synchronization. Account sync calls map directly to Supabase RPCs and the linked-devices table.

## Integration

Implementations live in `data/repository`, use `data.remote` APIs and `data.local` stores, and are bound by `core/di/RepositoryModule`. Home, Search, Details, Collection, Library, Stream, Player, Settings, Account, startup sync, and external playback tracking consume these ports. Xtream catalog and playback services, TMDB, Trakt, Supabase, addon/plugin managers, debrid services, and subtitle APIs connect through the implementations rather than through the UI.
