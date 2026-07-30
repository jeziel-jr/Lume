# app/src/full/java/com/lagradost/cloudstream3/plugins/

## Responsibility
This package exposes the plugin base and a narrow legacy manager stub for CloudStream-compatible extensions.

## Patterns
- `Plugin` accepts no-arg, `Activity?`, and `Context` load conventions.
- Registration is local-list first, then mirrored into global `APIHolder` and `extractorApis` registries.
- `PluginManager` intentionally returns no online plugins and makes unloading a no-op.

## Flow
The external loader instantiates a plugin, calls `load(Context)`, collects registered main and extractor APIs, and exposes them to the full runner. Each registration records the source filename before updating the global registry.

## Integration
The classes match signatures expected by downloaded plugins and are used by `ExternalExtensionLoader`. They depend on CloudStream `MainAPI`, `ExtractorApi`, `APIHolder`, and extractor globals.
