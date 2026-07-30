# app/src/main/java/com/nuvio/tv/data/remote/api/

## Responsibility

Provides declarative Retrofit contracts for every remote provider boundary. The
interfaces contain no business logic, caching, mapping, or retry policy.

## Design

- `AddonApi` exposes manifest, catalog, meta, stream, and subtitle resources through
  dynamic `@Url` GET calls.
- `TmdbApi` covers trending, search, external-ID lookup, localized details, credits,
  images, release data, recommendations, collections, seasons, people, companies,
  networks, discover filters, lists, genres, keywords, and alternative titles.
- `TraktApi` covers device OAuth, token refresh/revoke, user data, scrobbling, playback,
  watched/history state, comments, related titles, search, watchlists, and personal lists.
- `XtreamApi` uses `player_api.php` for authentication, VOD and series catalogs, and
  VOD or series detail calls. Its response models normalize mixed numeric/string values.
- `SkipIntroApi` groups IntroDB, AniSkip, ARM ID resolution, and Anime-Skip GraphQL
  contracts. `SeriesGraphApi` also provides TMDB and IMDb episode ratings.
- Other interfaces cover Real-Debrid, Premiumize, Torbox, MDBList, parental guides,
  trailers, donations, GitHub releases/contributors, unique contributions, and issue
  or auth diagnostic uploads.

## Flow

Repositories call suspend methods, inspect `Response.isSuccessful` and response bodies,
then apply provider-specific fallback, retry, or normalization rules. Retrofit handles
serialization through Moshi and applies the configured client, base URL, and interceptors.

## Integration

These contracts are injected into `data.repository`, `data.trailer`, and `data.xtream`.
Small response models declared beside `ParentalGuideApi`, `SeriesGraphApi`, `SkipIntroApi`,
and `TrailerApi` are transport-only types and remain below the repository boundary.
