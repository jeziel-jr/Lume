# baselineprofile/src/main/java/com/

## Responsibility

Top-level namespace directory for the baseline profile test code. It carries no
implementation by itself.

## Package role

The `com` namespace leads to `com.nuvio.tv.baselineprofile`, matching the
namespace used by the test module and the app's baseline profile include filter.

## Flow and integration

Kotlin under this namespace is compiled into the instrumentation artifact. The
only execution path continues through the nested generator package, then into
the target `:app` process during profile collection.
