# app/src/main/java/com/nuvio/tv/ui/screens/player/

## Responsibility

Owns playback execution and the TV playback UI: media engine setup, overlays, stream and track controls, subtitles, skip segments, next episode behavior, diagnostics, and playback progress reporting.

## Design

- `PlayerScreen` renders `PlayerUiState` from `PlayerViewModel` and coordinates `PlayerRuntimeController`.
- The runtime controller is split into focused files for initialization, lifecycle, metadata, streams, tracks, subtitle timing, playback events, error recovery, scrobbling, and engine failover.
- ExoPlayer and the custom MPV surface are supported through media-source and rendering helpers. External player and device-specific playback integrations are isolated in `core.player`.
- Overlay composables model transient controls such as pause, loading, audio, subtitles, stream info, display mode, parental warnings, post-play, and next episode prompts.

## Flow

Player navigation receives canonical video and content arguments. The ViewModel and runtime controller prepare the selected source, initialize the configured engine, observe playback events, persist progress, and publish state transitions. Track, subtitle, aspect, frame-rate, audio-delay, skip, and autoplay actions return as `PlayerEvent` values. Playback errors are classified for provider, network, format, or device diagnosis.

## Integration

Connects to `StreamScreen`, `XtreamPlaybackService`, stream and subtitle repositories, watch progress, Trakt scrobbling, IntroDB or Anime-Skip services, `ExternalPlaybackTracker`, media sessions, Android audio and display APIs, and bundled MPV or FFmpeg support. Stream resolution remains upstream of this package; the player consumes a selected playable source.
