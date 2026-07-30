# app/src/main/java/com/nuvio/tv/ui/screens/stream/

## Responsibility

Resolves and presents playable sources for a selected movie or episode before handing the chosen source to the player. It supports automatic selection, manual addon filtering, source badges, cached links, and the existing torrent or debrid compatibility paths.

## Design

- `StreamScreen` renders `StreamScreenUiState` from `StreamScreenViewModel`.
- Navigation arguments carry the canonical video ID, content metadata, episode coordinates, and manual-selection flags.
- The ViewModel stages concurrent source loading, merges partial addon responses, applies source badge presentation, and exposes source chips and filtered streams.
- Autoplay and direct-play watchdog policies are isolated from the Compose renderer. Cached links and binge groups reduce repeated source resolution.

## Flow

The screen receives a detail or Continue Watching selection, loads stream groups through `StreamRepository` and enabled providers, then presents source state as responses arrive. Automatic selection may route directly to `PlayerScreen`; manual selection waits for user choice. The chosen stream is cached when appropriate, progress and scrobble context are carried forward, and subtitle or external playback options remain available.

## Integration

Integrates with `StreamRepository`, `MetaRepository`, addon and plugin managers, player settings, stream-link cache, badge presentation, subtitle repository, watch progress, Trakt scrobbling, torrent service, and debrid resolvers. In the current Lume direction, authorized Xtream playback is the supported product path, while the broader provider machinery is retained legacy code.
