# app/src/main/java/com/nuvio/tv/core/auth/diagnostics/

## Responsibility

Captures structured, credential-filtered evidence for authentication and QR-login attempts, including request, response, network timing, state, exception, and terminal outcome data.

## Design

- `AuthDiagnosticsSession` is a synchronized, single-finish event accumulator identified by flow type, attempt UUID, and optional QR trace ID.
- Request and response payloads are filtered by credential-key matching. JSON values are recursively replaced, with a regex fallback for malformed JSON. Headers and state details use the same key policy.
- `AuthDiagnosticEventListener` is an OkHttp `EventListener` that records DNS, connect, TLS, request-header, response-header, completion, and failure phases with elapsed durations.
- Terminal completion packages app, device, environment, flow, timeline, exceptions, and raw diagnostic lines into `AuthDiagnosticReportRequestDto`, then submits through the repository. Duplicate completion is rejected.

## Flow

`AuthManager` creates a session for startup restoration, password auth, refresh, or QR operations. The shared request executor records sanitized request and response events and attaches the listener to a per-request client. Failures add classified exception and terminal events. `finishTerminal()` submits the immutable report and logs whether upload succeeded or was queued.

## Integration

- `AuthManager` owns session creation and passes it through all auth HTTP calls.
- `AccountViewModel` uses a QR flow session and trace ID for TV-login diagnostics.
- `AuthDiagnosticReportRepository` persists or uploads the report through the playback-reports network binding.
- `BuildConfig`, Android device metadata, DTOs, and `core.logging` provide environment data and safe log formatting without exposing credentials.
