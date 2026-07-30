# app/src/main/java/com/nuvio/tv/data/remote/

## Responsibility

Defines external data contracts. `api` describes HTTP operations for TMDB, Trakt,
Xtream, addons, diagnostics, debrid services, trailers, and supporting services.
`dto` describes their JSON payloads. `supabase` contains Kotlin serialization models
and the avatar RPC repository for the Postgrest integration.

## Design

- Retrofit interfaces use suspend functions and return `Response<T>` when callers need
  status and body control. Xtream calls return decoded bodies directly.
- Addon endpoints use `@Url` because each installed addon supplies its own base URL and
  path. Authenticated provider APIs pass bearer or API-key values explicitly.
- Moshi `@JsonClass(generateAdapter = true)` DTOs mirror remote names with `@Json` and
  nullable fields for incomplete or inconsistent provider payloads.
- Supabase models use `@Serializable` and `@SerialName`; `AvatarRepository` maps RPC
  rows into a small domain-facing catalog and caches it in memory.

## Flow

1. Hilt supplies configured Retrofit clients, Moshi, and Postgrest.
2. Repositories call an API contract, inspect status where applicable, and map DTOs
   through `data.mapper` or repository-local mapping.
3. Supabase RPC/table responses decode into `SupabaseModels`, while avatar storage paths
   become public URLs from `BuildConfig.AVATAR_PUBLIC_BASE_URL`.

## Integration

- `remote.api` is consumed by repositories, `data.xtream`, and `data.trailer`.
- `remote.dto` is the transport boundary for addon, TMDB, Trakt, Xtream, debrid,
  diagnostics, and contribution flows.
- `remote.supabase` is used by sync, profile, addon/plugin, library, watch-progress,
  and avatar features. It is a compatibility integration and is not the active Lume
  catalog source.
