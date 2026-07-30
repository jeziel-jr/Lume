# baselineprofile/src/main/java/com/nuvio/tv/

## Responsibility

App-aligned namespace root for the baseline profile generator. It keeps profile
collection code separate from Lume runtime sources.

## Package role

`baselineprofile/` is the only child package. The `com.nuvio.tv` prefix is also
the app namespace and the prefix selected by the app's baseline profile filter.

## Flow and integration

The nested generator is discovered by the Android instrumentation runner,
collects a startup trace from the target app, and passes profile output back to
the AndroidX baseline profile plugin used by `:app`.
