# app/src/main/java/com/nuvio/tv/ui/screens/xtream/

## Responsibility

Mandatory per-device Xtream setup for an unconfigured TV. The screen presents QR-first phone entry, a remote-control manual fallback, validation feedback, and an explicit on-TV confirmation step before credentials are stored.

## Design

- `XtreamSetupScreen` is a Compose projection of `XtreamSetupUiState`. It switches between QR and manual forms, masks the proposed username, and exposes retry, confirm, reject, and completion callbacks. The provider endpoint appears read-only with a "set by Lume" hint; a hidden gesture (five OK presses) reveals an editable `Server (manual)` field for the operator only.
- `XtreamSetupViewModel` is a Hilt ViewModel with a short-lived `XtreamSetupServer` owned by the ViewModel lifecycle. The server callback is the only path from the local web form into credential validation. The endpoint comes from `XtreamEndpointResolver` (bounded `awaitEndpoint()` preserves the last known value offline) instead of user input.
- Setup is intentionally two phase: authenticated credentials become pending after server validation, then confirmation persists them. A ten-minute expiry and `onCleared` stop the local server; the password is not displayed or included in the QR URL.

## Flow

1. Initialization calls `start()`, resolves the TV's LAN address, asks `XtreamEndpointResolver` for the current endpoint (bounded so a device without connectivity still renders), and starts `XtreamSetupServer` on an available port with a provider that returns the resolved endpoint.
2. The ViewModel builds a QR URL containing the TV address, port, and session fragment. A phone submits username and password to the local server, or the TV calls `submitManual()` directly.
3. `validate()` authenticates through `XtreamApiFactory` with a 20-second timeout. Authorized account data is converted to `XtreamAccountInfo`, the local request is marked awaiting confirmation, and credentials remain pending in UI state. Invalid credentials mark the request invalid and expose a localized error.
4. `confirm()` marks the request applied, saves credentials and account info, stores the operator override when the hidden field was used, clears health, API, playback, and availability runtime state, and invalidates the catalog when endpoint or username changes. The `applied` flag triggers `onConfigured` and stops the server. `reject()` discards pending state without persistence.

## Integration

The flow integrates `DeviceIpAddress`, `QrCodeGenerator`, `XtreamSetupServer`, `XtreamCredentialsStore`, `XtreamEndpointResolver`, `XtreamApiFactory`, `XtreamCatalogRepository`, `XtreamAvailabilityStore`, `XtreamPlaybackResolver`, and `XtreamServerHealthMonitor`. Catalog and availability invalidation preserve the separation between local credential lifecycle and the TMDB-owned catalog, while API and resolver cache clearing makes a new provider identity effective immediately.
