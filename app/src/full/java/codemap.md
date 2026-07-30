# app/src/full/java/

## Responsibility
This is the Kotlin package root for the full flavor. Its children provide the external CloudStream compatibility boundary and the Lume-owned plugin, runtime, DI, and updater overlays.

## Patterns
- Variant files retain the package names and method shapes expected by shared code and downloaded extensions.
- Full-only dependencies stay below the flavor boundary instead of leaking into Play Store source compilation.
- Runtime work is lazy, coroutine-based, and routed through Hilt-provided services where the shared app expects an interface.

## Flow
Shared application lifecycle code enters `com.nuvio.tv`. Plugin execution crosses into `com.lagradost.cloudstream3` for extension compatibility and returns normalized scraper results, while update requests remain in `com.nuvio.tv.updater` until installation is handed to Android.

## Child responsibilities
- `com/lagradost/` implements the CloudStream-compatible application, utility, network, and plugin shims.
- `com/nuvio/` contains full-flavor Lume integration packages and their feature-specific children.

## Integration
The full Gradle variant compiles this tree with shared main code and links it to Lume domain/data services, TMDB metadata, auth/sync, CloudStream APIs, QuickJS, OkHttp, Android lifecycle, and package-install APIs.
