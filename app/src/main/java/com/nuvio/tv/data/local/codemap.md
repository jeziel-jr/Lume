# app/src/main/java/com/nuvio/tv/data/local/

## Responsibility

Persists profile settings, local playback state, watch history, libraries, collections,
search history, caches, and startup flags. It exposes typed `Flow` values and suspend
mutators instead of leaking DataStore preferences to repositories or UI.

## Design

- `ProfileDataStore` stores the profile list and active profile. `ProfileDataStoreFactory`
  creates cached per-feature stores, suffixes non-primary profiles, applies migrations,
  resets corrupt files, and clears profile-scoped files safely.
- Most stores use Preferences DataStore with `flatMapLatest` over `ProfileManager.activeProfileId`.
  `AddonPreferences` and `PluginDataStore` can resolve primary profile addon/plugin state
  for profiles configured to share it.
- Settings stores cover TMDB, Trakt auth and behavior, MDBList, debrid, player and
  device-local player preferences, layout and Home catalog controls, theme, experience,
  trailer, anime skip, onboarding, debug, Sentry, session notices, profile locks, and
  startup sync markers. Enum parsing has defaults and setters normalize ranges.
- `PlayerSettingsDataStore` owns migration of legacy audio, subtitle, Dolby Vision,
  buffering, VOD cache, networking, and autoplay keys. It emits one `PlayerSettings`
  object and clamps values before persistence.
- `WatchProgressPreferences`, `WatchedItemsPreferences`, and `LibraryPreferences` use
  Gson JSON maps or sets, preserve display metadata, merge by timestamps, protect local
  entries newer than the last push, and tolerate malformed individual records.
- `WatchedSeriesStateHolder` persists fully watched IDs and per-series revalidation
  deadlines. `ContinueWatchingEnrichmentCache` writes profile-specific atomic JSON
  snapshots with throttled hash checks and version signals.
- `CollectionsDataStore` serializes addon, TMDB, and Trakt sources and validates imports.
  Stream links, binge groups, track choices, subtitle delay, and stream badge rules have
  focused stores with legacy migration where required.
- `RemoteConfigStore` persists the verified remote provider document together with the active
  endpoint and an optional operator override. It is SharedPreferences-backed like
  `XtreamCredentialsStore` because the active endpoint is read synchronously during startup and
  setup, and it stores no secret.

## Flow

1. The active profile selects a feature store. DataStore emits changes as typed flows;
   writes occur through atomic `edit` transactions.
2. Repositories read local state for immediate UI and playback, then sync eligible library,
   watched, and progress data through core sync services. Profile changes switch flows to
   the new store and repository services reset profile-scoped caches.
3. Large or frequently enriched state uses Gson/Moshi/kotlinx serialization, compressed
   or atomic files, or hashed preference keys. Device-local settings intentionally bypass
   profile and remote synchronization.

## Integration

- `ProfileManager` drives profile scoping and shared primary addon/plugin behavior.
- Repository implementations consume these stores for catalog configuration, library,
  watch progress, Trakt auth, streams, player preferences, and diagnostics.
- Core sync services use explicit profile IDs, push timestamps, delta cursors, and remote
  Supabase models. `ProfileDataStoreFactory` is the common persistence boundary.
