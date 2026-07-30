# app/src/playstore/java/com/

## Responsibility
This package root contains the Play Store-specific `com.nuvio.tv` capability and service substitutions.

## Patterns
- Package-compatible overlays keep shared call sites unchanged across flavors.
- Immutable policy values and inert service façades represent disabled capabilities without loading full-only code.

## Flow
The app reads the Play Store policy, resolves plugin calls through the no-op manager, and routes update calls to a state-only ViewModel and no-op dialog. Lifecycle hooks return without creating CloudStream state.

## Child responsibilities
- `nuvio/` contains the disabled feature policy, plugin/runtime contracts, Hilt module, and updater overlay.

## Integration
Shared Lume code consumes these classes by package and interface. No Play Store overlay loads CloudStream, QuickJS, remote plugin repositories, GitHub releases, or Android APK installation.
