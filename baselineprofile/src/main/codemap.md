# baselineprofile/src/main/

## Responsibility

The active source set for the baseline profile instrumentation module. Its
Kotlin is compiled into the test artifact used to exercise Lume startup.

## Contents

- `java/` contains the `com.nuvio.tv` package hierarchy.
- There are no resources or manifest-specific feature files in this source set.

## Flow and integration

The test runner loads the generator from `src/main`, invokes a
`BaselineProfileRule`, and records the target app's startup trace. The resulting
profile is consumed by the app module through its `baselineProfile` dependency.
