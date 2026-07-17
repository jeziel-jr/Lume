# Lume TMDB and Xtream MVP Validation

**Date**: 2026-07-16
**Spec**: `.specs/features/tmdb-xtream-mvp/spec.md`
**Diff scope**: working tree against `HEAD` (`849f7020`), including untracked feature files
**Verifier**: independent verifier, author != verifier
**Verdict**: PASS

## Summary

The findings that caused the first FAIL are fixed. Focused tests pass, the rebuilt universal APK has the expected Lume identity and deep-link declaration, and a scratch-only regression mutation for unknown-year series matching is killed. The supplied emulator E2E evidence confirms the principal clean-install, localized catalog, movie playback, and exact episode playback outcomes on the packaged application.

The unchanged 15 failures in the upstream full suite were observed before this feature implementation and remain pre-existing residual debt. They are not attributed to this diff without contrary evidence.

## Re-Verification Results

| First-FAIL area | Evidence | Result |
| --- | --- | --- |
| TMDB Home pagination | `TmdbCatalogService.kt:87-125` fetches a requested TMDB page; `:140-142` propagates page and `hasMore`; `HomeViewModel.kt:695-720` requests the next page and merges it through `mergeCatalogPage`; `CatalogRow.kt:62-97` appends only IDs absent from the existing row. `TmdbCatalogServiceTest.kt:83-99` asserts page 2 and `hasMore`; existing `CatalogRowPaginationTest.kt:8-34` asserts append/advance behavior. | PASS |
| TMDB Home cached fallback | `TmdbCatalogService.kt:20,31-39` retains the last nonempty Home rows and returns them after a later exception. `TmdbCatalogServiceTest.kt:61-81` primes the cache, makes both APIs throw, and asserts the exact original rows remain. | PASS |
| Unknown-year series matching | `XtreamTitleMatcher.kt:29-33` requires `candidateYear == year` whenever TMDB provides a year. `XtreamPlaybackResolverTest.kt:53-61` asserts a same-title candidate with no year is unavailable. | PASS |
| Provider `releaseDate` year fallback | `XtreamApi.kt:71-84` derives series year from `year`, camel-case `releaseDate`, snake-case `release_date`, then title. `XtreamPlaybackResolverTest.kt:63-75` resolves a 2024 candidate whose only year source is `releaseDate`. | PASS |
| Legacy Settings reachability | `SettingsScreen.kt:141-234` filters the visible Settings model to Appearance, Layout, Playback, Advanced, and About. Account, Profiles, Integration/TMDB/MDBList/Debrid, Trakt, and Debug are absent from the rendered Settings rail. Dormant navigation destinations remain compiled but have no Settings entry point. | PASS |
| `pt-BR` defaults | `NuvioApplication.kt:67-80` assigns `pt-BR` on a clean profile; `MainActivity.kt:277-291` applies the cached locale before composition; `TmdbSettingsDataStore.kt:42-59,76-78` defaults TMDB to enabled, modern Home enabled, and `pt-BR`; catalog/search/detail calls use `pt-BR`. Supplied clean-install E2E observed Home directly in Portuguese with movie and series rails. | PASS |
| Lume deep links | `AndroidManifest.xml:95-103` exposes the browsable `lume` scheme; `DeepLinkParser.kt:19-35` accepts it and maps TMDB links; `DeepLinkParserTest.kt:9-15` asserts `lume://tmdb/movie/1177672` becomes movie `tmdb:1177672`. The packaged manifest also contains `android:scheme="lume"`. | PASS |
| Ranking coverage | `XtreamTitleMatcher.kt:35-49` scores dubbed/dual/national markers above other candidates and adds quality; `XtreamPlaybackResolver.kt:39-46` sorts movie streams by that score. `XtreamPlaybackResolverTest.kt:77-93` asserts the dubbed 1080p URL ranks before a subtitled 720p URL. | PASS |

## Spec-Anchored Evidence

| Criterion | Outcome evidence | Result |
| --- | --- | --- |
| Browse AC 1 | Supplied clean-install emulator E2E reached Home directly in `pt-BR` with movie and series rails. Static request defaults are `pt-BR` in `TmdbCatalogService.kt:22,31,87`. | PASS |
| Browse AC 2 | `HomeViewModel.kt:695-720` plus `CatalogRow.kt:62-97`; page metadata assertion at `TmdbCatalogServiceTest.kt:83-99` and unique merge assertions at `CatalogRowPaginationTest.kt:8-34`. | PASS |
| Browse AC 3 | Exact cached-row equality after API exceptions at `TmdbCatalogServiceTest.kt:61-81`. | PASS |
| Browse AC 4 | Uncached exceptions propagate from `TmdbCatalogService.kt:37-39`; `HomeViewModel.kt:684-690` presents the connection error and its retry event returns to `loadLumeCatalogs`. | PASS |
| Search AC 1 | `TmdbCatalogServiceTest.kt:21-35` asserts separate Filmes and Series rows and TMDB IDs. | PASS |
| Search AC 2 | Supplied emulator E2E opened a known TMDB movie with localized metadata, resolved two Xtream streams, and played in Media3 without errors. | PASS |
| Search AC 3 | `TmdbCatalogServiceTest.kt:37-59` asserts stable generated episode IDs and metadata. Supplied emulator E2E generated T1:E1 with metadata. | PASS |
| Search AC 4 | `TmdbCatalogServiceTest.kt:47-58` retains a future episode and asserts it is unavailable. | PASS |
| Resolve AC 1 | Exact TMDB-ID and direct URL assertions at `XtreamPlaybackResolverTest.kt:21-43`; supplied movie E2E resolved and played the selected title. | PASS |
| Resolve AC 2 | Known-year exact episode assertion at `XtreamPlaybackResolverTest.kt:95-113`, unknown-year rejection at `:53-61`, and provider-date fallback at `:63-75`. Supplied series E2E resolved exact S01E01 with no AndroidRuntime errors. | PASS |
| Resolve AC 3 | Wrong movie ID and missing episode return `Unavailable` at `XtreamPlaybackResolverTest.kt:35-43,115-128`; repository maps unavailable to `Em breve` without a stream. | PASS |
| Resolve AC 4 | `XtreamPlaybackResolverTest.kt:130-136` asserts provider exceptions produce `Failure`, distinct from `Unavailable`; repository preserves the connection-error message. | PASS |
| Resolve AC 5 | Exact first-stream URL assertion for dubbed 1080p over subtitled 720p at `XtreamPlaybackResolverTest.kt:77-93`. | PASS |
| Startup AC 1 | Supplied clean-install E2E reached Home directly; `MainActivity.kt:495-534` bypasses profile/setup gates and starts at Home. | PASS |
| Startup AC 2 | `MainActivity.kt:651-702` defines exactly Home, Search, Library, and Settings; `SettingsScreen.kt:226-234` removes legacy setup sections from visible Settings. | PASS |
| Startup AC 3 | Existing local library/progress implementations remain active without a remote-account startup gate. No contrary runtime evidence was observed. | PASS |

## Focused Gate

- **Command**: `./gradlew :app:testFullDebugUnitTest --tests 'com.nuvio.tv.core.tmdb.TmdbCatalogServiceTest' --tests 'com.nuvio.tv.data.xtream.XtreamPlaybackResolverTest' --tests 'com.nuvio.tv.core.deeplink.DeepLinkParserTest' --rerun-tasks`
- **Result**: 23 passed, 0 failed, 0 skipped.
- **Breakdown**: 4 TMDB catalog tests, 10 Xtream resolver tests, 9 deep-link parser tests.
- **Build command**: `./gradlew :app:assembleFullDebug`
- **Build result**: PASS.

## Full-Suite Baseline

The upstream full suite still has the same 15 failures established before feature implementation: `LocalhostZeroCopyDataSourceTest` (1), `DolbyVisionBaseLayerPolicyTest` (2), `StreamAutoPlaySelectorTest` (1), `TmdbMetadataServiceTest` release-range tests (2), `CollectionsDataStoreSourceMigrationTest` (1), `PosterOptionsControllerShowTest` (5), and `NuvioExoPlayerPerformanceHelperTest` (3).

**Classification**: pre-existing residual debt, not a regression caused by this diff. The focused changed behavior passes and no evidence links these baseline failures to the feature.

## APK Inspection

**Artifact**: `app/build/outputs/apk/full/debug/app-full-universal-debug.apk`

| Check | Result |
| --- | --- |
| Build timestamp | 2026-07-16 15:39:19 -0300 |
| Size | 263,407,820 bytes |
| SHA-256 | `8e373da70a1feebbc4fb1b2e1f49c9bd91a26eaea4d2726af03de39e64688639` |
| Application ID | `com.jeziel.lume.debug` |
| Version | `0.7.17-beta` (`1035`) |
| SDK | min 24, target 36 |
| Manifest identity | label `Lume`, Lume icon/banner, launcher and Leanback launcher |
| Deep link | packaged browsable `lume` scheme |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Signature | APK Signature Scheme v2 verified, one Android Debug signer |

## Discrimination Sensor

**Scratch location**: `/var/folders/kw/7n5t6ypn1bl4y07cscf14pz80000gn/T/opencode/lume-validation-sensor`

| Mutation | Targeted assertion | Result |
| --- | --- | --- |
| Restored unsafe matching by changing the year condition to accept `candidateYear == null` when TMDB supplies a year | `series rejects candidate without a year when tmdb year is known` | KILLED: 1 test executed, 1 failed at `XtreamPlaybackResolverTest.kt:54` |

The real working tree was not mutated by the sensor.

## External Emulator Evidence

The following user-supplied observations are accepted as external E2E evidence:

- Clean install reached Home directly in `pt-BR` with movie and series rails.
- A known TMDB movie opened localized details, resolved two Xtream streams, and played in Media3 without errors.
- A known TMDB series generated T1:E1 with metadata, resolved exact S01E01, and produced no AndroidRuntime errors.

## Remaining Material Gaps

1. **Provider index stale-cache policy remains incomplete.** `XtreamPlaybackResolver.kt:32-38,55-56` caches VOD and series indexes only in process memory. It does not persist the compact index, serve it after process restart, track staleness, or refresh in the background as required by the listed stale-provider-cache edge case and the approved design. This does not invalidate the verified online movie/episode MVP paths, but it remains an explicit unimplemented resilience requirement.
2. **Some UI integration behavior is evidenced externally or statically rather than by repository tests.** There is no automated clean-install navigation test, Settings visibility test, locale cold-start test, HomeViewModel append/retry test, or deep-link-to-detail navigation test. The supplied emulator evidence covers clean startup, localization, details, resolution, and playback, but Settings and full deep-link navigation remain regression risks.

## Requirement Status

| Requirement | Status |
| --- | --- |
| CAT-01 | Verified |
| CAT-02 | Verified |
| PLAY-01 | Verified |
| PLAY-02 | Verified |
| APP-01 | Verified |
| APP-02 | Verified with existing local persistence |
| CFG-01 | Verified by configured build; blank-value failure remains statically evidenced |

**Overall**: READY for the verified TMDB/Xtream MVP scope, with the provider stale-cache requirement and automated UI integration coverage retained as residual debt.
