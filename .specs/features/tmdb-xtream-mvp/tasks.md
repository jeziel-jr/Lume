# Lume TMDB and Xtream MVP Tasks

## Execution Protocol

Implement with `tlc-spec-driven`. Changes remain uncommitted unless the user explicitly requests commits.

**Design**: `.specs/features/tmdb-xtream-mvp/design.md`
**Status**: Done

## Test Coverage Matrix

> Generated from `README.md`, existing `app/src/test/**`, Gradle configuration, and the feature spec.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Matching/domain services | unit | All branches and every matching edge case | `app/src/test/**/xtream/*Test.kt` | `./gradlew :app:testFullDebugUnitTest` |
| TMDB mapping/services | unit | Search mapping and all episode mapping branches | `app/src/test/**/tmdb/*Test.kt` | `./gradlew :app:testFullDebugUnitTest` |
| View models/repositories | unit | Happy, unavailable, and provider-failure paths | `app/src/test/**` | `./gradlew :app:testFullDebugUnitTest` |
| Build configuration/resources | build | Kotlin compilation and APK packaging | none | `./gradlew :app:assembleFullDebug` |

## Parallelism Assessment

| Test Type | Parallel-Safe? | Isolation Model | Evidence |
| --------- | -------------- | --------------- | -------- |
| JVM unit | Yes | Mocked dependencies and pure fixtures | Existing MockK and coroutine tests in `app/src/test`. |
| Android build | No | Shared Gradle outputs | Single build workspace. |

## Gate Check Commands

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | Domain/service changes | `./gradlew :app:testFullDebugUnitTest` |
| Full | Repository and view-model integration | `./gradlew :app:testFullDebugUnitTest :app:compileFullDebugKotlin` |
| Build | Phase completion | `./gradlew :app:testFullDebugUnitTest :app:assembleFullDebug` |

## Execution Plan

### Phase 1: Foundation

`T1 -> T2`

### Phase 2: Catalog and Playback

`T2 -> T3 -> T4`

### Phase 3: Product Integration

`T4 -> T5 -> T6`

## Task Breakdown

### T1: Add Lume Build Configuration

- **What**: Add validated build-time credentials and Lume application identity.
- **Where**: `app/build.gradle.kts`, `local.example.properties`, ignored `local.properties`.
- **Depends on**: none
- **Requirements**: CFG-01
- **Tests**: build
- **Gate**: Build
- **Done when**: blank credentials fail clearly; configured Kotlin compilation succeeds; no secret is tracked.

### T2: Add Xtream API and Resolver

- **What**: Implement DTOs, API calls, compact index, normalization, safe matching, and URL construction.
- **Where**: `data/remote/api/XtreamApi.kt`, `data/xtream/**`, tests.
- **Depends on**: T1
- **Requirements**: PLAY-01, PLAY-02
- **Tests**: unit
- **Gate**: Quick
- **Done when**: matching movie/episode, unavailable, ambiguity, normalization, ranking, and failure paths pass.

### T3: Complete TMDB Catalog Behavior

- **What**: Add movie/series title search and generate series episodes from TMDB.
- **Where**: `TmdbApi.kt`, `TmdbMetadataService.kt`, related models and tests.
- **Depends on**: T2
- **Requirements**: CAT-01, CAT-02
- **Tests**: unit
- **Gate**: Quick
- **Done when**: search rows and complete episode metadata map correctly.

### T4: Integrate TMDB Details and Xtream Streams

- **What**: Make TMDB details primary and emit Xtream results through existing stream contracts.
- **Where**: detail view model, stream repository/module, tests.
- **Depends on**: T3
- **Requirements**: CAT-02, PLAY-01, PLAY-02
- **Tests**: unit
- **Gate**: Full
- **Done when**: movie and episode play requests resolve correctly and error states remain distinct.

### T5: Replace Legacy Startup, Home, and Search Flows

- **What**: Open directly to TMDB Home, expose TMDB search, and remove legacy provider/account routes from the UX.
- **Where**: `MainActivity.kt`, Home/Search view models, navigation/sidebar, tests.
- **Depends on**: T4
- **Requirements**: CAT-01, APP-01, APP-02
- **Tests**: unit
- **Gate**: Full
- **Done when**: clean startup opens Home and primary navigation contains only Lume destinations.

### T6: Apply Branding and Build APK

- **What**: Apply Lume strings/resources, update documentation, and package the debug APK.
- **Where**: resources, manifest, README, settings.
- **Depends on**: T5
- **Requirements**: APP-01, CFG-01
- **Tests**: build
- **Gate**: Build
- **Done when**: tests pass and an installable Lume APK is generated.

## Validation Checks

| Task | Depends On | Diagram Shows | Test Type | Status |
| ---- | ---------- | ------------- | --------- | ------ |
| T1 | none | start | build | OK |
| T2 | T1 | T1 -> T2 | unit | OK |
| T3 | T2 | T2 -> T3 | unit | OK |
| T4 | T3 | T3 -> T4 | unit/integration | OK |
| T5 | T4 | T4 -> T5 | unit/integration | OK |
| T6 | T5 | T5 -> T6 | build | OK |
