# app/src/main/java/com/nuvio/tv/core/deeplink/

## Responsibility

Parses supported app and Stremio-style links into domain commands and handles addon manifest installation.

## Design

- `DeepLinkParser` is a pure object that parses `URI`, decodes path and query values, normalizes movie or series aliases, and returns `AppDeepLink.Meta` or `AppDeepLink.AddonInstall`.
- `nuvio://` and `lume://` support meta, detail/open/watch, media-type, and provider-ID forms. `tmdb:` IDs are preserved; IMDb-style IDs are normalized. `stremio://` and addon-like hosts are converted to HTTPS manifest URLs, while `auth` and unrecognized non-addon hosts are rejected.
- `DeepLinkHandler` is an injected suspend boundary that uses `AddonRepository.fetchAddon()` before persisting with `addAddon()`, returning localized success or error results.

## Flow

`MainActivity` supplies incoming intent URLs to `DeepLinkParser`. A meta result is routed to the relevant details or playback path. An addon result is accepted only in the appropriate setup state or passed to `DeepLinkHandler`; fetch failures become `DeepLinkInstallResult.Error`, and successful manifests are saved before the localized confirmation is returned.

## Integration

- `AppDeepLink` in the domain layer is the parser output contract.
- `MainActivity` is the entry-point consumer for initial and new-intent deep links.
- `AddonRepository` and `NetworkResult` provide manifest validation and error mapping.
- Android string resources supply user-facing addon installation messages; no network or persistence logic lives in the parser.
