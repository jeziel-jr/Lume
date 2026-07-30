# app/src/main/java/com/nuvio/tv/core/logging/

## Responsibility

Provides small Kotlin extensions for consistent diagnostic log values and bounded exception summaries.

## Design

- `rawForLog()` and `urlForLog()` replace only null values with `(null)`; they do not otherwise redact or transform non-null text.
- `bodySnippetForLog()` provides a null and blank-safe body representation and truncates only when the caller supplies a smaller maximum length.
- `diagnosticSummary()` walks an exception cause chain up to six entries and joins class names and bounded messages with a directional separator.

## Flow

Callers format nullable IDs, URLs, response bodies, and exceptions at log construction time. The extensions return strings only and have no storage, dispatch, or logging side effects.

## Integration

`AuthManager` uses these helpers for QR, Supabase request, response, fallback, and refresh logs. Credential filtering is handled by `core.auth.diagnostics`, not by these general-purpose extensions, so callers must sanitize sensitive values before passing them here.
