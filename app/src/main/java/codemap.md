# app/src/main/java/

## Responsibility

The Kotlin and Java implementation root for the shared `main` source set. It contains the Lume
application entry points and the package hierarchy below `com.nuvio.tv`.

## Design

The package tree separates cross-cutting coordination in `core`, provider and persistence adapters
in `data`, provider-neutral contracts and models in `domain`, and TV presentation in `ui`. Root
classes are limited to application composition and the activity shell. Hilt supplies singleton
services and ViewModels, while `Flow`, `StateFlow`, and `SharedFlow` carry asynchronous state.

## Startup and control flow

`NuvioApplication` is the `@HiltAndroidApp` graph root. It initializes diagnostics, optional plugin
hooks, Android TV synchronization, realtime invalidation, locale state, and the shared image loader.
`MainActivity` owns the Android lifecycle, external player launcher, deep-link intake, setup gate,
profile and settings observation, sidebar shell, and the `NuvioNavHost` entry point.

After setup, UI events select domain catalog or metadata operations. Repositories call remote APIs or
local stores, map results into domain values, and emit them to ViewModels. Core TMDB and Xtream
services enrich, classify, resolve, and prepare playback. Player completion writes progress and can
trigger next-episode navigation and launcher updates.

## Integration

The hierarchy is compiled with the shared resources and manifest in `app/src/main`. Flavor overlays
under `app/src/full` and `app/src/playstore` provide package-compatible replacements where product
capabilities differ. No screen should depend directly on transport DTOs or storage preferences;
those concerns cross this tree through domain models, repository ports, and core services.
