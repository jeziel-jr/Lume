# app/src/main/java/com/nuvio/tv/ui/screens/

## Responsibility

This package contains the Compose presentation entry points for Lume's TV flows. It groups feature screens by user journey and keeps navigation argument handling at the boundary between the UI and the domain and data layers.

## Design

- Screens are stateless Compose renderers around Hilt `ViewModel` instances and immutable `UiState` models.
- `StateFlow` is the main state boundary. ViewModels collect repositories, DataStore flows, and service results in `viewModelScope`, then screens render loading, success, empty, and error states.
- `NuvioNavHost` and `Screen` define routes and translate route arguments into screen inputs. Feature packages own event callbacks, focus restoration, and TV D-pad behavior.
- Shared components provide cards, catalog rows, availability badges, dialogs, posters, loading states, and navigation chrome.

## Flow

App startup and onboarding select an experience and layout, then enter Home. Home, Search, Discover, Library, and browse screens resolve content into `MetaPreview` or `CatalogRow` values. Selecting an item navigates to details, stream selection, or the player. Detail and catalog surfaces submit visible items to the Xtream availability tracker, while Home filters unavailable items before rendering.

## Integration

The screens depend on `domain.model`, repository interfaces, TMDB services, Xtream catalog and playback services, local DataStore state, and shared player and deep-link infrastructure. TMDB remains the discovery and metadata source. Xtream is used for authorized playback availability. Legacy account, addon, plugin, debrid, torrent, and cloud integrations remain in source for compatibility, but AD-004 excludes them from the intended Lume UX.
