# app/src/main/java/com/nuvio/tv/data/mapper/

## Responsibility

Converts remote addon DTOs into domain models and applies the normalization required by
the rest of the app. The functions are top-level extensions and contain no I/O.

## Design

- `AddonMapper` maps manifests, catalogs, resources, behavior hints, catalog extras, and
  Stremio configuration. It trims types, supports string or map resource declarations,
  parses mixed extra values, and removes duplicate extras.
- `CatalogMapper` resolves an item type from the item or catalog, maps poster shape,
  ratings, links, trailers, and records the source addon base URL.
- `MetaMapper` maps full metadata, people, videos, release dates, links, and embedded
  streams. It falls back across `director` or app extras and parses human-readable
  episode runtimes into minutes.
- `MetadataFieldMappers` handles tolerant string/list/map fields, people, behavior hints,
  trailer and YouTube ID deduplication, and release-date envelopes.
- `StreamMapper` preserves stream and client-resolve details, maps nested parsed media
  data, and sanitizes proxy headers by dropping empty values and `Range`.

## Flow

Repositories receive a successful DTO, call the relevant `toDomain` extension, and emit
domain objects. Domain code therefore sees normalized `ContentType`, `PosterShape`,
`Meta`, `Video`, `Stream`, and addon resource models rather than provider shapes.

## Integration

Inputs come from `remote.dto`; outputs are `domain.model` objects. `AddonRepositoryImpl`,
catalog, meta, stream, and subtitle repositories are the main callers. The mapper also
provides the source metadata used by playback and trailer flows.
