# app/src/playstore/java/com/nuvio/tv/di/

## Responsibility
`PluginModule` binds the disabled Play Store plugin façade.

## Patterns
- A singleton `@Provides` method returns a no-argument `PluginManager`.
- The module has no full-only collaborators, so the Play Store graph cannot accidentally construct extension services.

## Flow
Hilt installs the module in `SingletonComponent`, creates one inert manager, and injects it wherever shared code requests the plugin contract.

## Integration
This is the Play Store counterpart to the full module. It preserves the injection point while excluding plugin storage, auth/sync, repository parsing, DEX loading, and QuickJS runtime dependencies.
