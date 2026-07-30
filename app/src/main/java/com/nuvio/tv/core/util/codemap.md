# app/src/main/java/com/nuvio/tv/core/util/

## Responsibility

Provide small domain-neutral release-date helpers used to keep future content out of catalog rows.

## Design and patterns

`MetaPreview.isUnreleased(today)` first parses the explicit `released` value as an ISO local date,
ignoring a time suffix. If that is absent or invalid, it parses `releaseInfo` as a full date and then
falls back to a four-digit year in the 1900 to 2099 range. Invalid or missing values are treated as
released. `CatalogRow.filterReleasedItems` is immutable and returns the original row when no item
was removed.

## Flow

Callers provide the comparison date, filter each row's `MetaPreview` list, and retain all row
metadata while replacing only the item list when necessary. No network, clock, or persistence call
is made by these helpers.

## Integration

TMDB and addon catalog loaders populate `released` or `releaseInfo`; Home and collection flows can
apply the row filter before rendering. The utility depends only on domain `MetaPreview` and
`CatalogRow` models and Java time parsing.
