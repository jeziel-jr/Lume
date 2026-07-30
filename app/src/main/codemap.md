# app/src/main/

## Responsibility

The shared Android application source set for Lume on Android TV and Fire TV. It contains the
common manifest, resources, startup classes, Compose UI, domain contracts, data implementations,
and cross-cutting services used by every supported product variant.

## Source-set roles

- `main` is the common product implementation and owns the TMDB-first catalog, local state, and
  authorized Xtream playback path.
- `full` overlays optional sideload capabilities such as CloudStream-compatible plugins and the
  in-app updater while keeping the shared package contracts.
- `playstore` overlays disabled or inert implementations for sideload-only capabilities. It keeps
  the same public signatures without plugin runtime, APK installation, or update network work.
- Gradle selects one flavor overlay with `main`; shared code uses common Hilt bindings and feature
  policy rather than variant-specific UI branches.

## Design

The source set is organized as `core`, `data`, `domain`, and `ui`. Hilt builds the application
graph, domain models and repository interfaces define provider-neutral boundaries, data handles
remote and local persistence, core coordinates device and provider policies, and Compose screens
render state through lifecycle-aware `StateFlow` collection. TMDB owns discovery and metadata;
Xtream owns authorized playback availability; local stores own credentials, profiles, watch state,
layout, and settings.

## Startup and flow

`NuvioApplication.onCreate()` starts Sentry according to its setting, plugin runtime hooks, Android
TV channel synchronization, optional realtime invalidation, and the cached locale. Hilt then makes
the singleton graph available. `MainActivity` installs the splash screen, registers the external
player result launcher, detects display capabilities, captures deep links, and composes the TV
shell.

The activity observes credentials, profiles, authentication, theme, layout, and experience state.
Without Xtream credentials it shows the QR-first setup screen. Once credentials exist it initializes
the cached Xtream catalog, then enters the navigation host and Home. Activity lifecycle events also
start or stop foreground synchronization, Trakt refresh, Android TV channel updates, and periodic
surface pulls.

TMDB responses become localized domain catalog rows and metadata. The Xtream index classifies or
filters those rows and resolves selected movies or episodes. Playback updates local progress and
Continue Watching, which can be synchronized or published to Android TV launcher surfaces.

## Integration

The manifest declares the Leanback launcher, deep-link entry, boot receiver, channel job, external
playback keep-alive service, permissions, and application resources. The common source set connects
to AndroidX Compose and Navigation, Media3, Coil, Hilt, TMDB, Xtream, Supabase, Trakt, and the
variant-selected full or Play Store boundaries.
