# app/src/main/java/com/nuvio/tv/data/remote/dto/trakt/

## Responsibility

Defines Trakt wire models for device OAuth, media identity, watch history and playback,
scrobbling, comments, hidden items, progress, and list management.

## Design

- `TraktAuthDtos.kt` models device-code and refresh-token requests plus user settings and
  stats. Token values and device-flow fields remain nullable only where the API can omit
  them.
- `TraktMediaDtos.kt` centralizes movie, show, season, episode, IDs, and artwork models.
- `TraktSyncDtos.kt` models playback, watched movies/shows, episode history, show progress,
  history add/remove payloads, and hidden entries.
- `TraktListsDtos.kt` models watchlist and personal/public list metadata, list items, and
  mutation responses. `TraktCommentsDtos.kt` adds comments, user stats, and search unions.
- `TraktScrobbleDtos.kt` models movie or show plus episode scrobble requests.
- `@Json` names retain Trakt snake_case fields while nullable properties tolerate partial
  responses.

## Flow

`TraktApi` serializes requests and decodes responses. Repository services normalize IDs,
dates, artwork, pagination, and list membership before exposing domain models or state.

## Integration

Consumed by `TraktApi`, `TraktAuthService`, `TraktProgressService`, `TraktLibraryService`,
comments, related-title, scrobble, and episode-mapping services. `TraktIdUtils` supplies
the canonical ID parsing used when these DTOs are converted into local state.
