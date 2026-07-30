# app/src/main/java/com/nuvio/tv/core/

## Responsibility

Cross-cutting application infrastructure between Compose screens, domain repositories, local
DataStores, and remote or device APIs. The package owns composition, authentication and setup,
catalog and playback policy, stream transformation, optional synchronization, diagnostics, and
Android TV integration. Domain models remain the contracts; core services coordinate side effects
and adapt external protocols to those contracts.

## Design and patterns

- Hilt singleton services form the application graph. `core.di` supplies qualified Retrofit and
  OkHttp clients, Supabase plugins, repositories, Xtream playback bindings, torrent services, and
  profile infrastructure. `NuvioApplication` is the graph root, while `MainActivity` is the Android
  entry point and navigation owner.
- State is exposed with `StateFlow`, `SharedFlow`, and DataStore `Flow`. IO work uses coroutine
  scopes and dispatchers. `NetworkResult` and `Result` make loading, success, error, and optional
  remote sync outcomes explicit. Mutexes, semaphores, in-flight deferred maps, and bounded retries
  prevent duplicate refreshes or provider overload.
- Core keeps source boundaries explicit: TMDB supplies discovery, metadata, artwork, seasons, and
  episodes; Xtream supplies authorized playback availability; local stores own credentials,
  profiles, watch state, layout, and settings. Matching and playback policies reject ambiguous or
  unsafe fallbacks instead of silently routing to a different title.
- Cross-cutting safety is centralized. Auth and diagnostic payloads filter credential fields,
  logging helpers bound exception and body output, Sentry is opt-in and scrubbed, HTTP clients use
  IPv4-first DNS and breadcrumbs, and local setup encrypts credentials before transmission.
- Pure policies and adapters isolate decision logic from Android: deep-link parsing, stream
  selection and badges, release filtering, debrid file selection, display capabilities, auto-next,
  and catalog key migration are testable without screens. Provider adapters cover TMDB, Trakt,
  cloud/debrid, TorrServer, and local HTTP configuration without leaking their DTOs upward.

## Flow

`NuvioApplication.onCreate()` starts Sentry, plugin runtime hooks, Android TV channel observation,
realtime invalidation when enabled, and the cached locale. Hilt then supplies the singleton graph.
`MainActivity` attaches the external-player result launcher, detects display capabilities, initializes
the Xtream catalog after credentials exist, and gates navigation behind QR-first Xtream setup. It
collects auth, profile, settings, playback overlay, and deep-link flows while starting and stopping
periodic sync with the Activity lifecycle.

The main content path is TMDB API response to localized `CatalogRow` or `MetaPreview`, then optional
Xtream availability classification and pagination expansion. Search, Discover, collections, Trakt
lists, and entity browse use the same domain rows. A selected item resolves through the Xtream index
or a provider-specific stream source, applies source scope, debrid and torrent policies, badge and
formatter presentation, and then reaches internal Media3 or an external player. Playback progress
updates local watch state, Trakt scrobbling where configured, Continue Watching caches, Android TV
channels, and Watch Next programs.

Account and configuration paths run in parallel with content. Auth session events drive account
state and refresh recovery; local QR setup validates Xtream credentials before persistence. Optional
Supabase sync pulls profile, addon, plugin, collection, layout, library, watch, and credential
surfaces by active profile, while realtime invalidations coalesce into targeted pulls. Local state is
preserved when remote data is empty or malformed.

## Integration

Key integration boundaries are:

- `MainActivity`, Compose, Navigation, and ViewModels consume core flows and route deep links,
  external playback results, setup, and launcher intents.
- `AuthManager`, `ProfileManager`, DataStores, and repositories provide identity, local persistence,
  profile scope, and mutation flags. `StartupSyncService` coordinates optional Supabase PostgREST
  and Realtime work.
- `TmdbApi`, `TraktApi`, Xtream clients, cloud/debrid APIs, and `TorrServerBinary` or `TorrServerApi`
  provide remote playback and metadata. `core.network` and `core.di` standardize their clients.
- Media3, Android display APIs, FileProvider, Sentry, Coil, AndroidX TvProvider, JobScheduler, and
  NanoHTTPD connect core behavior to the device, launcher, diagnostics, and local phone setup.
- `core.server` and `core.qr` implement local configuration handoff; `core.player`, `core.debrid`,
  `core.cloud`, `core.torrent`, and `core.streams` converge on playable stream presentation and
  playback; `core.recommendations` and `core.sync.androidtv` publish local watch state to TV
  surfaces.
