# app/src/main/java/com/nuvio/tv/core/profile/

## Responsibility

`ProfileManager` is the application-level facade for local profiles. It exposes the active profile
and profile list as eagerly started `StateFlow`s, enforces profile count and primary-profile rules,
and coordinates profile-specific storage cleanup.

## Design and patterns

The Hilt `@Singleton` wraps `ProfileDataStore` for profile metadata and
`ProfileDataStoreFactory` for per-profile feature stores. The primary profile is id `1`, at most
six profiles can exist, and new IDs are selected from `2..6`. Names are trimmed with a localized
default fallback, while avatar and addon/plugin inheritance flags remain part of `UserProfile`.
Deletion refuses id `1`, marks the profile cleared through the factory, removes its DataStore files,
and deletes its `plugin_code_pN` directory.

## Flow

The manager eagerly mirrors active id, readiness, first-selection state, remember-last setting, and
profiles from the data store. `setActiveProfile`, `createProfile`, `updateProfile`, and
`deleteProfile` validate against the current in-memory flow before writing. Consumers read
`activeProfileId.value` for profile-scoped repositories and sync operations, while the UI observes
the exposed flows.

## Integration

Profile-scoped DataStores, addon/plugin inheritance, profile synchronization, and settings sync use
this facade as the active-profile source. Android file cleanup is deliberately kept here so callers
do not need to know DataStore filenames or plugin cache layout.
