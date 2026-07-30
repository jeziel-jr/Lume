# app/src/main/java/com/nuvio/tv/data/remote/dto/

## Responsibility

Models JSON exchanged with addons and remote services. DTOs preserve wire naming and
optional fields so repositories can decide how to handle missing, malformed, or
provider-specific data.

## Design

- Most models are immutable Moshi `@JsonClass(generateAdapter = true)` data classes.
- Addon DTOs represent manifests, catalog previews, full metadata, embedded videos,
  streams, subtitles, trailers, behavior hints, and app extras.
- `TmdbApi.kt` owns the TMDB DTOs for discovery, details, credits, images, release and
  content ratings, recommendations, people, collections, companies, and networks.
- Diagnostic DTOs carry structured auth or playback reports. Playback reports include
  bounded loading, player, format, network, and analytics detail.
- Debrid DTOs model device auth, account/cloud files, cache checks, torrents, and links.
  Torbox uses a generic success/data/error envelope; helper methods resolve variant IDs
  and display names.
- `GitHubReleaseDto`, contribution, donation, and report DTOs cover auxiliary services.
- `Any` is intentional where addon payloads vary, such as director, writer, cast, or
  Xtream auth and numeric fields.

## Flow

Retrofit decodes responses into these types. Repositories validate and map them to domain
models, while `data.mapper` centralizes addon metadata, stream, and manifest conversion.
No DTO is intended to be stored as application state or rendered directly.

## Integration

`remote.api` imports these types for method signatures. `mapper`, repositories, trailer
resolution, diagnostics upload, and Xtream indexing consume them. The nested `trakt` and
`mdblist` packages isolate provider-specific wire schemas.
