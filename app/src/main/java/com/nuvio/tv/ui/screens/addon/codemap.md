# app/src/main/java/com/nuvio/tv/ui/screens/addon/

## Responsibility

TV management for installed Stremio-style addons and their Home catalogs. The folder
contains the main addon manager, an essential setup entry point, and a catalog-order
screen. It supports direct URL installation, enablement and ordering, Home catalog
visibility, collections, and QR-based phone configuration.

## Design

- `AddonManagerScreen` and `EssentialAddonSetupScreen` render the Hilt-provided
  `AddonManagerViewModel`; `CatalogOrderScreen` uses `CatalogOrderViewModel`.
  `AddonManagerUiState` and `CatalogOrderUiState` are immutable `StateFlow` render
  models, while Compose callbacks invoke view model commands.
- `AddonManagementAccess` centralizes profile and experience-mode policy. Secondary
  profiles using primary addons are read-only. Essential mode exposes addon-only web
  configuration, read-only mode exposes collections-only configuration, and advanced
  primary management uses the full configuration.
- Preferences are represented by stable catalog keys. Search-only catalogs are
  excluded from Home management. Legacy disable keys are accepted while current
  keys are generated with `homeCatalogKey`.
- Catalog ordering is data-driven. Normal mode permits individual catalog moves;
  follow-addons-order keeps addon catalogs in manifest order and moves collections at
  addon block boundaries. UI overlays handle QR state, pending changes, and transient
  messages without putting that state in composables.

## Flow

1. `AddonManagerViewModel` observes installed addons, profile-scoped layout
   preferences, experience mode, and collections. The manager screen waits for the
   mode, then renders installation, phone management, catalog-order, collections,
   refresh, and installed-addon controls according to access policy.
2. Installation trims and normalizes `http`, `https`, and `stremio` URLs, removes a
   trailing manifest path, fetches the addon through `AddonRepository`, and persists
   successful results. Reordering, enablement, and removal are also delegated to the
   repository and reflected by its installed-addon flow.
3. QR mode starts `AddonConfigServer` on an available port. The server receives the
   current addon, catalog, collection, disabled-key, and ordering state, and can
   resolve TMDB and Trakt source metadata or search requests for the phone page.
   Phone edits are sanitized by the server, converted into `PendingChangeInfo`, and
   enriched with addon names before TV confirmation.
4. Confirming a pending change persists addon order, validated catalog order and
   disabled keys, optional collection JSON, disabled collection keys, and the
   follow-addons-order flag. It triggers the relevant sync services, acknowledges the
   server request, then closes the local server. Rejection leaves local state intact.
5. `CatalogOrderViewModel` combines enabled addon manifests, collections, saved order,
   disabled keys, custom titles, and follow mode. It rebuilds the visible list when
   any source changes. Move and enable/disable commands write DataStore preferences
   and push Home catalog settings.
6. Essential setup reuses the manager view model for direct installation or an
   addons-only QR flow, while its continue action lets the caller leave setup without
   changing addon state.

## Integration

- `AddonRepository` supplies the installed-addon `Flow` and persistence operations;
  `Addon` and `CatalogDescriptor` provide the manifest model used by both view models.
- `LayoutPreferenceDataStore` stores profile-scoped Home order, disabled catalogs,
  custom titles, and follow mode. `CollectionsDataStore` stores and imports the
  collection JSON exchanged through the phone page.
- `HomeCatalogSettingsSyncService`, `CollectionSyncService`, and
  `StartupSyncService` propagate local changes or request an addon refresh. Home
  consumes the stable order and disabled keys when building its catalog rails.
- `AddonConfigServer` integrates the QR page with `DeviceIpAddress`,
  `QrCodeGenerator`, the app wordmark, `TmdbCollectionSourceResolver`, and
  `TraktPublicListSourceResolver`. `AddonWebConfigMode` limits the server payload and
  accepted changes for the current profile and experience mode.
- `NuvioNavHost` routes to addon management and catalog order, with onward navigation
  to collections. All screens use TV Compose focus handling, and QR or confirmation
  servers are stopped when their screen leaves composition or its view model clears.
