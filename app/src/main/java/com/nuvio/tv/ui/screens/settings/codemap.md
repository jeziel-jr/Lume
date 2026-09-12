# app/src/main/java/com/nuvio/tv/ui/screens/settings/

## Responsibility

TV settings presentation and orchestration. The package exposes the settings workspace, standalone settings screens, shared TV controls, picker dialogs, diagnostics, and the ViewModels that persist user choices. It covers appearance, Home and detail layout, playback, integrations, Xtream profile health, account/profile entry points, advanced diagnostics, and attribution screens.

## Design

- `SettingsScreen` owns category selection, inline versus external navigation, essential versus advanced mode, and D-pad focus restoration. Its current default category registry is filtered to Xtream profile, appearance, layout, playback, advanced, and about. The detail dispatcher also contains account, profile, content discovery, integration, experience, and debug content for callers or feature configurations that expose them.
- `SettingsDesignSystem` provides the shared `SettingsWorkspaceSurface`, headers, group cards, action/toggle rows, chips, choice dialogs, and focus-aware TV styling. Classic, Zen, and Horizon styles alter row shapes, grouping, and selection indicators. `SettingsScrollIndicators` decorates lazy and regular scroll containers without owning their state.
- Screens are mostly stateless Compose projections. Hilt ViewModels expose `StateFlow` or `Flow`; screens collect with lifecycle-aware APIs, keep only transient dialog, expansion, QR, and focus state locally, and dispatch writes through ViewModel methods.
- Large areas are split into focused renderers: layout sections, playback sections plus audio, subtitle, autoplay, and buffer/network helpers, and reusable diagnostics cards. Feature policy and player type gate rows instead of duplicating separate settings models.

## Flow

1. Navigation enters `SettingsScreen` or a standalone screen. The root loads active-profile and experience-mode state, derives visible categories, and requests focus on the selected rail or detail entry.
2. A settings screen collects a ViewModel state backed by a DataStore or repository. User actions update the corresponding preference asynchronously in `viewModelScope`; collected flows feed the new value back into the UI.
3. Appearance changes update `ThemeDataStore`, including theme, font, AMOLED, and settings style. Language changes update the app-locale preference and `LocaleCache`, then recreate the host Activity.
4. Layout actions persist Home, Discover, poster, hero, Continue Watching, card-depth, and stream-badge settings. Installed enabled addons supply selectable hero catalogs. The stream-badge QR action starts a local configuration server and persists phone-submitted settings.
5. Playback actions pass through `PlaybackSettingsViewModel` to player, trailer, torrent, and network stores. Section helpers expose conditional controls for player selection, autoplay, subtitles, audio, Dolby Vision, frame-rate and resolution matching, P2P, buffering, parallel connections, and VOD cache. `MemoryBudget` clamps buffer and parallel settings before persistence.
6. Integration screens validate and save TMDB, MDBList, Anime-Skip, and debrid credentials or preferences. Trakt owns a device-code polling state machine and synchronizes source choices and cached watch data. Xtream profile refresh authenticates, updates account metadata, refreshes health, or clears credentials on sign-out, and it also exposes the operator-defined server address: a manual refresh that bypasses the CDN, plus the manual-server indicator and reset action when the hidden operator override is active.
7. Advanced and debug surfaces run network and stream tests, expose last-playback diagnostics, control Sentry and diagnostic flags, clear local caches, generate test library items, and provide development-only sign-in and toggles.

## Integration

The package connects UI navigation callbacks to the Activity graph and uses `NuvioTheme`, `AppFeaturePolicy`, `BuildConfig`, and TV Material components for runtime-gated presentation. Data integrations include local DataStores, `ProfileManager`, addon/plugin repositories, player and trailer services, torrent/debrid services, TMDB and MDBList APIs, Anime-Skip, Trakt auth and sync services, Xtream account and health services, the updater, Supabase-backed account/profile services, and supporter/contributor repositories. QR settings flows use device IP discovery plus short-lived local servers. External attribution, donation, contributor, sponsor, Trakt, and update actions leave the app through Android intents; settings do not directly own playback or catalog business logic.
