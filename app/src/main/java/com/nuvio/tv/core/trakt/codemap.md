# app/src/main/java/com/nuvio/tv/core/trakt/

## Responsibility

Support Trakt image normalization and public-list-backed collection sources without making the
collection editor or catalog UI depend on Trakt DTO details.

## Design and patterns

`TraktImageUtils` normalizes absolute, protocol-relative, and host-only Trakt URLs to HTTPS, then
provides ordered best-poster, backdrop, landscape, and logo fallbacks. Empty image entries are
ignored.

`TraktPublicListSourceResolver` is an injected singleton that exposes catalog resolution as
`Flow<NetworkResult<CatalogRow>>`. It normalizes movie/show type and sort direction, uses the
authenticated service's public-request wrapper, honors Trakt pagination headers, and maps movie or
show DTOs into complete `MetaPreview` objects. Import metadata, numeric or URL parsing, search,
trending, and popular list discovery all share the same response and error handling.

## Flow

A `TraktCollectionSource` enters `resolve`, which emits Loading, performs the public API request,
deduplicates by media type and ID, and emits a typed row or localized error. Editor lookup calls
resolve list metadata or searches public lists, then stores a stable source key containing list ID,
media type, sort field, and direction. Preview mapping prefers Trakt IDs, then slug or normalized
fallback IDs, and attaches the normalized artwork and metadata.

## Integration

The collection editor and local collection resolver consume import and search models. `TraktApi`,
`TraktAuthService`, Trakt DTOs, and domain collection or catalog models form the boundaries. The
shared image helpers are also used by Trakt library and metadata flows outside this package.
