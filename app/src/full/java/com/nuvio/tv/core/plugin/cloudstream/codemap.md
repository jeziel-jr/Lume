# app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/

## Responsibility
This package adapts external CloudStream repositories and `.cs3` DEX extensions to Lume's TMDB-ID and `LocalScraperResult` contracts.

## Patterns
- `ExternalRepoParser` accepts a repo manifest with `pluginLists` or a direct plugin array and fetches lists concurrently.
- `ExternalExtensionLoader` downloads size-limited DEX files, uses `DexClassLoader`, manifest lookup plus annotation scanning, reflective wrappers, API/extractor caches, and fallback class scanning.
- `ExternalExtensionRunner` supports `TmdbProvider` JSON loading and text-search providers, with title alternatives, Latin-script filtering, type/year checks, episode extraction, and partial link timeouts.
- `ExternalExtractorRegistry` deduplicates global extractors and records missing domains; `TvTypeExtensions` maps provider types to `movie` or `tv`.

## Flow
Repository URL -> parsed plugin entries -> downloaded `.cs3` -> loaded `MainAPI` and extractors -> TMDB metadata or provider search -> `load` and episode selection -> `loadLinks` -> valid HTTP links -> `LocalScraperResult`.

## Integration
`PluginManager` owns repository persistence and calls this package for downloads and execution. The package bridges CloudStream `MainAPI`, `ExtractorApi`, `APIHolder`, and `app.baseClient` with Lume TMDB services and source models.
