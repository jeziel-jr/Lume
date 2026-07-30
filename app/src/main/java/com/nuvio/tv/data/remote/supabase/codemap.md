# app/src/main/java/com/nuvio/tv/data/remote/supabase/

## Responsibility

Defines Supabase serialization models and reads the server-managed avatar catalog. The
models cover legacy addon/plugin rows, sync-code linking, TV login, watch progress and
events, library and watched items, profiles and PIN state, settings and collection blobs,
and provider credential blobs.

## Design

- `SupabaseModels.kt` uses Kotlin serialization and `@SerialName` for database column
  names. Defaults make partial rows and event payloads decodable.
- `AvatarRepository` is a singleton with an in-memory last-successful catalog cache. It
  calls the `get_avatar_catalog` RPC, maps storage paths to public URLs, and exposes
  explicit invalidation.

## Flow

Postgrest decodes RPC or table responses into these models. Repositories and core sync
services translate them into local profile, library, watch, addon, plugin, or auth state.
Avatar URLs are built from `BuildConfig.AVATAR_PUBLIC_BASE_URL` and are not fetched here.

## Integration

`AvatarRepository` depends on Supabase Postgrest and is injected into profile flows.
`SyncRepositoryImpl` uses the sync-code and linked-device models. Other sync services use
the watch, library, settings, collection, and credential models. This is a compatibility
backend boundary, not the TMDB catalog source.
