# app/src/main/java/com/nuvio/tv/ui/theme/

## Responsibility
Defines the visual system used by all Compose TV features: palettes, typography, spacing, sizes, shapes, elevations, strokes, media and layout dimensions, motion, and focus constants.

## Design

- `NuvioTheme` maps the selected `AppTheme`, font, AMOLED options, and settings UI style into TV Material 3 plus custom CompositionLocals.
- `NuvioTheme.colors`, `extendedColors`, `textStyles`, and token accessors provide stable semantic values to components instead of scattered literal colors and dimensions.
- `ThemeColors` supplies named palettes and `NuvioPrimitives` supplies raw values. Token data classes are immutable and grouped by usage such as screen, rail, card, dialog, side panel, player, and settings.
- Motion and focus tokens centralize TV transitions, focus scale, pressed scale, scroll targeting, and long-press timing.

## Flow

Settings state selects the app theme and font at the composition boundary. `NuvioTheme` derives the palette, Material color scheme, typography, and extended colors, then provides them to descendants. Screens read the semantic tokens during composition; this package has no feature data flow or persistence.

## Integration

Consumes `AppTheme`, `AppFont`, and `SettingsUiStyle` domain enums. Every UI feature depends on these tokens, while TV Material 3 receives the mapped color scheme and typography. Theme changes are persisted by settings ViewModels outside this package.
