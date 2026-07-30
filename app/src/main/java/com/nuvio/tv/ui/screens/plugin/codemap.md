# app/src/main/java/com/nuvio/tv/ui/screens/plugin/

## Responsibility

TV settings for the optional plugin system. The screen manages plugin repositories,
scraper providers, global plugin enablement, repository-based stream grouping, and
provider test results. It also offers QR-based repository configuration from a phone,
with every remote change confirmed on the TV.

## Design

- `PluginScreen` is a Compose TV route that collects the Hilt-provided
  `PluginViewModel`. `PluginUiState` is the single render model and `PluginUiEvent`
  is the input boundary for repository, scraper, setting, test, QR, and confirmation
  actions.
- `PluginViewModel` combines the `PluginManager` flows for settings, repositories,
  and scrapers. Mutations and tests run in `viewModelScope`; transient messages and
  test output remain in state until cleared by the screen or a new test.
- Profile access is enforced in the view model and UI. A secondary profile that uses
  the primary profile's plugins is read-only and sees only enabled scrapers.
- Risky VideoEasy scrapers require a second confirmation before being enabled.
  Repository and scraper cards, test diagnostics, and confirmation dialogs are
  composable children of the screen rather than independent state holders.

## Flow

1. On initialization, the view model loads the app wordmark for the local web page
   and starts collecting plugin manager state. The UI renders repository cards and
   scraper cards from the latest state.
2. User events are dispatched through `onEvent`. Repository additions, refreshes,
   removals, setting changes, scraper toggles, and tests call the corresponding
   `PluginManager` operation and update loading or result state.
3. A scraper test returns local stream results plus `TestDiagnostics`; the screen
   shows a compact result list and expandable diagnostic steps for the tested scraper.
4. QR mode starts a local `RepositoryConfigServer` on an available port, creates a
   QR code from the device IP and port, and exposes the current repository list. A
   phone submission becomes a normalized added/removed URL diff in
   `pendingRepoChange`.
5. Confirming a pending QR change adds proposed repositories, removes matching old
   repositories, acknowledges the server request, and closes the server after a
   short completion delay. Rejecting only marks the request rejected. QR servers are
   stopped when the screen leaves composition or the view model is cleared.

## Integration

- The full build's `PluginManager` persists through `PluginDataStore`, loads Nuvio
  and external repositories through its plugin runtime and loaders, and coordinates
  plugin synchronization. The play store build supplies an unavailable-feature stub.
- `PluginSafety` identifies the scraper requiring confirmation, while
  `ProfileManager` supplies the active profile and shared-plugin policy.
- `RepositoryConfigServer`, `DeviceIpAddress`, and `QrCodeGenerator` implement the
  phone handoff; `R.drawable.app_logo_wordmark` brands the local page.
- `NuvioNavHost` exposes this route only when the application plugin policy allows
  it. Stream and player features consume the enabled scraper state from the same
  `PluginManager`.
