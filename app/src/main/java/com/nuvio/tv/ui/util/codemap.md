# app/src/main/java/com/nuvio/tv/ui/util/

## Responsibility
Contains UI-only helpers for Android TV interaction and display: DPAD throttling and fast scrolling, long-press detection, focus requester grouping, RTL key handling, stable holders, blur transformation, dates, language labels, genre labels, and content type labels.

## Design

- Modifier extensions encapsulate key event policy so screens do not each implement repeat throttling or focus scrolling.
- `StableHolders` and related immutable wrappers reduce unnecessary Compose recomposition for frequently updated screen state.
- Formatting helpers keep localized labels and date handling out of feature layouts. Blur and recomposition helpers are isolated from data loading.
- The helpers are deliberately UI-facing and do not become alternate repositories or domain services.

## Flow

Screens attach the modifiers or call formatters while composing. Key events are throttled or translated into focus and scroll behavior, then screen callbacks or ViewModel events perform the actual mutation. Formatters synchronously map model values to display strings.

## Integration

Used throughout components and screen packages with Compose runtime, focus, input, layout, Coil transformation, and domain label or language types. Shared timing and focus values align with `NuvioTheme` tokens.
