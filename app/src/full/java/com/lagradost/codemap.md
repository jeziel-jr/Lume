# app/src/full/java/com/lagradost/

## Responsibility
This namespace is the full-flavor compatibility boundary for code compiled against CloudStream package names. It keeps external extensions loadable without importing the original CloudStream application UI or persistence model.

## Patterns
- Small API-compatible shims expose only the context, plugin, storage, and network behavior required by downloaded extensions.
- Child packages isolate CloudStream globals and registration from extension settings persistence and challenge recovery.

## Flow
`PluginRuntimeHooks` establishes application and activity state, then the full plugin bridge loads an extension through the app classloader or `DexClassLoader`. Extensions use the `cloudstream3` child APIs, which delegate global registration and HTTP work to the linked CloudStream library.

## Child responsibilities
- `cloudstream3/` provides application/context bridges, a notification stub, and the child utility, network, and plugin services.

## Integration
`com.nuvio.tv.core.plugin.cloudstream` consumes this namespace during extension loading. The shims connect to CloudStream `APIHolder`, extractor registries, `app.baseClient`, Android `CookieManager`, and the full runtime lifecycle.
