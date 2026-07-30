# app/src/full/java/com/nuvio/tv/core/plugin/

## Responsibility
This package owns full-flavor plugin repository lifecycle, scraper persistence, bounded execution, and the QuickJS bridge that converts CloudStream-compatible extensions into Lume source results.

## Patterns
- Repository URLs are sanitized, canonicalized, deduplicated, and classified as Nuvio manifests or external repositories.
- Scraper execution is enabled/type-filtered, limited by a semaphore and outer timeout, deduplicated by single-flight keys, and streamed with a result cap.
- JavaScript and DEX paths share `LocalScraperResult`; the runtime provides bounded fetch, Cheerio methods, polyfills, settings, TMDB key injection, and cancellation cleanup.

## Flow
Repositories are added or reconciled, manifests are fetched, JS or DEX extensions are downloaded and persisted, matching scrapers execute, and duplicate stream URLs become incremental results. Authenticated remote sync is debounced, mutex-protected, and deferred during an active pull.

## Child responsibilities
- `cloudstream/` parses external manifests, loads `.cs3` DEX files, runs provider APIs, resolves episodes, and adapts extractors and links.

## Integration
The manager consumes `PluginDataStore`, auth, sync, TMDB, and CloudStream child services. Hilt injects it through `PluginModule`, and shared playback/plugin UI consumes its settings and `LocalScraperResult` flows.
