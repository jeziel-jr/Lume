# app/src/full/java/com/nuvio/tv/updater/

## Responsibility
This package implements the full in-app update lifecycle: persisted check preferences, GitHub release selection, ABI-aware APK download, version comparison, Android install handoff, and TV UI state orchestration.

## Patterns
- Repository boundaries return `Result<AppUpdate>` and reject unsuccessful, draft, prerelease, or asset-less releases.
- ABI selection prefers device order, then universal assets, then assets without known ABI markers.
- `UpdateViewModel` owns one `StateFlow`, persists ignored tags and check time, reports download progress, and gates installation on unknown-source permission.
- APKs are cached privately and exposed through `FileProvider` only for the installer handoff.

## Flow
Read preferences, fetch the latest valid GitHub release, select and compare its APK, expose availability in shared UI, stream the download into cache, request unknown-source permission when required, then launch Android's package installer.

## Child responsibilities
- `model/` carries the selected release and APK metadata between repository, state, and UI.
- `ui/` renders the focus-aware TV prompt and routes user actions, download progress, and permission return to the ViewModel.

## Integration
The package consumes `BuildConfig`, `GitHubReleaseApi`, release DTOs, Android `FileProvider` and settings APIs, DataStore preferences, and shared Compose/TV UI through `UpdateUiState`.
