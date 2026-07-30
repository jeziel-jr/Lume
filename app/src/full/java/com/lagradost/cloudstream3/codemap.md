# app/src/full/java/com/lagradost/cloudstream3/

## Responsibility
This package provides the minimum CloudStream application surface required by downloaded extensions: context/activity access, notification compatibility, plugin registration, extension settings, and challenge-aware networking.

## Patterns
- `AcraApplication` stores application context and a weak current activity, forwarding active context to the linked library.
- `CommonActivity` remains a no-op compatibility stub rather than introducing CloudStream UI.
- Child services keep extension persistence and Cloudflare recovery bounded and separate from Lume's own storage and UI.

## Flow
`PluginRuntimeHooks` sets application and activity state. An extension reads the bridge, registers APIs through `Plugin`, and uses `DataStore` or `CloudflareKiller` when it needs settings or HTTP challenge recovery.

## Child responsibilities
- `utils/` implements extension-compatible JSON settings in the dedicated SharedPreferences file.
- `network/` retries Cloudflare failures through WebView cookies and the shared HTTP client.
- `plugins/` registers extension APIs in local and CloudStream global registries while retaining the legacy manager shape.

## Integration
External CloudStream-compatible extensions and the full plugin loader call this package. It delegates API and extractor registration to CloudStream globals and keeps Lume UI code outside the compatibility boundary.
