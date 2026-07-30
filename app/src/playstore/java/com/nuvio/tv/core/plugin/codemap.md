# app/src/playstore/java/com/nuvio/tv/core/plugin/

## Responsibility
The Play Store `PluginManager` is an API-compatible disabled façade.

## Patterns
- Repository, scraper, enabled-state, and grouping flows are `flowOf` empty or false values.
- Mutating operations are no-ops; repository add/refresh and scraper tests return `UnsupportedOperationException` failures.
- Execution and streaming methods return empty lists or `emptyFlow()`.

## Flow
Shared callers may observe the façade and invoke normal repository or execution methods, but no data is persisted, no remote URL is fetched, and no scraper is run.

## Integration
Hilt constructs it through the Play Store `PluginModule`. It uses shared `PluginRepository`, `ScraperInfo`, `RemotePluginInfo`, `LocalScraperResult`, and `TestDiagnostics` types to preserve signatures.
