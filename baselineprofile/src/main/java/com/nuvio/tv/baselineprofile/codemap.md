# baselineprofile/src/main/java/com/nuvio/tv/baselineprofile/

## Responsibility

Contains `BaselineProfileGenerator`, the module's sole instrumentation test and
the executable definition of the startup profile scenario.

## Design

`@RunWith(AndroidJUnit4::class)` supplies the Android test runner. A
`BaselineProfileRule` wraps one `generate` test and collects startup methods
with `includeInStartupProfile = true`. UI Automator waits for the target package
after launch so the trace covers initial app startup rather than only process
creation.

## Flow

The test presses Home, calls `startActivityAndWait()`, waits up to five seconds
for `com.nuvio.tv`, then sleeps for three seconds. Macrobenchmark records the
execution trace and emits baseline profile data; the test does not read or write
application data.

## Integration

The module targets `:app`, uses AndroidX macrobenchmark, test runner, JUnit, and
UI Automator dependencies, and enables collection only for its `benchmark`
variant. The generator passes `com.nuvio.tv` as the target package. The app's
full flavor declares application ID `com.jeziel.lume`, while its namespace and
profile filter use `com.nuvio.tv.**`; this distinction is part of the current
build configuration.
