# app/src/full/java/com/nuvio/tv/di/

## Responsibility
`PluginModule` supplies the full plugin graph to Hilt's singleton component.

## Patterns
- Explicit `@Provides` methods construct `PluginRuntime` and `PluginManager` as `@Singleton` services.
- The manager receives storage, sync, authentication, repository parsing, DEX loading, and execution collaborators.

## Flow
Hilt creates one runtime, then injects it with `PluginDataStore`, `PluginSyncService`, `AuthManager`, `ExternalRepoParser`, `ExternalExtensionLoader`, and `ExternalExtensionRunner` into one `PluginManager`.

## Integration
The module is installed in `SingletonComponent` and is selected only by the full source set. Shared consumers can inject the same `PluginManager` contract without knowing the construction details.
