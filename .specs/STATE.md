# Project State

## Decisions

### AD-001: Xtream owns the visible catalog

- Status: active
- Decision: the configured Xtream inventory is the source of truth for Home, Search, Catalog, details availability, and playback sources. TMDB may enrich provider-backed titles, but cannot introduce a visible title by itself.
- Rationale: every visible card must represent content that actually exists on the authorized server; this eliminates TMDB/provider collisions such as a future release being matched to an older same-title asset.

### AD-002: Shared and personal credentials have separate lifecycles

- Status: active
- Decision: the application-level TMDB key and default Xtream server are compiled into `BuildConfig`; Xtream username/password are configured per device at runtime and encrypted with an Android Keystore-backed key.
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

- Feature: Xtream-authoritative catalog, QR-first per-device setup, server-health diagnostics, TMDB enrichment, grouped playback sources, Frame 24 TV identity, and episode skip segments
- Phase: Xtream-authoritative Home/Search/Catalog/details/playback implementation complete in source; release build/install verification and user-owned visual/playback validation pending
- Completed in `0.8.0-beta`: Home and Search now originate from the Xtream VOD/series inventory; adult/XXX/ReelsShort categories are excluded; duplicate provider variants collapse into one card and remain selectable under `Fontes`; a root `Catalogo` destination exposes provider categories; movie TMDB identities are persisted and hydrated resumably in the background with concurrency two and retry; TMDB is detail enrichment only; the setup DNS default is `https://capone.icu`.
- Completed in `0.7.26-beta`: mandatory QR-first Xtream setup with an editable default server and remote-entry fallback, Android Keystore-backed per-device credentials, authenticated one-time local submissions, on-TV confirmation, settings reconfiguration, dynamic Xtream client/cache invalidation, and TMDB-wide four-request concurrency with bounded `429` retry.
- Completed in `0.7.27-beta`: polished mobile setup page, default-locked DNS field with an explicit pencil edit action, persisted provider status/expiration/connection metadata, and a TV Settings profile category with account refresh and confirmed credential removal.
- Completed in `0.7.28-beta`: dedicated full-width remote-control setup, larger focus-aware fields whose keyboard opens only after OK, permanently visible profile refresh/sign-out actions, and sign-out that retains local catalog caches.
- Completed in `0.7.29-beta`: separate account and playback-server health, on-demand movie/series media probes from the already loaded Xtream catalog, persisted credential-free health results, and contextual player diagnosis that distinguishes provider responses, access/rate limits, local connectivity, and app/device format rejection.
- Runtime `0.7.29-beta`: `versionCode=1053` `fullRelease`, built with the authorized local debug-signing mode, verified as non-debuggable with a valid v2 signature, and reinstalled via `adb install -r` on the Fire TV at `192.168.0.7:5555`; existing app data was preserved and the process remains closed.
- Verified in `0.7.29-beta`: the 7 new server-health classifier/aggregation tests plus 21 focused playback-resolver/setup-cache tests pass; `compileFullDebugKotlin` and `assembleFullRelease` succeed; APK and installed package metadata both report `com.jeziel.lume` `0.7.29-beta` (`1053`). The full pre-existing unit suite still has unrelated red tests/timeouts in player, TMDB, collections, and poster-options areas.
- Migration note: previously compiled Xtream username/password values are intentionally not migrated. On the next manual launch, this installation must complete the QR setup once before Home.
- Completed: Frame 24 Lume branding, full-bleed dark TV banner, legacy-compatible square launcher mipmaps, transparent in-app wordmark, branded notification icon, Amazon Appstore 1280x720 Fire TV asset, TMDB catalog/search/details/episodes, playback-aware Home filtering with progressive page fill, automatic Home refilter after Xtream index readiness, localized/original-title availability matching, conservative movie playback fallback for missing/zero provider TMDB IDs, strict rejection of positive mismatched provider TMDB IDs, `Indisponível`/`Verificando` search-poster badges, conservative Xtream matching, 42 progressive Home rails, non-blocking Xtream bootstrap, single-pass two-map title index, background-priority index construction, persistent availability, focused-card prefetch, Leanback-only launcher entry, public IntroDB activation, build-configured Anime-Skip fallback, TMDB-to-IMDb skip resolution, shared internal/external player skip lookup, and stale-episode response protection
- Previous runtime: `0.7.28-beta` (`versionCode=1052`) `fullRelease` introduced the dedicated remote-entry layout before server-health diagnostics.
- Previous verified behavior: the `0.7.25-beta` playback/catalog checks include the real `Barbie e o Segredo das Fadas` provider shape (`stream_id=91811`, `tmdb_id=0`); launcher evidence remains at `design/brand/lume/fire-tv-sideload-validation.png`.
- Fire TV constraint: the official launcher uses the 1280x720 Fire TV App Icon uploaded through Amazon Developer Console for the rectangular card. A sideloaded APK that is not associated with an Amazon Appstore listing falls back to the embedded square launcher icon. The submission-ready asset is `design/brand/lume/lume-fire-tv-appstore-icon-1280x720.png`.
- Known issue: during the first no-cache bootstrap, Home may briefly preserve the initial TMDB page and search temporarily shows availability as `Verificando`; once the background Xtream index becomes ready, loaded Home rails are automatically refetched and filtered with at most three concurrent catalog loads. Runtime visual QA remains user-owned because the release was intentionally installed without launching it. The full pre-existing unit-test suite and lint baseline remain non-green outside the focused availability scope.
- In progress: user-owned server-health UI validation, Home filtering/search-badge visual validation, skip-button/auto-skip/credits playback validation, and cold-launch timing validation
- Next step: manually launch the closed release; confirm Home removes unmatched titles after the short index-ready transition, Search labels genuinely unmatched titles, and `Barbie e o Segredo das Fadas` now resolves through the conservative provider-ID fallback; then validate a mapped series episode for `Pular Abertura`, `Pular Resumo`, `Pular Créditos`, auto-skip settings, and credits-triggered next episode; independently record first-content, download, serialization, index, ready, and navigation-jank evidence
- Blockers: a rectangular card in the official Fire TV launcher requires an Amazon Appstore listing; no listing currently exists
- Uncommitted files: complete feature working tree, intentionally not committed
- Branch: `dev`
