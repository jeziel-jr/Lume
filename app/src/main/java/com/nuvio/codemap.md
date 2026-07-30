# app/src/main/java/com/nuvio/

## Responsibility

The vendor and product namespace for Lume's shared application code. Its `tv` child contains the
Android TV application boundary, including startup, core services, data adapters, domain contracts,
and Compose presentation.

## Design

This package level establishes naming and ownership, not runtime behavior. The same
`com.nuvio.tv` package is used by `main`, `full`, and `playstore` so flavor overlays can substitute
capabilities behind stable interfaces. Provider DTOs and Android persistence remain below the
domain-facing boundaries rather than being exposed from this namespace level.

## Startup and flow

The application starts in `com.nuvio.tv.NuvioApplication`, then enters
`com.nuvio.tv.MainActivity`. Activity state is collected from Hilt services and local stores,
credentials gate the QR-first setup, and configured launches proceed through Compose navigation.
Content travels from remote or local data sources through repositories and core policies into domain
models and UI state; user actions travel back through ViewModels and repository ports.

## Integration

The child namespace integrates with Android TV, Hilt, Compose, Media3, TMDB, Xtream, local DataStore
files, and optional Supabase or flavor-specific services. `tv` is the only application namespace
under this level, so cross-layer dependencies are documented at that boundary.
