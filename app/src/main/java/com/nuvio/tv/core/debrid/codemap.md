# app/src/main/java/com/nuvio/tv/core/debrid/

## Responsibility

Provider-aware policy and stream adaptation for debrid playback. The package
describes supported providers and capabilities, checks local torrent cache
availability, resolves provider links to playable URLs, selects files from
torrent responses, and formats or filters debrid streams for presentation. It
also contains the small device-authorization result model used by provider
setup flows. Provider HTTP contracts remain in `data/remote`; this package
coordinates them around the domain `Stream` model.

## Design

- `DebridProviders` is a registry and capability matrix. It normalizes provider
  IDs, separates visible services from resolver services, and chooses the
  preferred configured credential. `DebridProvider` and the sealed device-auth
  result types keep provider differences explicit.
- `DirectDebridResolver` is a facade over provider adapters for Torbox,
  Premiumize, and Real-Debrid. It gates resolution through
  `DebridSettings`, deduplicates concurrent requests with a `Mutex`, and keeps
  successful resolved URLs for 15 minutes using a key with a stable API-key
  fingerprint rather than the raw key.
- Provider file selectors share the same ordered strategy: specific filename,
  season and episode pattern, source file index, then the largest playable
  video. `DebridMagnetBuilder` supplies a magnet from an existing URI or an
  info hash and preserves usable tracker metadata.
- `LocalDebridAvailabilityService` and `LocalDebridService` use immutable
  copies of grouped streams. They model cache status as checking, cached, not
  cached, or unknown, and batch normalized hashes through Torbox or Premiumize.
- `DirectDebridStreamFilter` derives structured facts from parsed metadata and
  fallback text, then applies quality, resolution, codec, audio, language,
  size, release-group, sorting, and result-limit preferences. `StreamTextSizeParser`
  defines the shared structured-field and free-text size fallback chain.
- `DebridStreamPresentation` removes inactive or known-uncached managed
  streams, applies preferences, and delegates names and descriptions to
  `DebridStreamFormatter`. The formatter uses the small conditional template
  language in `DebridStreamTemplateEngine` and cached compiled badge rules.
- Hilt `@Singleton` services isolate stateful coordination. The stream
  preparer adds bounded background resolution, prioritizing the autoplay
  candidate and enforcing six preparations per minute and thirty per hour.

## Flow

1. `DebridSettingsDataStore` supplies enabled state, active credentials, and
   provider preferences. Without permission or a suitable credential, cache
   checks and link resolution stop early.
2. Stream groups with local torrent hashes are marked checking and sent as one
   normalized batch to the selected cache-check provider. The resulting
   metadata is copied back into each `Stream` as `StreamDebridCacheStatus`.
3. For a direct or local-torrent stream, `DirectDebridResolver` returns a
   typed success or failure. Local torrents first verify cache state, build a
   provider `StreamClientResolve`, and then use the Torbox or Premiumize
   adapter. Direct provider streams dispatch by provider ID. Successful output
   is copied into the stream URL and behavior hints.
4. Torbox creates a cached-only torrent and requests a file link. Premiumize
   performs a direct download request. Real-Debrid adds a magnet, selects a
   file, retrieves a downloadable link, unrestricts it, and deletes the
   temporary torrent when resolution fails.
5. Repository stream results pass through cache annotation and presentation.
   The preparer can replace matching streams asynchronously, while the filter
   exposes only cached instant candidates and the configured preference order.

## Integration

- Consumes `DebridSettingsDataStore`, domain `Stream`, `AddonStreams`,
  `StreamClientResolve`, cache-status models, and `DebridSettings`.
- Calls `TorboxApi`, `PremiumizeApi`, and `RealDebridApi` with their DTO file
  models and maps all provider failures to the package's typed resolve result.
- `StreamRepositoryImpl` uses local availability and presentation; player
  stream preparation uses `DirectDebridResolver`, `PlayerSettings`, and
  `StreamAutoPlaySelector`.
- Badge formatting integrates with `core/streams` compiled badge rules. The
  provider registry is also consumed by debrid settings and cloud-library
  adapters.
- The current product state treats debrid as a legacy provider path that is not
  exposed in the Lume UX, although these common stream and cloud integration
  seams remain implemented.
