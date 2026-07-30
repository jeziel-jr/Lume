# app/src/main/java/com/nuvio/tv/core/plugin/

## Responsibility

Small common utilities for plugin scraper safety and diagnostic reporting. The
package does not load extensions or perform scraping itself. It provides a
guard for a known VideoEasy scraper family and an ordered per-run diagnostic
log that plugin infrastructure and the TV UI can share.

## Design

- `PluginSafety` is an `internal` stateless policy object. It checks scraper ID,
  display name, and filename case-insensitively, returning true when any
  supplied identity contains `videasy`.
- `TestDiagnostics` is a mutable run-local accumulator. `addStep` preserves
  insertion order for UI display and immediately mirrors each step to
  `android.util.Log` under the `TestDiagnostics` tag. There is no persistence,
  parsing, or retry policy here.

## Flow

1. The plugin manager creates `TestDiagnostics` for a scraper test.
2. Extension loading and scraper execution append status lines as work
   progresses. The same object travels through extension loader and runner
   calls, then returns with scraper results.
3. Plugin enablement checks the scraper identity through `PluginSafety` before
   allowing the guarded path. The resulting diagnostics are stored in plugin
   UI state and rendered by the plugin screen.

## Integration

- Full and Play Store plugin managers use `TestDiagnostics` for scraper-test
  results; full Cloudstream extension loading and execution receive it to add
  steps.
- `PluginViewModel` uses `PluginSafety`, while `PluginUiState` and
  `PluginScreen` expose the diagnostic object to Compose UI.
- The utilities depend only on the Android logger and plugin/domain callers.
  They have no network, storage, coroutine, or extension-loading dependency.
