# app/src/playstore/java/com/nuvio/tv/

## Responsibility
This package contains the Play Store feature policy plus disabled plugin, runtime, DI, and updater implementations selected instead of the full counterparts.

## Patterns
- Compile-time `AppFeaturePolicy` exposes the Play Store capability set: plugins, in-app updates, and in-app trailers are disabled, while external trailers remain enabled.
- Plugin manager and runtime hooks preserve lifecycle and API shape without performing extension work.
- The updater state model and dialog signature preserve shared UI compatibility while all actions remain inert.

## Flow
Shared app code checks policy, injects the no-argument plugin manager, observes stable empty update state, and can call the same updater entry points. Calls never reach remote repositories, DEX/QuickJS execution, CloudStream HTTP setup, or the Android installer.

## Child responsibilities
- `core/` provides disabled policy, plugin façade, and no-op lifecycle hooks.
- `di/` binds one no-argument inert manager in Hilt's singleton component.
- `updater/` preserves update state and prompt APIs without release, download, or install behavior.

## Integration
Shared `com.nuvio.tv` code consumes this overlay by package and interface. It uses shared domain models, lifecycle contracts, Hilt, and UI state types while intentionally excluding full-only CloudStream, QuickJS, GitHub, and package-installer dependencies.
