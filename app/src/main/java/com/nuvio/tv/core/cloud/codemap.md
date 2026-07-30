# app/src/main/java/com/nuvio/tv/core/cloud/

## Responsibility

Provides a provider-neutral cloud/debrid library model, loads configured cloud items, identifies playable video files, and resolves a selected file to a playback URL.

## Design

- `CloudLibraryProviderApi` isolates provider operations behind `listItems()` and `resolvePlayback()`.
- `CloudLibraryRepository` selects only configured providers with `CloudLibrary` capability and converts each provider result into a per-provider UI state. Credentials are read at call time and are not stored in the models.
- Torbox maps torrent, Usenet, and web-download envelopes, normalizes IDs and names, calculates aggregate sizes and progress, and requests type-specific download links.
- Premiumize maps a flat file response into root-file or top-level-folder items, sorts playable files first, and uses an existing link before requesting item details.
- File playability is determined from video MIME types or provider extension allowlists. Stable keys combine provider, item type, and ID.

## Flow

`refresh()` reads `DebridSettingsDataStore`, returns a disabled loaded state when cloud library is off, then lists each supported configured provider independently. UI consumers select an item and file; `resolvePlayback()` rechecks settings and credentials, rejects non-playable files, delegates to the matching adapter, and returns success, missing-credentials, not-playable, or failure data.

## Integration

- `LibraryViewModel` owns refresh and playback selection and converts successful results into `CloudLibraryPlaybackInfo` for `LibraryScreen`.
- `TorboxApi` and `PremiumizeApi` are injected provider clients supplied by `core.di.NetworkModule`.
- `DebridProviders`, capability checks, and `DebridSettingsDataStore` define configured credentials and provider availability.
- Playback callers receive resolved URLs and file metadata, while provider-specific DTOs remain at the adapter boundary.
