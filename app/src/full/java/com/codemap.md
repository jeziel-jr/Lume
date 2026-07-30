# app/src/full/java/com/

## Responsibility
This package root separates the two full-flavor integration families: `com.lagradost` preserves the CloudStream extension ABI, and `com.nuvio` supplies Lume's full application services.

## Patterns
- CloudStream-facing classes preserve external package names and expose only the compatibility surface needed by extensions.
- Lume classes use compile-time policy, Hilt singletons, coroutines, and flows to connect optional capabilities to shared consumers.

## Flow
Shared lifecycle and feature code enters `com.nuvio.tv`. Plugin orchestration crosses into `com.lagradost.cloudstream3` registries, extension classes, and network helpers, then returns source results to the Lume playback pipeline. Update requests remain within the Lume updater subtree.

## Child responsibilities
- `lagradost/` contains application globals, plugin registration, extension settings storage, and Cloudflare-aware HTTP compatibility.
- `nuvio/` contains feature policy, plugin/runtime services, dependency injection, and updater behavior.

## Integration
The namespace is consumed by `app/src/main` and external `.cs3` extensions. It connects to Android lifecycle, Hilt, BuildConfig, shared domain/data services, CloudStream registries, QuickJS, OkHttp, and package installation.
