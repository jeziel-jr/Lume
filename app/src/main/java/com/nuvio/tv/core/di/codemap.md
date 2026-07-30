# app/src/main/java/com/nuvio/tv/core/di/

## Responsibility

Defines Hilt singleton wiring for Supabase, Retrofit and OkHttp clients, repository interfaces, Xtream playback, torrent services, and profile infrastructure.

## Design

- `SupabaseModule` creates one configured Supabase client with persistent, automatic auth refresh and exposes its `Auth` and `Postgrest` plugins. Its Ktor validator converts eligible Cloudflare or transient refresh responses into the auth retry exception.
- `RepositoryModule` binds domain repository contracts to data implementations and exposes the remote Xtream source through `XtreamDataSource`.
- `NetworkModule` creates shared Moshi and named clients. Clients use `IPv4FirstDns`, common headers, caching and Sentry breadcrumbs where appropriate; TMDB has a four-request dispatcher plus rate-limit interception. Named Retrofit instances isolate service base URLs and credentials.
- `TorrentModule` constructs context-backed settings, the TorrServer binary and API, then the singleton service. `ProfileModule` is a marker because profile classes already have injectable singleton constructors.

## Flow

Hilt builds the singleton graph at application startup. Consumers request domain interfaces or named clients, receive the configured implementation, and call Retrofit APIs through the appropriate client. The Xtream playback resolver is provided with credentials, catalog, availability, and data-source lambdas; debrid, TMDB, Trakt, diagnostics, reports, trailers, skip, and other API bindings are selected by qualifier.

## Integration

- `NuvioApplication` is the `SingletonComponent` root through `@HiltAndroidApp`.
- Data repositories, ViewModels, auth, cloud adapters, sync services, player networking, and settings consume these bindings.
- `BuildConfig`, `LocaleCache`, and `ApplicationContext` provide runtime URLs, keys, language headers, caches, and filesystem-backed services.
- This module is composition only. Request mapping and business decisions remain in data, core, and domain implementations.
