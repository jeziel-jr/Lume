# app/src/main/java/com/nuvio/tv/core/streams/

## Responsibility

Define, import, compile, and apply Fusion-style badges to addon stream results, plus the small
presentation settings model for badge placement, addon logos, and file-size badges.

## Design and patterns

`StreamBadgeRules` is a serializable value model. `normalized()` trims URLs, drops unusable imports,
deduplicates case-insensitively, caps imports at three, and guarantees one active source. The parser
decodes unknown-tolerant JSON, removes filters without a usable name or pattern, and maps optional
visual fields into `StreamBadgeFilter`.

`StreamBadgeMatcher` compiles enabled Java regex patterns from active imports. It extracts a literal
hint for a cheap case-insensitive pre-screen, reuses a `ThreadLocal<Matcher>` across candidates,
and searches filename, torrent, parsed media, stream metadata, and addon fields. Matching badges
are deduplicated by image URL or name. `StreamBadgePresentation` caches compiled filters and a
canonical badge pool, applies groups on `Dispatchers.Default`, and merges existing and generated
badges without duplicates.

## Flow

Settings arrive from `StreamBadgeSettingsDataStore` or an explicit rules value. Rules are normalized
and compiled once per value, then each `AddonStreams` group is copied with matching badges attached
to its streams. Invalid regexes are skipped; an empty compiled set returns the original groups
unchanged. Existing badges are retained and merged in insertion order.

## Integration

The stream screen consumes the transformed `AddonStreams` list and `StreamBadgeSettings` controls
its rendering. `StreamBadgeConfigServer` imports and edits the same rules through its JSON API and
web page. Domain `Stream` and `StreamBadge` models provide the candidate text and output objects;
DataStore persistence supplies the active settings.
