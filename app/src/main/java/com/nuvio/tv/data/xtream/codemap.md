# app/src/main/java/com/nuvio/tv/data/xtream/

## Responsibility

Owns authorized Xtream setup state, catalog indexing, playback resolution, persisted
availability, and server-health diagnostics. Xtream is a playback and availability
source, not the discovery or metadata authority.

## Design

- `XtreamCredentialsStore` normalizes the server URL and stores credentials as AES/GCM
  ciphertext backed by an Android Keystore key. Account status and connection metadata
  are stored with the encrypted record; the catalog snapshot contains only a source
  fingerprint.
- `XtreamApiFactory` caches a Moshi Retrofit client per normalized base URL. The remote
  data source supplies credentials to `XtreamApi` calls and implements `XtreamDataSource`.
- `XtreamCatalogRepository` reads or writes a gzip snapshot with schema version 2,
  endpoint/username fingerprint, six-hour TTL, atomic replacement, plausible-size checks,
  and stale-cache preservation. VOD and series are indexed in one background-priority
  pass into separate normalized-title maps. Index generations invalidate resolver caches.
- `XtreamTitleMatcher` removes accents, years, bracketed/media tags, and punctuation,
  while retaining year extraction and quality/language preference scoring.
- `XtreamPlaybackResolver` uses exact normalized title and compatible year candidates,
  performs bounded detail calls, rejects positive mismatched provider TMDB IDs, handles
  missing or zero provider IDs, normalizes mislabeled single-season year suffixes, and
  builds direct movie or episode URLs.
- `XtreamCatalogAvailabilityService` classifies TMDB previews from the local index and
  persisted exact movie or series results. `CatalogAvailabilityTracker` reclassifies
  changing lists after catalog state or availability revisions and emits `UNKNOWN` while
  loading or after catalog errors.
- `XtreamServerHealthMonitor` separately checks account, movie, and series samples,
  classifies HTTP, network, provider, and media responses, and persists credential-free
  health by source fingerprint. A healthy probe followed by player rejection becomes an
  app format failure.

## Flow

1. Setup saves normalized credentials. `XtreamCatalogRepository.initialize()` loads a
   valid snapshot immediately, then refreshes stale data in the background or downloads
   VOD and series lists concurrently on a cache miss.
2. TMDB metadata supplies localized, original, and alternative titles plus the release
   year. Availability classification first uses exact cached TMDB IDs, then conservative
   title matching; playback resolution uses the same local index and fetches provider
   detail only for candidate streams.
3. Series availability probes details and records available season/episode pairs. A
   resolver returns `Available`, `Unavailable`, or `Failure` without guessing across
   ambiguous candidates.
4. Health refresh authenticates the account, samples cached catalog media, probes range
   responses, and updates the combined diagnostic state. Player failures can update one
   media component without hiding the other.

## Integration

- `XtreamPlaybackService` obtains TMDB enrichment through `TmdbMetadataService` and
  exposes domain-facing availability and resolution results.
- `StreamRepositoryImpl` uses `resolveTmdbPlaybackId` to route IMDb or TMDB IDs into this
  path before trying addon or plugin streams.
- `XtreamCatalogAvailabilityService` is consumed by search, Discover, library, and other
  non-Home surfaces for `AVAILABLE`, `LIKELY_AVAILABLE`, `UNAVAILABLE`, or `UNKNOWN`
  badges. Home filtering uses the same local catalog policy without badges.
- Settings and setup use `XtreamCredentialsStore`; diagnostics and player code consume
  `XtreamServerHealthMonitor`. No password is written to catalog or health snapshots.
