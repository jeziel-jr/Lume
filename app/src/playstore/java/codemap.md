# app/src/playstore/java/

## Responsibility
This is the Play Store flavor's Kotlin package root for policy, plugin, runtime, DI, and updater overlays that preserve shared call sites without bringing in full-only behavior.

## Patterns
- Package names and public method shapes match the full overlay so compile-time flavor substitution is transparent to shared consumers.
- Implementations are deliberately inert rather than conditionally loading full-only dependencies.
- Empty flows, no-op callbacks, and explicit unsupported failures make disabled behavior observable and predictable.

## Flow
Application and lifecycle code reaches `com.nuvio.tv`; disabled policy values prevent full feature entry, plugin calls resolve through the inert façade, and update calls terminate in state-only implementations.

## Child responsibilities
- `com/nuvio/` contains the Play Store Lume namespace and its disabled capability packages.

## Integration
The source set compiles with shared main code and uses shared domain models, lifecycle contracts, and Hilt interfaces without the full CloudStream, QuickJS, repository download, or package-installer graph.
