# app/src/full/java/com/nuvio/tv/

## Responsibility
This package contains the full-flavor replacements and additions for Lume's feature policy, plugin system, CloudStream runtime hooks, dependency injection, and updater.

## Patterns
- Compile-time policy values expose the full capability set to shared code.
- Long-lived plugin services are Hilt singletons, execution is coroutine-based, and updater state is modeled with flows.
- CloudStream initialization is lazy and isolated from startup until extension work is requested.

## Flow
Lifecycle hooks establish the application and current activity context. DI creates the plugin runtime and manager, which reconcile repositories and execute scrapers. The updater ViewModel coordinates release checks, preferences, download progress, permission state, and installer handoff.

## Child responsibilities
- `core/` owns feature policy, plugin orchestration, CloudStream adaptation, and lazy runtime hooks.
- `di/` binds the full plugin runtime and manager graph in Hilt's singleton component.
- `updater/` owns GitHub release selection, APK download/install state, the update model, and TV prompt UI.

## Integration
Shared Lume UI and application code consume these implementations. They connect to `BuildConfig`, Android lifecycle/package APIs, Hilt, GitHub release services, local plugin data, auth/sync, TMDB services, QuickJS, OkHttp, and CloudStream registries.
