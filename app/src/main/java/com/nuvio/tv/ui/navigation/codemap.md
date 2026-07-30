# app/src/main/java/com/nuvio/tv/ui/navigation/

## Responsibility
Defines all app destinations and the single Compose Navigation host that connects onboarding, Home, catalog surfaces, details, stream selection, playback, settings, account, profiles, collections, and integrations.

## Design

- `Screen` is the route contract and owns route builders that URL-encode IDs, titles, artwork, headers, episode data, stream metadata, and return flags.
- `NuvioNavHost` is the integration boundary. It wires screen callbacks to navigation, parses arguments, and preserves saved-state values for detail episode focus and hero restoration.
- Stream and player routes carry enough playback context to support direct playback, manual source selection, autoplay, torrent metadata, external-player return, and navigation back to Detail or Home.
- Navigation transitions are centralized and suppress the stream-to-player fade for autoplay handoff.

## Flow

Feature callbacks call a `Screen.createRoute` builder, then `NuvioNavHost` creates the destination and supplies decoded arguments to the screen. Detail starts Stream, Stream resolves a source and starts Player, and Player reports completion, errors, current episode, or exit reasons back to the host. Saved state restores season, episode, and hero focus when returning.

## Integration

Connects Compose Navigation to every `ui.screens` package, `NuvioTheme` motion tokens, Hilt ViewModels, and domain playback/catalog data. Feature visibility also follows `BuildConfig` and `AppFeaturePolicy`; this package does not resolve catalog or playback data itself.
