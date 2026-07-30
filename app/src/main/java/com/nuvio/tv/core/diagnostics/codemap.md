# app/src/main/java/com/nuvio/tv/core/diagnostics/

## Responsibility

Controls optional Sentry crash reporting and adds scrubbed HTTP breadcrumbs for the app's shared network clients.

## Design

- `SentryInitializer` starts once, reads the initial setting off the main thread, and observes `SentrySettingsDataStore.enabled` for runtime enable or close changes.
- Sentry is configured from `BuildConfig` with no default PII, screenshots, view hierarchy, tracing, or automatic activity lifecycle tracing. A `beforeSend` callback removes request and user data and drops configured noisy issue text.
- `SentryNetworkBreadcrumbInterceptor` wraps OkHttp calls, records status, timing, host, path, and bounded error details, removes query and fragment values from breadcrumb URLs, and maps outcomes to info, warning, or error levels.

## Flow

`NuvioApplication.onCreate()` starts the initializer. Enabling with a configured DSN initializes Sentry and tags the scope with app and build metadata; disabling closes it. Shared OkHttp clients check Sentry state, time each request, record a breadcrumb on success or failure, then return or rethrow the original result.

## Integration

- `NuvioApplication` provides lifecycle startup and the settings datastore.
- `core.di.NetworkModule` installs the breadcrumb interceptor on the main and direct-debrid clients; other derived clients inherit or add their own interceptors.
- `BuildConfig` supplies DSN, environment, and release identity. Sentry remains opt-in through persisted settings.
