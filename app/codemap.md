# app/

## Responsibility
`app/` is Lume's Android application module. It assembles the shared TV product, selects the full or Play Store distribution overlay, packages the manifest/resources and native/media stack, and produces the installable application variants.

## Source-set and flavor roles
- `src/main/` is the common product implementation and owns TMDB-first discovery, local state, Xtream availability, playback, startup, and shared UI.
- `src/full/` adds sideload-oriented CloudStream extension loading, plugin orchestration, lazy runtime integration, and in-app update behavior.
- `src/playstore/` supplies package-compatible inert implementations for those full-only capabilities while retaining shared UI and service contracts.
- The `distribution` flavor dimension combines `main` with either `full` or `playstore`; build types provide debug, release, and benchmark variants.

## Build wiring
The module applies the Android application, Kotlin, Compose, Hilt/KSP, serialization, Sentry, and baseline profile plugins. It uses namespace `com.nuvio.tv`, application ID `com.jeziel.lume`, API levels 24 through 36, and Java/Kotlin 11. Build configuration reads required TMDB and Xtream endpoint properties, emits variant `BuildConfig` values, packages one universal ABI APK, and keeps language resources in the base install.

Common dependencies include AndroidX TV/Compose, Navigation, Hilt, Retrofit/OkHttp, coroutines, Coil, DataStore, Media3, local Nuvio engine AARs, subtitle and media support, Supabase, Trakt, and Sentry. Full-only dependencies are isolated in `fullImplementation`, including QuickJS, CloudStream, NiceHTTP, Conscrypt, Jsoup, Jackson, and JavaScript crypto support. Optional native Dolby and FFmpeg decoder paths are wired through CMake, local JNI/AAR assets, or the `:ffmpeg-decoder-downmix` project.

## Runtime wiring
The merged manifest starts `NuvioApplication` and exposes `MainActivity` as the Leanback launcher and `lume` deep-link entry. It also wires `FileProvider`, boot and Android TV channel services, and the external playback keep-alive service. Application startup initializes the shared service graph and selected flavor hooks; the activity drives QR-first Xtream setup, cached catalog startup, navigation, playback results, local progress, and Android TV surface synchronization.

The `:baselineprofile` module targets this application, and generated profiles are merged into the main app variant. Debug, release, and benchmark builds select their corresponding signing, shrinking, package identity, and diagnostics behavior through the module configuration.

## Detailed maps
- [Source-set overview](src/codemap.md)
- [Shared application source](src/main/codemap.md)
- [Full flavor wiring](src/full/codemap.md)
- [Play Store flavor wiring](src/playstore/codemap.md)
- [Baseline profile module](../baselineprofile/codemap.md)
