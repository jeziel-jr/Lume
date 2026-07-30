# Project State

## Decisions

### AD-001: TMDB owns the catalog

- Status: active
- Decision: TMDB is the source of truth for discovery, search, metadata, artwork, seasons, and episodes.
- Rationale: Xtream is used only to resolve playback availability.

### AD-002: Shared and personal credentials have separate lifecycles

- Status: active
- Decision: the application-level TMDB key is compiled into `BuildConfig`, the setup flow supplies the editable default Xtream server, and Xtream username/password are configured per device at runtime and encrypted with an Android Keystore-backed key.
- Rationale: one APK can be shared without exposing the owner's playback credentials, while TMDB remains an application-level integration.

### AD-005: Xtream setup is local and QR-first

- Status: active
- Decision: an unconfigured installation must complete Xtream setup before Home. The TV serves a short-lived local page opened by QR, validates submitted credentials, and requires confirmation on the TV before persisting them.
- Rationale: phone entry avoids typing long credentials with a TV remote without adding a cloud account or backend.

### AD-003: Personal state remains local

- Status: active
- Decision: library, playlists, and watch progress use local persistence without account synchronization.
- Rationale: The app has one user and no cross-device synchronization requirement.

### AD-004: Legacy providers are absent from the UX

- Status: active
- Decision: Stremio addons, plugins, debrid, torrents, Supabase login, and Nuvio setup flows are not exposed by Lume.
- Rationale: Lume is focused exclusively on TMDB browsing and authorized Xtream VOD playback.

## Handoff

- Feature: QR-first per-device Xtream setup, server-health diagnostics, TMDB rate-limit resilience, playback-aware catalog, global non-Home availability badges, Discover playback identity, Frame 24 TV identity, and episode skip segments
- Phase: catalog availability, localized Discover previews/playback, skip-segment, and server-health implementations complete; `0.7.32-beta` release installed and verified on Fire TV, with user-owned visual/playback validation pending
- Completed in `0.7.32-beta`: Discover no longer renders the raw English Cinemeta preview before availability classification. IMDb cards are resolved through TMDB's localized external-ID response and replaced with the TMDB identity, configured-language title, localized poster, backdrop, description, date, rating, vote count, and original/addon titles as conservative alternatives. The first 30 visible items and each subsequent 30-item reveal batch are localized before entering the grid; failed conversions preserve the original addon preview.
- Availability correction in `0.7.32-beta`: localized Discover cards now classify with exact `tmdb:` identity and Portuguese/original title candidates instead of relying only on the English addon title. The reusable tracker also observes exact playback-cache revisions, so entering details and confirming a title as playable automatically removes a stale `Indisponível` badge after returning.
- Verified in `0.7.32-beta`: 18 focused localized-preview, identity, catalog-availability, cache-revision, stale-classification, and badge-policy regressions pass; `compileFullDebugKotlin` and `assembleFullRelease` succeed. The single universal APK reports `com.jeziel.lume` `0.7.32-beta` (`1057`), has no `debuggable` flag, exposes the Leanback launcher, and has a valid v2 signature.
- Runtime `0.7.32-beta`: the verified `fullRelease` APK was reinstalled with `adb install -r` on the Fire TV at `192.168.0.7:5555`; installed package metadata reports `versionCode=1057`, `versionName=0.7.32-beta`, and `apkSigningVersion=2`. The unchanged `firstInstallTime` confirms preserve-data replacement, and `pidof com.jeziel.lume` is empty, so the app remains closed.
- Completed in `0.7.31-beta`: IMDb movie and Stremio-style episode identifiers opened from Discover now resolve to one TMDB playback identity before Xtream lookup, while direct `tmdb:` identifiers retain their path and genuinely unconvertible legacy identifiers retain the addon fallback. Details no longer assume every non-`tmdb:` identifier is playable. A lifecycle-scoped availability tracker now reclassifies changing and paginated lists after Xtream index updates, cancels stale classifications, shows `Verificando` while loading, and avoids false `Indisponível` results on catalog errors.
- Availability surfaces in `0.7.31-beta`: Search and its See All, Discover, cast/crew filmography, network/company browse, More Like This, TMDB collections/franchises, local/Trakt Library, and local folders/collections in classic, grid, and modern layouts. Trailers and cloud files remain outside this policy. Home and Home-origin See All continue to receive filtered items and intentionally pass no badge state.
- Verified in `0.7.31-beta`: 44 focused identity, catalog-availability, stale-classification, badge-policy, and Xtream playback tests pass; `compileFullDebugKotlin` and `assembleFullRelease` succeed. The single universal APK reports `com.jeziel.lume` `0.7.31-beta` (`1056`), has no `debuggable` flag, exposes the Leanback launcher, and has a valid v2 signature.
- Runtime `0.7.31-beta`: the verified `fullRelease` APK was reinstalled with `adb install -r` on the Fire TV at `192.168.0.7:5555`; installed package metadata reports `versionCode=1056`, `versionName=0.7.31-beta`, `apkSigningVersion=2`, and the original data directory/user ID. `pidof com.jeziel.lume` is empty, so the app remains closed for user-owned validation.
- Completed in `0.7.30-beta`: reverted the Xtream-first catalog experiment and retained the TMDB-first Home/Search/details architecture; VOD now reads the provider `release_date`, so Barbie (2023) matches its real server entry while the provider's `A Odisséia` (2016) cannot satisfy the TMDB `A Odisseia` (2026) candidate. The catalog snapshot schema was incremented to rebuild old cached VOD entries, and the setup DNS default is `https://capone.icu`.
- Verified in `0.7.30-beta`: 37 focused catalog-index, availability, and playback-resolver tests pass, including regressions for Barbie (2023) and the 2016/2026 Odisseia collision; `compileFullDebugKotlin` and `assembleFullRelease` succeed. The single universal APK reports `com.jeziel.lume` `0.7.30-beta` (`1055`), is non-debuggable, and has a valid v2 signature.
- Runtime `0.7.30-beta`: the verified `fullRelease` APK was reinstalled with `adb install -r` on the Fire TV at `192.168.0.7:5555`; installed package metadata reports `versionCode=1055` and `versionName=0.7.30-beta`, existing app data was preserved, and the process remains closed.
- Completed in `0.7.26-beta`: mandatory QR-first Xtream setup with an editable default server and remote-entry fallback, Android Keystore-backed per-device credentials, authenticated one-time local submissions, on-TV confirmation, settings reconfiguration, dynamic Xtream client/cache invalidation, and TMDB-wide four-request concurrency with bounded `429` retry.
- Completed in `0.7.27-beta`: polished mobile setup page, default-locked DNS field with an explicit pencil edit action, persisted provider status/expiration/connection metadata, and a TV Settings profile category with account refresh and confirmed credential removal.
- Completed in `0.7.28-beta`: dedicated full-width remote-control setup, larger focus-aware fields whose keyboard opens only after OK, permanently visible profile refresh/sign-out actions, and sign-out that retains local catalog caches.
- Completed in `0.7.29-beta`: separate account and playback-server health, on-demand movie/series media probes from the already loaded Xtream catalog, persisted credential-free health results, and contextual player diagnosis that distinguishes provider responses, access/rate limits, local connectivity, and app/device format rejection.
- Runtime `0.7.29-beta`: `versionCode=1053` `fullRelease`, built with the authorized local debug-signing mode, verified as non-debuggable with a valid v2 signature, and reinstalled via `adb install -r` on the Fire TV at `192.168.0.7:5555`; existing app data was preserved and the process remains closed.
- Verified in `0.7.29-beta`: the 7 new server-health classifier/aggregation tests plus 21 focused playback-resolver/setup-cache tests pass; `compileFullDebugKotlin` and `assembleFullRelease` succeed; APK and installed package metadata both report `com.jeziel.lume` `0.7.29-beta` (`1053`). The full pre-existing unit suite still has unrelated red tests/timeouts in player, TMDB, collections, and poster-options areas.
- Migration note: previously compiled Xtream username/password values are intentionally not migrated. On the next manual launch, this installation must complete the QR setup once before Home.
- Completed: Frame 24 Lume branding, full-bleed dark TV banner, legacy-compatible square launcher mipmaps, transparent in-app wordmark, branded notification icon, Amazon Appstore 1280x720 Fire TV asset, TMDB catalog/search/details/episodes, playback-aware Home filtering with progressive page fill, automatic Home refilter after Xtream index readiness, localized/original-title availability matching, conservative movie playback fallback for missing/zero provider TMDB IDs, strict rejection of positive mismatched provider TMDB IDs, reusable `Indisponível`/`Verificando` badges outside Home, conservative Xtream matching, IMDb-to-TMDB Discover playback identity, 42 progressive Home rails, non-blocking Xtream bootstrap, single-pass two-map title index, background-priority index construction, persistent availability, focused-card prefetch, Leanback-only launcher entry, public IntroDB activation, build-configured Anime-Skip fallback, TMDB-to-IMDb skip resolution, shared internal/external player skip lookup, and stale-episode response protection
- Previous runtime: `0.7.28-beta` (`versionCode=1052`) `fullRelease` introduced the dedicated remote-entry layout before server-health diagnostics.
- Previous verified behavior: the `0.7.25-beta` playback/catalog checks include the real `Barbie e o Segredo das Fadas` provider shape (`stream_id=91811`, `tmdb_id=0`); launcher evidence remains at `design/brand/lume/fire-tv-sideload-validation.png`.
- Fire TV constraint: the official launcher uses the 1280x720 Fire TV App Icon uploaded through Amazon Developer Console for the rectangular card. A sideloaded APK that is not associated with an Amazon Appstore listing falls back to the embedded square launcher icon. The submission-ready asset is `design/brand/lume/lume-fire-tv-appstore-icon-1280x720.png`.
- Known issue: during the first no-cache bootstrap, Home may briefly preserve the initial TMDB page and search temporarily shows availability as `Verificando`; once the background Xtream index becomes ready, loaded Home rails are automatically refetched and filtered with at most three concurrent catalog loads. Runtime visual QA remains user-owned because the release was installed without launching it. The full pre-existing unit-test suite and lint baseline remain non-green outside the focused availability scope.
- In progress: user-owned validation of localized Discover title/poster and availability accuracy, one Discover movie and episode, Search/See All, cast, network or company, Library/collection, Home badge exclusion, server-health UI, skip behavior, and cold-launch timing
- Next step: manually launch the closed `0.7.32-beta` release and revisit the reported Cinemeta Popular grid, confirming localized titles/posters and that playable titles no longer retain `Indisponível`; then spot-check one series and a later reveal batch
- Blockers: a rectangular card in the official launcher requires an Amazon Appstore listing, and no listing currently exists
- Uncommitted files: the `0.7.31-beta` implementation plus the `0.7.32-beta` Discover correction and handoff documentation remain in the working tree; no commit was requested
- Branch: `dev`
