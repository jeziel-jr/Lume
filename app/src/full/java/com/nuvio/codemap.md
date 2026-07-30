# app/src/full/java/com/nuvio/

## Responsibility
This is the Lume-owned namespace for full-flavor application integrations that replace the Play Store no-op implementations without changing shared call sites.

## Patterns
- Compile-time source overlays and `AppFeaturePolicy` make full-only capabilities explicit.
- Hilt constructs long-lived plugin services, while lifecycle hooks defer CloudStream setup until extension work begins.
- Updater state crosses the shared UI boundary through flows and callbacks rather than direct installer calls from screens.

## Flow
Application code enters the `tv` subtree for policy, runtime, plugin DI, and updater state. Plugin operations may cross to the sibling `com.lagradost` compatibility namespace, while update operations continue through release selection, download, and Android installation.

## Child responsibilities
- `tv/` contains the full feature policy, plugin/runtime system, Hilt bindings, and updater lifecycle.

## Integration
Shared `com.nuvio.tv` code consumes these implementations. They integrate with Android lifecycle and package APIs, GitHub release DTOs, Hilt, TMDB metadata, auth/sync, CloudStream runtime APIs, and local plugin data.
