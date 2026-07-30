# gradle/

## Responsibility

This directory contains shared Gradle build metadata for the multi-module Android
project. Its primary source file is `libs.versions.toml`, which centralizes
dependency versions, library coordinates, and plugin aliases. The
`gradle/wrapper/` subdirectory contains the pinned Gradle runtime configuration
and wrapper bootstrap files.

It is build configuration, not application runtime code. Android module
settings, build types, flavors, local credentials, native toolchain options, and
source dependencies remain in the root and module build scripts.

## Design

`libs.versions.toml` uses Gradle Version Catalog sections:

- `[versions]` names shared versions, including AGP `8.13.2`, Kotlin `2.3.0`,
  Compose, Android TV, Media3, networking, persistence, testing, and build
  tooling versions.
- `[libraries]` maps dependency aliases to Maven coordinates or version refs.
  Groups cover AndroidX and Compose, Hilt, networking, coroutines, image
  loading, navigation, DataStore, Media3, local plugin support, Supabase,
  serialization, testing, and baseline profiling.
- `[plugins]` exposes aliases for Android, Kotlin, Compose, Hilt, KSP,
  serialization, baseline profiles, and Sentry Gradle plugins.

Gradle automatically imports the conventional
`gradle/libs.versions.toml` catalog. The generated `libs` accessor is therefore
available to the root and included project build scripts without a manual
catalog declaration in `settings.gradle.kts`. Version refs keep related plugin
and library versions aligned, while dependencies that require a local fork,
variant-specific behavior, or a project file can remain direct declarations in
the consuming build script.

## Flow

1. Gradle starts with the wrapper and evaluates `settings.gradle.kts`.
2. Settings configures plugin and dependency repositories, then includes
   `:app`, `:baselineprofile`, and `:ffmpeg-decoder-downmix`.
3. The conventional version catalog is loaded and generates `libs.plugins`,
   `libs.versions`, and `libs.<library>` accessors.
4. The root `build.gradle.kts` registers shared plugin versions with
   `apply false`, so modules can apply the same plugins through aliases without
   resolving different versions.
5. `app/build.gradle.kts` applies the application, Kotlin, Compose, Hilt, KSP,
   serialization, baseline profile, and Sentry aliases, then uses catalog
   library aliases for most dependencies. It also uses direct coordinates and
   local AARs where the build requires explicit versions or repository-local
   artifacts.
6. The baseline profile and FFmpeg decoder modules reuse catalog aliases for
   their Android, benchmark, test, and Media3 dependencies. Their module-specific
   Android and native configuration remains local to those build files.

The catalog supplies names and versions only. It does not load
`local.properties`, generate `BuildConfig` values, select flavors, or control
the app's release and debug behavior.

## Integration

- `build.gradle.kts` consumes `libs.plugins.*` to make plugin versions available
  to subprojects without applying them at the root.
- `app/build.gradle.kts` consumes both `libs.plugins.*` and `libs.*`. Its
  `full` and `playstore` flavors, `debug`, `release`, and `benchmark` build
  types, TMDB and Xtream required properties, Sentry environment values,
  Compose configuration, native decoder selection, and signing configuration
  are independent of the catalog.
- `baselineprofile/build.gradle.kts` consumes the Android test, Kotlin,
  baseline profile, benchmark, AndroidX test, and UI Automator aliases.
- `ffmpeg-decoder-downmix/build.gradle.kts` consumes Media3 catalog aliases and
  adds its own CMake and local FFmpeg path configuration.
- `gradle.properties` supplies project-wide Gradle and Android settings, while
  the wrapper pins the Gradle distribution used to evaluate all of these files.

When adding a dependency with a shared version, add its version and coordinate
to the catalog, then use the generated alias in the consuming module. Keep
module-specific coordinates, local AARs, and variant-only dependencies in the
module script when they cannot be represented usefully by the shared catalog.
