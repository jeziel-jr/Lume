# app/src/main/java/com/nuvio/tv/data/

## Responsibility

The data layer translates provider responses into domain models, persists profile and
device state, and exposes repository implementations used by the application. It
contains the TMDB and addon integrations, Trakt and Supabase synchronization, local
watch state, trailer playback, and authorized Xtream catalog and playback access.

## Design

- `remote/api` contains Retrofit contracts and `remote/dto` contains wire models.
- `mapper` performs explicit DTO to domain conversion, including tolerant addon fields.
- `repository` coordinates APIs, local stores, domain interfaces, caches, retries, and
  optimistic state. It does not expose provider DTOs to UI code.
- `local` uses profile-scoped Preferences DataStore for user state, with JSON files for
  larger snapshots and migrations for legacy keys.
- `xtream` is a separate playback provider boundary. It indexes provider titles locally,
  uses TMDB identity for matching, and persists availability by endpoint and username
  fingerprint without persisting the password in the catalog snapshot.
- `trailer` resolves TMDB YouTube candidates into Media3-compatible sources.

## Flow

1. Retrofit or Postgrest calls return DTOs or serializable Supabase models.
2. Repositories validate response status, normalize IDs and provider variants, map data
   into domain models, and emit `Flow<NetworkResult<...>>` or repository-specific flows.
3. Local stores provide profile-aware state. Debounced sync services push eligible local
   changes and pull remote changes back into the stores.
4. TMDB supplies discovery and metadata. Xtream credentials produce a cached VOD and
   series index, which feeds availability classification and direct stream resolution.
5. Player and settings code consume domain models, cached watch state, stream sources,
   subtitles, diagnostics, and trailer playback sources.

## Integration

- Domain contracts live under `com.nuvio.tv.domain.repository` and domain models under
  `com.nuvio.tv.domain.model`.
- Hilt constructs repositories, Retrofit APIs, Moshi, Postgrest, profile stores, and
  provider services.
- Core auth, profile, TMDB, plugin, debrid, sync, and network services are repository
  collaborators rather than data-layer-owned UI state.
- The active product path is TMDB-first discovery with authorized Xtream availability;
  legacy addon, Trakt, debrid, plugin, and Supabase code remains represented here for
  supported settings, migration, or compatibility paths.
