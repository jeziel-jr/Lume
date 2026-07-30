# Repository Atlas: Lume

## Project Responsibility

Lume is a Kotlin and Jetpack Compose Android TV application for TMDB-first
discovery and metadata, with authorized Xtream VOD used only for playback
availability and stream resolution. Local device state owns profiles,
credentials, library items, and watch progress.

## System Entry Points

- `settings.gradle.kts` and `build.gradle.kts`: module inclusion and shared
  Gradle plugin configuration.
- `app/build.gradle.kts`: application variants, flavors, dependencies,
  BuildConfig values, packaging, and release configuration.
- `app/src/main/AndroidManifest.xml`: Leanback launcher, deep links, services,
  providers, and permissions.
- `app/src/main/java/com/nuvio/tv/NuvioApplication.kt`: process initialization
  and shared image-loader setup.
- `app/src/main/java/com/nuvio/tv/MainActivity.kt`: setup gate, lifecycle,
  navigation host, playback results, and TV shell.

## Architecture and Runtime Flow

The common `main` source set follows layered boundaries. `domain` defines
provider-neutral models and ports. `data` implements those ports with local
storage, remote services, mapping, Xtream indexing, availability, and playback
resolution. `core` contains cross-cutting policy and platform integrations.
`ui` presents immutable ViewModel state through Compose TV screens and routes
actions through navigation and repositories.

At startup, the application restores local configuration. Missing Xtream
credentials route to QR-first setup. Configured installations load a cached
catalog, compose Home, and refresh remote data in the background. TMDB supplies
discovery, localization, artwork, and metadata. Xtream classifies availability
and resolves only selected playback targets. Playback progress returns to local
stores and optional compatible sync or Android TV surfaces.

## Repository Directory Map

| Directory | Responsibility summary | Detailed map |
| --- | --- | --- |
| `app/` | Android application module, variants, packaging, and runtime wiring. | [app/codemap.md](app/codemap.md) |
| `app/src/` | Shared, full, and Play Store source-set organization. | [app/src/codemap.md](app/src/codemap.md) |
| `app/src/main/` | Common product source set and Android manifest. | [app/src/main/codemap.md](app/src/main/codemap.md) |
| `app/src/main/java/com/nuvio/tv/` | Application boundary, startup, and layer integration. | [package map](app/src/main/java/com/nuvio/tv/codemap.md) |
| `app/src/main/java/com/nuvio/tv/core/` | Cross-cutting platform, provider, playback, diagnostics, and policy services. | [core map](app/src/main/java/com/nuvio/tv/core/codemap.md) |
| `app/src/main/java/com/nuvio/tv/data/` | Local and remote data access, mapping, repositories, trailers, and Xtream data. | [data map](app/src/main/java/com/nuvio/tv/data/codemap.md) |
| `app/src/main/java/com/nuvio/tv/domain/` | Provider-neutral models, deep links, and repository contracts. | [domain map](app/src/main/java/com/nuvio/tv/domain/codemap.md) |
| `app/src/main/java/com/nuvio/tv/ui/` | Compose TV components, navigation, feature screens, theme, and focus utilities. | [UI map](app/src/main/java/com/nuvio/tv/ui/codemap.md) |
| `app/src/full/` | Full-distribution implementations for extension and update behavior. | [full map](app/src/full/codemap.md) |
| `app/src/playstore/` | Play Store-compatible implementations of full-only contracts. | [Play Store map](app/src/playstore/codemap.md) |
| `baselineprofile/` | Startup profile generation and app benchmark integration. | [baseline profile map](baselineprofile/codemap.md) |
| `gradle/` | Version catalog and Gradle wrapper configuration. | [Gradle map](gradle/codemap.md) |
| `gradle/wrapper/` | Pinned Gradle distribution bootstrap. | [wrapper map](gradle/wrapper/codemap.md) |

Each detailed map links or summarizes its child directories. Read the nearest
map before changing a component. Required product and runtime decisions remain
in [`.specs/STATE.md`](.specs/STATE.md) and
[`docs/PRD_HOME_CATALOG_ROADMAP.md`](docs/PRD_HOME_CATALOG_ROADMAP.md).
