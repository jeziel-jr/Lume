# app/src/full/java/com/nuvio/tv/updater/ui/

## Responsibility
`UpdatePromptDialog` presents update state on TV and routes user actions to the updater ViewModel callbacks.

## Patterns
- The composable is state-in, callbacks-out and renders nothing when `showDialog` is false.
- Focus requesters establish D-pad entry; release notes use a focusable, vertically scrolling Markdown surface.
- Download progress is animated from `downloadProgress`; lifecycle resume retries installation after the unknown-sources settings screen.
- A short install-button delay prevents the same OK event that finished downloading from immediately triggering installation.

## Flow
`UpdateUiState` selects checking, error, available, downloading, downloaded, or permission content. Button callbacks invoke dismiss, ignore, download, install, or settings actions; resume observes permission return and calls install again.

## Integration
The dialog uses TV Material, Compose, Markdown, localized resources, and `NuvioColors`. Its callbacks are supplied by shared app UI and ultimately call `UpdateViewModel`.
