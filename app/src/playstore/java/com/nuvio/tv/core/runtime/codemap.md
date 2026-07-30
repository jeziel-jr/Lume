# app/src/playstore/java/com/nuvio/tv/core/runtime/

## Responsibility
The Play Store runtime hook is a no-op lifecycle boundary that replaces full CloudStream initialization.

## Patterns
- `onApplicationCreate`, `onActivityCreate`, and `onActivityDestroy` retain the full API but execute `Unit`.
- There are no context references, synchronization, Conscrypt setup, cookie jar changes, or HTTP cache side effects.

## Flow
Shared lifecycle code calls the three hooks as usual; each returns immediately, so no extension runtime is created.

## Integration
The class satisfies calls from shared application lifecycle code and is selected instead of the full hook implementation. It has no dependency on CloudStream, NiceHttp, or `NuvioApplication`.
