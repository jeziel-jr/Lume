# app/src/playstore/java/com/nuvio/

## Responsibility
This is the Play Store flavor's Lume namespace boundary. Its child overlay preserves shared contracts while removing unavailable sideload capabilities.

## Patterns
- Child implementations match the full flavor's package and API shapes, but keep behavior disabled at compile time.
- Hilt and `StateFlow` remain available to shared consumers even when plugin and updater services are inert.

## Flow
Shared lifecycle, settings, and UI code enters the `tv` child. Feature checks select the disabled policy before plugin, runtime, or updater calls reach no-op implementations.

## Child responsibilities
- `tv/` contains disabled feature policy, plugin/runtime façades, the Play Store Hilt binding, and state-compatible updater code.

## Integration
The namespace integrates with shared domain models, lifecycle, Hilt, and UI contracts, but has no dependency path to full-only extension loading, GitHub downloads, CloudStream initialization, or Android package installation.
