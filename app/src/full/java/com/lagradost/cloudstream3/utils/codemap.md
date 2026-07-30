# app/src/full/java/com/lagradost/cloudstream3/utils/

## Responsibility
`DataStore` supplies extension-compatible settings storage and JSON conversion.

## Patterns
- A singleton Jackson `JsonMapper` serializes arbitrary Kotlin values as JSON.
- One `SharedPreferences` file is addressed with plain keys or `folder/path` keys.
- Read and write failures are contained, logged where appropriate, and represented as null, defaults, or zero removals.

## Flow
An extension calls context extension methods such as `setKey`, `getKey`, `getKeys`, or `removeKeys`. Folder helpers normalize trailing slashes, then JSON is stored in or read from `cs3_plugin_preferences`.

## Integration
The API mirrors CloudStream's utility calls and is used by external extensions. It is independent of Lume's application DataStore and does not persist `AcraApplication` keys.
