# app/src/main/java/com/nuvio/tv/ui/

## Responsibility
This is the Compose TV presentation layer. It contains the application theme, reusable TV controls, navigation graph, screen features, and UI-only helpers. Screen code translates domain and repository state into remote-control-first interactions without owning the underlying catalog, playback, or persistence rules.

## Design

- Features are grouped by screen or shared concern. Most stateful screens use Hilt-provided `ViewModel` classes with lifecycle-aware `StateFlow` collection.
- `NuvioTheme` and token objects are the common visual contract. Components use `FocusRequester`, key handlers, lazy containers, and explicit focus restoration for Android TV navigation.
- Navigation is centralized in `navigation/Screen.kt` and `navigation/NuvioNavHost.kt`; feature composables receive navigation callbacks instead of constructing destinations themselves.
- Shared card, rail, dialog, loading, error, availability, stream, and poster-option behavior belongs in `components/`, not in individual screen copies.

## Flow

Routes provide encoded content and return-focus arguments to screen composables. A screen obtains its ViewModel, collects state from repositories or data stores, renders the current state, and sends user actions back through callbacks or ViewModel events. Repository updates emit new state, which recomposes the screen and can trigger focus, pagination, availability reclassification, or navigation.

## Integration

The UI depends on `domain` models and repositories, `core` services such as TMDB, Xtream, profile, sync, stream, and playback services, Android Navigation Compose, TV Material 3, Coil, and localized resources. TMDB remains the discovery and metadata source while Xtream availability is surfaced through shared non-Home badges and filtered from Home where the feature owns the result set.
