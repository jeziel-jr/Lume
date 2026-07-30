# app/src/main/java/com/nuvio/tv/core/player/

## Responsibility

Playback infrastructure shared by the internal player, external player handoff, trailers, and
device-specific video adaptation. It owns progress and auto-next tracking for external players,
display mode selection, stream auto-selection, subtitle handoff, and Dolby Vision/HDR bitstream
processing.

## Design and patterns

- `ExternalPlaybackTracker` is an application singleton with a `StateFlow` transition overlay and
  replaying `SharedFlow` auto-next event. It persists pending metadata and auto-next state in
  `SharedPreferences` so an external player killing the process does not lose progress handling.
  `ExternalAutoNextPolicy` contains the pure chain-abort, loader, and episode eligibility rules.
- `ExternalPlayerResultContract` and `ExternalPlayerLauncher` build lenient `ACTION_VIEW` intents.
  They support player-specific extras for resume, headers, subtitles, and skip-segment JSON, then
  normalize the varied result keys and completion signals on return.
- `FrameRateUtils` probes NextLib first and `MediaExtractor` second, caches detections by URL and
  sanitized headers, selects a compatible display mode, waits for stable polling, and restores the
  original mode. `DisplayCapabilities` exposes a pure mode-support predicate for UI decisions.
- `DolbyVisionExtractorsFactory` wraps stock Media3 extractors. Video `TrackOutput` instances
  batch samples and send NAL processing to `DoviBridge`; Matroska uses the vendored extractor seam
  in `DolbyVisionMatroskaTransformer`. The pure Kotlin strippers remove DV RPU, enhancement-layer,
  or HDR10+ SEI units when native processing is not used.
- `TrailerPlayerPool` keeps one application-scoped ExoPlayer and yields its codecs for full-screen
  playback. `BitrateAwareLoadControl` applies runtime byte and back-buffer overrides. Stream
  selection filters sources by feature policy, addon/plugin scope, debrid cache state, binge group,
  first-playable, or a user regex.

## Flow

Stream screens call `launchPlayer`, which stores metadata, starts the keep-alive foreground
service, resolves resume progress and optional skip intervals, and launches through the registered
Activity Result launcher or the Zidoo fire-and-forget path. Results are duration-backfilled when
needed, converted into `WatchProgress`, optionally scrobbled to Trakt, and may emit the next
episode event. Zidoo devices instead poll localhost:9529 until two stopped samples confirm the end.

Media3 creates wrapped extractors for MP4, fragmented MP4, TS, or MKV. Track formats determine DV
profile and conversion mode, samples are rewritten into reusable buffers, and codec strings are
updated to advertise the resulting profile. Subtitle URLs are downloaded by `SubtitleFileCache`
and exposed as FileProvider URIs before the external intent is built.

## Integration

The tracker consumes watch-progress and metadata repositories, player settings, skip intervals, and
Trakt mapping/scrobble services. `MainActivity` owns the Activity Result launcher and collects the
auto-next and overlay flows. Media3, Android display APIs, FileProvider, the native `dovi_bridge`
library, and the vendored Matroska extractor are the playback boundaries. Trailer composables read
`LocalTrailerPlayerPool`; stream screens use `StreamAutoPlayPolicy` and `StreamAutoPlaySelector`.
