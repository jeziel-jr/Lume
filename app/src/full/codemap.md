# app/src/full/

## Responsibility
The `full` source set supplies the sideload flavor overlay for CloudStream-compatible extensions, in-app updates, and the enabled full feature policy while preserving the contracts consumed by shared Lume code.

## Patterns
- The Gradle flavor selects these implementations in place of the Play Store stubs, using the same `com.nuvio.tv` package names.
- Hilt owns singleton plugin services, while lifecycle hooks and extension runtime setup remain lazy until plugin work is requested.
- Compatibility code keeps CloudStream package names at the boundary and returns Lume `LocalScraperResult` values to shared playback code.

## Flow
Application and activity lifecycle events establish the extension context. Repository definitions are reconciled into local scraper records, JavaScript or DEX extensions produce stream results, and the updater selects and installs a compatible GitHub APK through Android's package installer.

## Child responsibilities
- `java/` contains the `com.lagradost` compatibility namespace and Lume-owned full integrations.
- The CloudStream subtree supplies extension-facing globals, persistence, networking, and plugin registration shims.
- The `com.nuvio.tv` subtree supplies feature policy, plugin orchestration, runtime hooks, DI, and updater state/UI.

## Integration
The overlay compiles with `app/src/main` and connects to shared domain, data, TMDB, authentication, sync, Hilt, Android lifecycle/package APIs, QuickJS, OkHttp, and CloudStream library registries. `AppFeaturePolicy` enables plugins, in-app updates, in-app trailers, and the IMDb rating logo for this variant.
