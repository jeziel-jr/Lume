# app/src/

## Responsibility
This is the Android source tree for Lume. It combines the shared application implementation with one distribution flavor overlay, plus the Android resources, manifest, and verification source sets used to build the TV application.

## Source-set and flavor roles
- `main/` owns the common Lume product: startup, resources, Compose UI, domain contracts, local state, TMDB discovery and metadata, and authorized Xtream playback.
- `full/` replaces shared capability boundaries with CloudStream-compatible extension execution, lazy runtime hooks, Hilt plugin services, in-app updates, and full trailer policy.
- `playstore/` preserves those package contracts with disabled plugin/runtime and updater implementations. External trailer playback remains available without extension loading or APK installation.
- Gradle merges `main` with exactly one distribution overlay. Same-package policy, DI, runtime, and updater implementations let shared code avoid flavor-specific UI branches.
- `test/` and `androidTest/` exercise the common and variant-selected behavior through JVM and device test configurations.

## Build and runtime wiring
The common manifest declares the Leanback launcher, `lume` deep link, `NuvioApplication`, `MainActivity`, boot/channel synchronization, external playback keep-alive, and `FileProvider`. `NuvioApplication` initializes cross-cutting services and the selected runtime hooks. `MainActivity` owns splash, deep-link, display, credential/setup, navigation, and TV shell coordination.

At runtime, the activity observes local profile, credential, theme, layout, and catalog state. Missing Xtream credentials route to QR-first setup; configured users enter the cached Xtream bootstrap and navigation host. TMDB supplies localized catalog and metadata results, the Xtream index classifies availability and resolves playback, and local progress feeds Continue Watching and Android TV surfaces.

## Integration
The source tree is compiled by the `app` Android application module. Shared code connects to AndroidX Compose and Navigation, Media3, Coil, Hilt, Retrofit/OkHttp, TMDB, Xtream, Supabase, Trakt, and the selected flavor boundary. Flavor overlays add or remove full-only CloudStream, QuickJS, updater, and installer paths without changing the shared application contracts.

## Detailed maps
- [Shared `main` source set](main/codemap.md)
- [Full flavor overlay](full/codemap.md)
- [Play Store flavor overlay](playstore/codemap.md)
