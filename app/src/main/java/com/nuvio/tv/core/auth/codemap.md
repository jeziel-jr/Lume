# app/src/main/java/com/nuvio/tv/core/auth/

## Responsibility

Owns Supabase account session state, email and QR TV-login exchanges, refresh recovery, effective-user lookup, logout cleanup, and auth-specific HTTP diagnostics.

## Design

- `AuthManager` is a Hilt singleton exposing `StateFlow<AuthState>` and observing Supabase `SessionStatus` on an IO `CoroutineScope`.
- Login and QR operations use explicit JSON OkHttp requests, decode `TvLogin*Result` values, and import tokens through `SupabaseAuthSession` into a `UserSession`.
- Session refreshes are serialized by a `Mutex`. A changed refresh token makes a concurrent caller reuse the already refreshed session. Refresh failures are classified as invalid-session or transient.
- `getEffectiveUserId()` resolves the sync owner through PostgREST and caches it per authenticated source user, with optional fallback to the local user ID.
- `AccountLocalDataResetService` clears profile stores, Android TV channels, and selected account files after explicit or unexpected logout. Diagnostic request bodies and headers are filtered before reporting.

## Flow

Supabase session events drive `Loading`, `FullAccount`, or `SignedOut`. Authenticated events validate the user email and mark the session notice store; unauthenticated events try a serialized refresh when a refresh token exists, otherwise sign out and clear account data. Email login, signup, and QR start, poll, and exchange all call the shared Supabase JSON executor, which records diagnostics, logs bounded summaries, and retries eligible failures against the configured fallback origin. Successful token responses are imported into Supabase Auth.

## Integration

- `SupabaseModule` provides the `Auth` and `Postgrest` clients and applies the refresh-response policy.
- `MainActivity`, `AccountViewModel`, settings, repositories, and sync services consume `authState`, user IDs, login methods, and refresh recovery.
- `AuthDiagnosticReportRepository` receives terminal reports; `core/auth/diagnostics` supplies the session and OkHttp event listener.
- `AuthSessionNoticeDataStore`, profile stores, and `AndroidTvChannelManager` receive lifecycle and logout cleanup effects.
