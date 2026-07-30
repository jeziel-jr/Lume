# app/src/main/java/com/nuvio/tv/

## Responsibility

The application boundary for Lume's Android TV experience. Root classes establish process and
activity behavior, while `core`, `data`, `domain`, and `ui` divide infrastructure, adapters,
contracts, and presentation beneath it.

## Design

`NuvioApplication` owns process-level initialization and the shared image loader. `MainActivity`
owns Android lifecycle coordination, setup gating, deep links, external player results, profile and
settings state, the TV sidebar, and the navigation host. Hilt composes singleton services and
ViewModels; domain models and repository interfaces prevent UI code from depending on provider DTOs.

The active product boundary is explicit: TMDB supplies discovery, metadata, artwork, seasons, and
episodes; authorized Xtream supplies playback availability and direct VOD or episode resolution;
local stores supply credentials, profiles, watch state, library state, layout, and settings. Legacy
addon, plugin, Trakt, debrid, torrent, cloud, and Supabase code remains available for compatibility
or supported settings paths, but is not the intended primary discovery UX.

## Startup

The process initializes Sentry, optional plugin hooks, Android TV channel synchronization, optional
realtime invalidation, and the cached locale. The activity installs the splash screen, binds the
external playback result contract, captures launch intents, and collects local and account state.
Missing Xtream credentials route to QR-first setup. Configured credentials trigger cached catalog
initialization, then the activity composes the themed sidebar and `NuvioNavHost` starting at Home.
Foreground and background lifecycle callbacks control sync, Trakt refresh, channel reconciliation,
and external playback transitions.

## Cross-layer flow

1. UI screens request domain operations through ViewModels and repository interfaces.
2. Data repositories combine Retrofit or Postgrest responses with profile-scoped DataStores,
   normalize them through mappers, and emit domain models or `NetworkResult` states.
3. Core services apply TMDB catalog and metadata policy, Xtream availability and playback matching,
   stream selection, profile and sync policy, diagnostics, and Android TV integration.
4. Compose renders catalog rows, metadata, availability state, streams, settings, and playback.
   Selection reaches internal Media3 or an external player, and progress returns to local stores,
   Continue Watching, optional sync, and launcher surfaces.

## Child responsibilities

### `core/`

Cross-cutting coordination and device or provider policy. `auth` handles Supabase sessions and
account diagnostics; `build` holds build and playback policy contracts; `cloud`, `debrid`, and
`torrent` adapt optional remote file sources; `deeplink` parses inbound commands; `diagnostics`,
`logging`, and `network` provide safe reporting and common HTTP behavior; `di` builds the Hilt graph;
`player` owns Media3, external playback, subtitles, display adaptation, and progress transitions;
`plugin` is the optional extension boundary; `profile` owns active profile rules; `qr` and `server`
implement phone-assisted local setup and configuration; `recommendations` publishes Watch Next;
`streams` applies stream badges; `sync` coordinates optional authenticated synchronization; `tmdb`
owns localized catalogs, metadata, identity conversion, and playable catalog filtering; `trakt`
adapts public lists and images; and `util` contains release-date policy helpers.

### `data/`

`remote` defines Retrofit, DTO, and Supabase contracts; `mapper` normalizes provider payloads;
`local` owns profile-scoped DataStores and larger local snapshots; `repository` implements domain
ports and combines APIs, stores, caches, and sync; `xtream` owns encrypted setup, catalog indexing,
availability persistence, playback resolution, and server health; and `trailer` resolves TMDB and
YouTube trailer sources for Media3.

### `domain/`

`model` defines the provider-neutral catalog, metadata, playback, library, watch, profile, and
settings vocabulary; `repository` defines application-facing ports; and `deeplink` defines typed
commands for content opening and addon installation. This layer performs no I/O or navigation.

### `ui/`

`screens` contains feature ViewModels and Compose flows; `navigation` owns route contracts and the
single navigation host; `components` provides shared TV cards, rows, dialogs, badges, loading, and
stream controls; `theme` provides palettes and TV design tokens; and `util` provides focus, DPAD,
formatting, and Compose helpers. UI state is rendered from domain and service flows, not provider
transport objects.

## Integration

The package integrates with the shared manifest and resources, Android TV and lifecycle APIs, Hilt,
Compose Navigation and TV Material, Coil, Media3, TMDB, Xtream, Supabase, Trakt, and the selected
full or Play Store implementation. Core and data are the side-effect boundaries; domain is the
contract boundary; UI is the interaction boundary.
