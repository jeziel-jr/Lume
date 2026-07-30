# baselineprofile/src/main/java/

## Responsibility

Kotlin source root for the module's instrumentation code. The package tree is
kept parallel to the app namespace for profile filtering and test discovery.

## Package role

The only current package is `com.nuvio.tv.baselineprofile`, which contains the
single profile generator class. Intermediate `com` and `nuvio` directories are
namespace containers, not independent modules.

## Flow and integration

The compiled test class uses AndroidX Macrobenchmark and UI Automator to launch
the app under test. Its collected method trace is returned to the baseline
profile Gradle plugin and merged by `:app`.
