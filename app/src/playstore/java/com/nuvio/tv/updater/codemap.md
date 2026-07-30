# app/src/playstore/java/com/nuvio/tv/updater/

## Responsibility
The Play Store updater overlay preserves update state and call signatures while disabling all in-app update behavior.

## Patterns
- `UpdateUiState` mirrors the full state shape, but uses an opaque payload because the release model is not compiled here.
- `UpdateViewModel` exposes one stable `StateFlow` and implements every action as a no-op.
- Child UI preserves the shared composable entry point without importing full updater dependencies.

## Flow
Shared UI may call check, dismiss, ignore, download, install, or settings actions. None changes state, accesses GitHub, writes an APK cache, requests permission, or launches an installer.

## Child responsibilities
- `ui/` provides the signature-compatible composable that returns without rendering or invoking callbacks.

## Integration
The ViewModel is available to shared Hilt/UI call sites and pairs with the no-op dialog. The package intentionally omits the full repository, preferences, downloader, installer, release model, and Android package graph.
