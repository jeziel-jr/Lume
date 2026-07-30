# app/src/main/java/com/nuvio/tv/ui/screens/account/

## Responsibility

Account authentication and cloud-sync settings for the TV. The package contains the account overview, email and QR login surfaces, linked-device management, optional sync-code screens, account settings content, common TV text input, and sign-out confirmation.

## Design

- `AccountViewModel` is the stateful coordinator. `AccountUiState` combines auth state, linked devices, local and remote sync statistics, effective owner identity, errors, and QR session state so the Compose surfaces can remain event driven.
- Screens collect the state and own only transient navigation and dialog state. `AuthQrSignInScreen` handles onboarding versus settings mode, lifecycle cleanup, expiration display, and the self-hosted email fallback. `SHOW_SYNC_CODE_FEATURES` currently keeps the legacy sync-code actions hidden in the main account screen.
- QR login has explicit start, poll, exchange, cancellation, and terminal states. Trace IDs, nonces, bounded polling, endpoint diagnostics, and cleanup protect against overlapping or abandoned sessions. Sign-out requires confirmation and leaves the ViewModel to update auth state.

## Flow

1. `AccountViewModel` observes `AuthManager.authState` and local profiles. A full account triggers local connected-stat loading, remote sync-overview loading, and linked-device loading from `AccountScreen`.
2. Email sign-in or sign-up calls `AuthManager`, then pushes or pulls profile settings, addons, plugins, library, watch progress, and watched-item data. Pulls capture the active profile ID before long operations and reconcile local repositories while preserving source-specific Trakt behavior.
3. QR login creates a secure device nonce and Supabase TV-login session, publishes the QR bitmap and expiration, polls until approval, exchanges the approved code, then pulls remote data and refreshes local statistics. Errors are mapped to localized messages without exposing raw service details in the UI.
4. Sync-code generation pushes local data before requesting a code. Claiming a code refreshes effective ownership and remote data. Linked-device unlinking refreshes the device list. `AccountSettingsContent` renders signed-out login or signed-in status, per-profile sync totals, and sign-out.

## Integration

The package integrates `AuthManager`, Supabase PostgREST and TV-login RPC/edge endpoints, `SyncRepository`, linked-device APIs, and the addon, plugin, library, watch-progress, watched-item, and profile-settings sync services. It reads `ProfileManager`, Trakt auth state, local DataStores and repositories for stats and source decisions, uses `QrCodeGenerator` and `BuildConfig` for QR configuration, and routes navigation through callbacks supplied by the Activity. It is separate from the local Xtream credential setup.
