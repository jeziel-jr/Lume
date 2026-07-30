# app/src/main/java/com/nuvio/tv/core/sync/androidtv/

## Responsibility

Maintain the Android TV or Fire TV launcher Continue Watching preview channel from the app's
disk-backed Continue Watching enrichment cache.

## Design and patterns

`AndroidTvChannelManager` gates on the Leanback feature, persists one channel ID in a preferences
DataStore, reuses orphaned channels, and creates a browsable preview channel with a MainActivity
app-link. It queries provider rows in memory because Fire OS rejects selection clauses, updates
existing preview programs in place, removes duplicates and stale keys, and explicitly clears absent
art or playback columns.

`AndroidTvChannelSyncService` is an IO singleton. It observes cache version and relevant settings,
debounces changes, skips provider churn while the app is foregrounded, then reconciles in-progress
and aired next-up items. `TvChannelRefreshJobService` obtains Hilt dependencies through an entry
point and schedules periodic or immediate JobScheduler work. `TvChannelBootReceiver` schedules the
immediate job after boot or provider initialization.

## Flow

The Home Continue Watching pipeline writes cached in-progress and next-up snapshots. When the app
backgrounds, when settings change, or when a scheduled job runs, the sync service reads both
snapshots, applies the days cap, dismissed-next-up keys, aired check, thumbnail preference, and
deduplication, then maps items to `WatchProgress`. The manager upserts by content or episode key,
removes rows no longer desired, and requests launcher visibility. The same filtered in-progress
items are forwarded to `TvRecommendationManager` for Watch Next.

## Integration

The cache and layout or Trakt settings are the input boundary; AndroidX TvProvider and
`MainActivity` are the launcher boundary. JobScheduler supplies background execution, Hilt supplies
services through the JobService entry point, and `TvChannelPreferences` preserves channel identity
across process restarts.
