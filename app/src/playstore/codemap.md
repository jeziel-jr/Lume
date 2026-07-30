# app/src/playstore/

## Responsibility
The `playstore` source set removes sideload-only capabilities while preserving the shared app contracts for plugins, runtime hooks, updates, and trailer behavior through inert overlays.

## Patterns
- The Play Store flavor replaces full implementations at compile time using the same `com.nuvio.tv` package names and public signatures.
- Policy values disable plugins, in-app updates, in-app trailers, and the IMDb rating logo; external trailers remain enabled.
- Hilt still provides singleton façades, but no plugin network/storage/DEX work or package installation is performed.

## Flow
Shared lifecycle and UI code can observe empty plugin/update state and call the same methods as in the full build. Calls return locally, unsupported plugin operations report failures where required, and no CloudStream runtime is initialized.

## Child responsibilities
- `java/` contains the package-compatible `com.nuvio.tv` policy, core, DI, and updater substitutions.
- Core children provide disabled plugin façades and no-op runtime hooks.
- Updater children preserve state and UI signatures without fetching releases, downloading APKs, or showing a prompt.

## Integration
The overlay is selected by the Play Store Gradle variant and compiles alongside `app/src/main`. It uses shared domain models, Hilt interfaces, lifecycle contracts, and external trailer paths without linking full CloudStream, QuickJS, GitHub, or Android installer dependencies.
