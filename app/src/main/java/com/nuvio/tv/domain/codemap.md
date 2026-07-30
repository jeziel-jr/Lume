# app/src/main/java/com/nuvio/tv/domain/

## Responsibility

The domain package defines provider-neutral contracts and value objects used by the rest of the app. It has no direct production Kotlin at its root. Its child packages describe deep-link commands, catalog and playback data, user state, and repository ports.

## Design

- `deeplink/` uses a sealed result model for supported external intents.
- `model/` uses data classes, enums, sealed types, Compose `@Immutable` values, and small normalization or derivation helpers. Models retain provider values such as `rawType` while exposing canonical API values.
- `repository/` exposes interfaces only. Observable state uses `Flow`; one-shot operations use `suspend` functions with `NetworkResult` or `Result`.
- Storage, HTTP, provider selection, and synchronization remain in `data/` and `core/` implementations.

## Flow

External URLs are parsed into `AppDeepLink` values, then routed to metadata navigation or addon installation. Remote DTOs and local persistence are mapped into domain models. Repository implementations expose those models to view models and core services. Model helpers then handle pagination merging, watchable episode filtering, stream classification, resume calculations, and settings normalization without performing I/O.

## Integration

Hilt binds the `data.repository` implementations to these domain interfaces in `core/di/RepositoryModule`. UI view models consume catalog, metadata, library, stream, subtitle, and progress contracts. Player, plugin, debrid, TMDB, Trakt, Supabase, startup-sync, and deep-link services use the domain types as their shared boundary.
