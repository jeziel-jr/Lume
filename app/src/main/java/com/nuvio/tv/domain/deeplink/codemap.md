# app/src/main/java/com/nuvio/tv/domain/deeplink/

## Responsibility

Defines the typed outcomes of URLs that can enter the app. It deliberately contains no URI parsing or Android navigation code.

## Design

- `AppDeepLink` is a sealed interface with two data variants: `Meta(type, id)` for opening content and `AddonInstall(manifestUrl)` for installing an addon.
- The payload is intentionally small. Media type and content ID are already normalized by the parser, while the addon payload is an HTTPS manifest or base URL.
- Invalid or unsupported URLs are represented by a parser-level `null`, not by an extra domain error subtype.

## Flow

`core/deeplink/DeepLinkParser` accepts `nuvio:`, `lume:`, and compatible `stremio:` URLs, normalizes media aliases and IDs, and creates one of the two variants. `MainActivity` routes `Meta` to details and `AddonInstall` to the install flow. `DeepLinkHandler` validates an install URL through `AddonRepository.fetchAddon`, persists it with `addAddon`, and returns a localized success or error result.

## Integration

The parser and handler are the only producers and consumers outside this package. Metadata routing reaches the detail screen and repository layer. Addon installation reaches `AddonRepository`, manifest APIs, addon preferences, and the optional remote addon sync path.
