# app/src/main/java/com/nuvio/tv/core/sync/

## Responsibility

Coordinate optional authenticated Supabase synchronization for profiles, settings, addons, plugins,
collections, home catalog layout, library, watch progress, watched items, and Trakt credentials.
State remains local first, while this package provides pull, push, merge, migration, and invalidation
boundaries for linked devices.

## Design and patterns

- Services are Hilt singletons using profile IDs captured at call time, `Dispatchers.IO`, `Result`
  return values, and a shared JWT-refresh-and-retry pattern. Writes use Supabase RPCs and include a
  persisted `SyncClientIdentity` origin ID so realtime events from this client can be ignored.
- `ProfileSettingsSyncService` serializes typed DataStore values into versioned feature JSON, drops
  device-only keys, preserves local catalog and playback entries on import, and suppresses the
  push echo after applying a remote signature. Home settings use `SyncHomeCatalogPayload` and stable
  addon or collection keys, with legacy platform fallback and non-empty remote safeguards.
- Addon and plugin sync honors primary profile inheritance. Collection, library, profile, and Trakt
  credential services serialize domain state, call dedicated RPCs, compare or preserve local state,
  and expose pull results. Credentials are only handled by the Trakt credential service.
- Watch progress and watched items support paged snapshots plus cursor-based deltas. Mutexes make
  delta application single-writer, persisted cursors advance after each page, and last-successful-
  push timestamps protect local unsynced entries from deletion. Watch progress canonicalizes series
  mirrors and avoids synthesizing mirrors from delete-sensitive delta events.

## Flow

`StartupSyncService` observes auth state, keys work by user and active profile, retries a bounded
startup pull, and chooses a six-hour warm path when possible. Broad pulls run independent addon,
plugin, collection, home, and library jobs, then choose Supabase or Trakt watch state according to
the current source setting. Periodic foreground pulls and explicit surface requests reuse the same
services. Remote empty or malformed data generally preserves local data.

`RealtimeSyncInvalidationService` follows auth and profile flows, subscribes to the Supabase
`sync_invalidations` table with retry backoff, filters self-originated and inactive-profile events,
coalesces surfaces for 500 ms, and asks `StartupSyncService` to pull each affected surface.

## Integration

AuthManager supplies account state and JWT refresh; ProfileManager supplies the active profile;
DataStore/preferences classes own local persistence; Supabase Postgrest RPCs and Realtime provide the
remote boundary. Repositories expose syncing flags so UI and local mutation paths do not race
remote application. `HomeCatalogSyncSupport` is the pure key and payload adapter shared by layout
storage and remote sync.
