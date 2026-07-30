# app/src/main/java/com/nuvio/tv/core/torrent/

## Responsibility

Local P2P playback infrastructure built around the bundled TorrServer native
binary. It owns the server process lifecycle, the loopback HTTP protocol,
magnet-to-file resolution, stream URL creation, torrent statistics, and local
torrent playback settings.

## Design

- `TorrServerBinary` is a process and health-check adapter. It locates
  `libtorrserver.so` in Android's native library directory, starts it on
  `127.0.0.1:8091`, creates an app-private config directory, detects early
  process death, waits for readiness, and cleans up orphaned or stopped
  processes.
- `TorrServerApi` is a small loopback HTTP adapter using OkHttp and JSON. It
  maps add, stats, drop, and stream operations to `TorrServerFile` and
  `TorrServerStats` value types.
- `TorrentService` is the single-active-stream facade. A `SupervisorJob` plus
  `Dispatchers.IO` owns stats polling, while a `StateFlow<TorrentState>` exposes
  idle, connecting, streaming, and error states to consumers.
- File selection is deliberately defensive for addon index differences:
  exact filename, filename containment, TorrServer's one-based ID offset,
  positional index, then the largest video file. `TorrentException` carries
  user-facing resource messages for setup and stream failures.
- `TorrentSettings` wraps a Preferences DataStore. It exposes a cold
  `Flow<TorrentSettingsData>` and uses asynchronous setters for P2P enablement,
  upload preference, and statistics visibility.

## Flow

1. `startStream` stops the previous torrent, enters `Connecting`, starts or
   reuses TorrServer, and builds a magnet from the info hash plus default and
   caller-supplied trackers.
2. The API adds the magnet and returns a server hash. The service polls
   metadata for up to 15 seconds, resolves the requested file, asks TorrServer
   for `/stream?link=...&index=...&play`, and publishes `Streaming` with the
   local URL.
3. A one-second polling job refreshes speed, peer, seed, and preloaded-byte
   fields while the state is still streaming. `stopStream` cancels polling,
   drops the current torrent, and returns to `Idle`; `shutdown` also stops the
   binary.
4. The player consumes the local URL as an ordinary playback source and maps
   `TorrentState` into player loading, statistics, and error UI.

## Integration

- `core/di/TorrentModule` provides the settings, binary, API, and service as
  application-scoped dependencies.
- `StreamScreenViewModel`, `PlayerViewModel`, and player runtime controllers
  start and stop streams and observe `TorrentState`; playback settings screens
  observe and mutate `TorrentSettings`.
- The binary is packaged through Android `jniLibs`; all server traffic stays on
  the app-local loopback address, and the final URL is intended for ExoPlayer.
- Project state records torrents as a legacy provider path absent from the
  current Lume UX, but the playback controllers retain this integration seam.
