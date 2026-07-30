# app/src/playstore/java/com/nuvio/tv/core/

## Responsibility
The Play Store core overlay disables full-only capabilities while maintaining the feature policy, plugin, and lifecycle contracts required by shared code.

## Patterns
- `AppFeaturePolicy` is a static table: plugins and in-app updates/trailers are false, external trailers are true, and trailer mode is `EXTERNAL`.
- `PluginManager` exposes cold empty flows, no-op mutations, and explicit unsupported failures for operations that require the full graph.
- `PluginRuntimeHooks` accepts lifecycle calls without retaining context or initializing CloudStream or HTTP state.

## Flow
Policy checks prevent full feature entry. If shared code still calls plugin APIs, repository additions and tests fail locally as unsupported, execution returns empty results, and lifecycle hooks return immediately.

## Child responsibilities
- `plugin/` implements the disabled manager façade with empty state and execution results.
- `runtime/` implements the no-op application/activity lifecycle boundary.

## Integration
The overlay satisfies shared `PluginManager` and runtime-hook call sites using shared domain types. It deliberately has no CloudStream, QuickJS, DEX, external-repository, or HTTP integration.
