# app/src/playstore/java/com/nuvio/tv/updater/ui/

## Responsibility
`UpdatePromptDialog` is a signature-compatible no-op composable for the Play Store build.

## Patterns
- It accepts the same state and action callbacks as the full dialog.
- It emits no UI and does not invoke callbacks, so an update prompt cannot appear in this flavor.

## Flow
Shared code may compose the function with `UpdateUiState`; the function returns immediately regardless of state.

## Integration
The overlay keeps shared call sites compiling without importing full updater UI dependencies or exposing an in-app installation route.
