# app/src/full/java/com/nuvio/tv/core/runtime/

## Responsibility
`PluginRuntimeHooks` bridges Android lifecycle events to the CloudStream compatibility runtime.

## Patterns
- Application and activity references are volatile and activity state is held weakly by `AcraApplication`.
- Conscrypt and the shared NiceHttp client initialize once, lazily, under synchronization.
- The client uses the extension cookie jar, redirects, ignored SSL errors, and a 50 MiB HTTP cache.

## Flow
`onApplicationCreate` records the app and context. A plugin load or Cloudflare path calls `ensureCloudstreamInitialized`, which installs Conscrypt and configures `app.baseClient`. Activity create/destroy updates the weak current activity.

## Integration
The hooks are called by application/activity lifecycle code and by the DEX loader before extension work. They connect `NuvioApplication.extensionCookieJar`, CloudStream `app`, `AcraApplication`, OkHttp, Conscrypt, and Android cache storage.
