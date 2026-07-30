# app/src/main/java/com/nuvio/tv/core/tmdb/

## Responsibility

TMDB is the discovery and metadata layer. This package defines Home and collection sources, loads
localized catalog rows, enriches details and episodes, resolves people and entity rails, converts
IMDb and TMDB identities, and expands rows to titles likely playable through the local Xtream index.

## Design and patterns

- `TmdbCatalogService` stores the ordered data-driven Home definitions, maps TMDB responses to
  `CatalogRow` and `MetaPreview`, supports trending, discover, lists, and mixed movie/TV rails, and
  limits concurrent Home requests with a four-permit semaphore. It retains a last successful in-
  memory Home result when refresh fails.
- `TmdbPlayableCatalogLoader` is the availability-aware wrapper. It classifies items through
  `XtreamCatalogAvailabilityService`, keeps available or likely-available matches, expands at most
  five TMDB pages toward twenty items, and leaves the original row while the Xtream index is unknown.
- `TmdbMetadataService` uses language-keyed caches and in-flight `CompletableDeferred`s. Details,
  credits, images, age rating, and alternative titles load in parallel; trailers have localized
  then English fallback ranking. The same service loads episode enrichment, all series videos,
  recommendations, collections, company or network browse rails, and person filmographies.
- `TmdbService` maintains typed bidirectional IMDb/TMDB caches and in-flight de-duplication. It
  parses prefixed or episode-shaped IDs and resolves localized IMDb previews directly from TMDB's
  external-ID response. `TmdbCollectionSourceResolver` exposes source resolution as
  `Flow<NetworkResult<CatalogRow>>`, covering lists, collections, people, directors, companies,
  networks, and discover filters.
- `TmdbRateLimitInterceptor` retries HTTP 429 responses using `Retry-After` or bounded exponential
  backoff with jitter. Image URLs, locale normalization, release dates, and media type mapping are
  centralized in the service helpers.

## Flow

UI or collection configuration chooses a stable source or Home catalog ID. The resolver reads the
configured language, calls `TmdbApi`, maps results, and emits Loading then Success or Error. Home
loads use independent catalog requests, while playable loading filters each fetched page and
continues until the target or page cap. Detail screens request enrichment and episode metadata,
which are merged with addon or TMDB previews by callers. Playback identity conversion uses cached
IMDb/TMDB mappings before Xtream resolution.

## Integration

`BuildConfig.TMDB_API_KEY` and `TmdbApi` are the remote boundary. Domain catalog models feed Home,
Search, Discover, details, collections, cast, company, and network screens. Xtream availability
services consume the stable `tmdb:<id>` identities and provide the playable filter; player and
skip logic consume the generated `tmdb:<id>:<season>:<episode>` video IDs. Settings DataStores
provide language and collection source configuration.
