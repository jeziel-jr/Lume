# baselineprofile/

## Responsibility

Standalone Android test module for collecting Lume startup baseline profiles. It
does not contain runtime application features.

## Structure

- `build.gradle.kts` configures the Android test, Kotlin Android, and baseline
  profile plugins, SDK levels, variants, and macrobenchmark dependencies.
- `src/main/java/` contains the instrumentation test package and generator.
- No resources or additional source sets are currently present.

## Flow

The generator runs a macrobenchmark against the configured target application,
records startup execution, and emits profile data. The test presses Home, starts
the target, waits for its package to appear, then allows a short startup window.

## Integration

The module is included as `:baselineprofile` and targets `:app`. The app applies
the baseline profile plugin and declares `baselineProfile(project(":baselineprofile"))`.
App configuration saves generated profiles, merges them into the main variant,
and filters collected methods to `com.nuvio.tv.**`.
