# app/src/main/java/com/nuvio/tv/ui/screens/xtream/

## Responsibility

Mandatory per-device Xtream setup for an unconfigured TV. The screen presents QR-first phone entry, a remote-control manual fallback, validation feedback, and an explicit on-TV confirmation step before credentials are stored.

## Design

- `XtreamSetupScreen` is a Compose projection of `XtreamSetupUiState`. It switches between QR and manual forms, masks the proposed username, and exposes retry, confirm, reject, and completion callbacks.
- `XtreamSetupViewModel` is a Hilt ViewModel with a short-lived `XtreamSetupServer` owned by the ViewModel lifecycle. The server callback is the only path from the local web form into credential validation.
- Setup is intentionally two phase: authenticated credentials become pending after server validation, then confirmation persists them. A ten-minute expiry and `onCleared` stop the local server; the password is not displayed or included in the QR URL.

## Flow

1. Initialization calls `start()`, resolves the TV's LAN address, obtains the current or default server URL from `XtreamCredentialsStore`, and starts `XtreamSetupServer` on an available port.
2. The ViewModel builds a QR URL containing the TV address, port, and server fragment. A phone submits normalized credentials to the local server, or the TV calls `submitManual()` directly.
3. `validate()` authenticates through `XtreamApiFactory` with a 20-second timeout. Authorized account data is converted to `XtreamAccountInfo`, the local request is marked awaiting confirmation, and credentials remain pending in UI state. Invalid credentials mark the request invalid and expose a localized error.
4. `confirm()` marks the request applied, saves credentials and account info, clears health, API, playback, and availability runtime state, and invalidates the catalog when endpoint or username changes. The `applied` flag triggers `onConfigured` and stops the server. `reject()` discards pending state without persistence.

## Integration

The flow integrates `DeviceIpAddress`, `QrCodeGenerator`, `XtreamSetupServer`, `XtreamCredentialsStore`, `XtreamApiFactory`, `XtreamCatalogRepository`, `XtreamAvailabilityStore`, `XtreamPlaybackResolver`, and `XtreamServerHealthMonitor`. Catalog and availability invalidation preserve the separation between local credential lifecycle and the TMDB-owned catalog, while API and resolver cache clearing makes a new provider identity effective immediately.
