# app/src/full/java/com/nuvio/tv/updater/model/

## Responsibility
`AppUpdate` is the immutable transport model for a selected GitHub release APK.

## Patterns
- A Kotlin data class keeps release tag, title, notes, release URL, chosen asset identity, download URL, and optional size together.
- Nullable fields reflect GitHub metadata that may be absent without weakening required install fields.

## Flow
`UpdateRepository` maps a validated GitHub release DTO and selected asset into `AppUpdate`; `UpdateViewModel` stores it in `UpdateUiState`, and the dialog consumes its display and download fields.

## Integration
The model is internal to the full updater and has no persistence or Android behavior. It is the contract between remote release selection, update state, and Compose UI.
