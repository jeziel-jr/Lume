# app/src/main/java/com/nuvio/tv/core/recommendations/

## Responsibility

Publish local Continue Watching state to Android TV launcher surfaces. This package adapts
`WatchProgress` and in-app Continue Watching items to the AndroidX TV Provider Watch Next contract.

## Design and patterns

`ProgramBuilder` is an injected singleton that creates stable internal IDs, movie or episode
program types, continuation metadata, artwork, progress, season/episode fields, and an intent URI
back to `MainActivity`. ContentResolver operations are defensive and exception-tolerant. IDs use
`wn_<contentId>` for movies and `wn_<contentId>_s<season>e<episode>` for episodes; the matcher also
recognizes either form when deleting by content id.

`TvRecommendationManager` gates all writes on the Leanback feature, serializes full refreshes with
a coroutine `Mutex`, clears Lume-owned Watch Next rows, and re-inserts only in-progress items.

## Flow

Android TV channel synchronization converts the cached in-progress list to
`ContinueWatchingItem.InProgress`, then calls `updateWatchNextFromCwItems`. The manager builds and
upserts each program through `ProgramBuilder`; removing progress calls the content-id deletion
path. Failures are logged or ignored so launcher integration cannot block app playback.

## Integration

`AndroidTvChannelSyncService` owns the cache-to-launcher timing and invokes this manager after its
channel reconciliation. `MainActivity` receives the generated intent URI and routes the content
back into the app. AndroidX `TvProvider`, the local watch-progress model, and Leanback feature
detection define the integration boundary.
