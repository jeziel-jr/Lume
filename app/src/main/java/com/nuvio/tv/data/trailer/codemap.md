# app/src/main/java/com/nuvio/tv/data/trailer/

## Responsibility

Resolves a playable trailer from TMDB video metadata and YouTube, then supplies a video
URL plus optional separate audio URL. It also provides a Media3 data source that works
around YouTube adaptive-stream throttling.

## Design

- `TrailerService` gates all lookups on TMDB trailer settings, normalizes language and
  media type, ranks official YouTube trailers before teasers, and tries localized then
  English TMDB video results.
- YouTube IDs use a success-only cache with three-hour fallback TTL or the URL's
  `/expire/<epoch>/` timestamp. A negative title lookup is cached only when a TMDB ID is
  known.
- `InAppYouTubeExtractor` fetches and caches watch-page API configuration, queries several
  YouTube Innertube clients, ranks adaptive and progressive formats, probes CDN hosts,
  and falls back to the backend `TrailerApi` resolver.
- `YoutubeChunkedDataSourceFactory` appends YouTube `range` query parameters in 10 MB
  chunks only for `googlevideo.com`; other URLs pass to `DefaultHttpDataSource` unchanged.
- `TrailerPlaybackSource` is the small transport object shared with player code.

## Flow

TMDB ID or title input -> settings gate -> TMDB video candidates -> YouTube extraction ->
cached `TrailerPlaybackSource`. If extraction fails, the backend resolver returns a
validated URL. Media3 opens the result through the chunked factory for adaptive streams.

## Integration

Uses `TmdbApi`, `TmdbService`, `TrailerApi`, and `TmdbSettingsDataStore`. Player and UI
code consume `TrailerPlaybackSource`; no trailer source is used for Xtream availability.
