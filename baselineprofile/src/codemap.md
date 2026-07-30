# baselineprofile/src/

## Responsibility

Source tree for the `:baselineprofile` Android test module. It separates source
sets from the module-level Gradle configuration.

## Source sets

Only `src/main/` is populated. In this test module it holds instrumentation
code rather than application production code; no `androidTest`, unit-test, or
resource tree is present.

## Flow and integration

Gradle compiles `src/main` with the module's Android test configuration. Its
generator is packaged with the baseline profile test APK, runs against the
`:app` target, and supplies collected profile output to the app's baseline
profile build pipeline.
