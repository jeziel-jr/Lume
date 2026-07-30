# app/src/main/java/com/nuvio/tv/ui/components/

## Responsibility
Provides reusable Compose TV building blocks shared by multiple features: poster and content cards, catalog and collection rows, hero and trailer surfaces, navigation chrome, dialogs, loading and error states, skeletons, progress labels, badges, stream chips, and collection media.

## Design

- Components are mostly stateless. They receive domain display models and callbacks, while feature ViewModels remain responsible for loading and mutations.
- Lazy rows and grids expose stable keys, pagination callbacks, focus callbacks, and optional availability state so screens can preserve focus while data changes.
- `NuvioTheme` tokens, TV Material 3 cards, explicit focus modifiers, and DPAD-aware helpers provide consistent remote navigation.
- `CatalogAvailabilityBadge`, `StreamBadgeChips`, `SourceStatusFilterChip`, and shared loading/error components encode common product states rather than duplicating them per screen.

## Flow

Feature screens pass current state into a component. The component renders the item, reports focus or click, and invokes callbacks for navigation, pagination, retry, or action menus. The feature updates its repository-backed state, then recomposition updates the shared component. Long-press poster actions are delegated to `posteroptions/`.

## Integration

Uses domain models such as `MetaPreview`, `CatalogRow`, `Video`, `Stream`, `WatchProgress`, and collection entries, plus theme tokens, Coil image loading, Android TV Material 3, and localized resources. Components are consumed by Home, Search, Detail, Library, Collection, Stream, Player, and Settings features.
