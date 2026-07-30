# app/src/full/java/com/nuvio/tv/core/

## Responsibility
The full core overlay supplies the enabled feature policy, plugin orchestration, CloudStream adaptation, and lifecycle runtime hooks required by full-flavor extension execution.

## Patterns
- `AppFeaturePolicy` is a compile-time capability table shared code can inspect without runtime flavor branching.
- Plugin services are singleton coroutine boundaries around local storage, remote extension code, and normalized scraper results.
- Runtime initialization is lazy, and scraper work uses bounded concurrency, background dispatchers, and cancellation-aware flows.

## Flow
Lifecycle setup records application/activity state. `PluginManager` reconciles repositories, dispatches JavaScript or DEX execution, and returns `LocalScraperResult` values. `PluginRuntimeHooks` prepares shared CloudStream HTTP state only when plugin or Cloudflare work needs it.

## Child responsibilities
- `plugin/` owns repository persistence, scraper execution, QuickJS bridging, and CloudStream extension adaptation.
- `runtime/` owns lazy Conscrypt, HTTP client, cookie jar, and lifecycle initialization.

## Integration
The core consumes local plugin storage, auth/sync, TMDB metadata, Hilt, QuickJS, and CloudStream compatibility APIs. Its results feed shared playback and plugin UI code.
