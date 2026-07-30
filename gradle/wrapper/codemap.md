# gradle/wrapper/

## Responsibility

This directory makes the project's Gradle toolchain reproducible. It pins the
Gradle distribution and carries the wrapper bootstrap JAR used by the checked-in
`gradlew` and `gradlew.bat` launchers. It does not configure Android plugins,
dependencies, application variants, or source compilation.

## Design

`gradle-wrapper.properties` is the human-readable wrapper configuration:

- `distributionUrl` selects the Gradle `8.13` binary distribution.
- `distributionBase` and `zipStoreBase` use `GRADLE_USER_HOME`, keeping the
  distribution and downloaded ZIP outside the repository.
- `distributionPath` and `zipStorePath` place cached wrapper data under
  `wrapper/dists` in that Gradle user home.
- `networkTimeout=10000` bounds distribution download operations.
- `validateDistributionUrl=true` enables URL validation before downloading.

`gradle-wrapper.jar` contains the wrapper implementation that reads these
properties, obtains the configured distribution when it is not cached, and
starts Gradle. The JAR and properties are a pair and should be updated together
when the project Gradle version changes.

## Flow

1. A developer or CI invokes `./gradlew` on POSIX or `gradlew.bat` on Windows.
2. The launcher locates the repository root, checks for a usable Java runtime,
   and starts `gradle/wrapper/gradle-wrapper.jar`.
3. The wrapper JAR reads `gradle-wrapper.properties`, validates the configured
   distribution URL, and downloads Gradle `8.13` into the user's wrapper cache
   if necessary.
4. The selected Gradle runtime evaluates root `settings.gradle.kts`, loads the
   conventional `gradle/libs.versions.toml` catalog, and includes the app,
   baseline profile, and FFmpeg decoder projects.
5. Gradle evaluates the root and relevant module build scripts, then executes
   the requested task with the project-wide settings from `gradle.properties`.

The wrapper scripts pass command-line arguments through unchanged. JVM and
Gradle options can also come from the environment, while repository-local
`gradle.properties` supplies shared project defaults such as the Gradle heap,
AndroidX usage, and Kotlin code style.

## Integration

- The wrapper is the supported entrypoint for root and module tasks, including
  `:app:assembleFullDebug`, `:app:testFullDebugUnitTest`, and
  `:app:lintFullDebug`.
- Once the runtime starts, `settings.gradle.kts` controls project inclusion and
  repository policy. The wrapper itself does not choose the `full` or
  `playstore` app flavor.
- `build.gradle.kts` and `app/build.gradle.kts` provide plugin application,
  Android configuration, build types, flavors, dependencies, signing, and
  BuildConfig values. The wrapper only supplies the Gradle runtime that
  evaluates those files.
- `gradle/libs.versions.toml` is loaded by the Gradle runtime after bootstrap;
  its plugin and library aliases are consumed by the root, app, baseline
  profile, and FFmpeg module scripts.
- A wrapper upgrade should regenerate the wrapper JAR and properties using the
  intended Gradle version, then verify that root and app tasks still resolve the
  Android Gradle Plugin and catalog aliases correctly.
