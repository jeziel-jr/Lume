# app/src/main/java/com/nuvio/tv/core/network/

## Responsibility

Defines the common Retrofit result contract, safe response wrapper, IPv4-first DNS policy, and playback stream measurement utilities.

## Design

- `NetworkResult` is a sealed `Loading`, `Success`, or `Error` state with an optional HTTP code. `SOURCE_SUFFICIENT_CODE` is a repository-level sentinel for metadata already satisfied by an addon catalog.
- `safeApiCall()` turns successful non-null Retrofit bodies into `Success`, HTTP failures and empty bodies into localized `Error`, rethrows coroutine cancellation, and maps other exceptions to messages.
- `IPv4FirstDns` delegates system lookup and stable-sorts IPv4 addresses before IPv6 addresses to avoid broken IPv6 route delays.
- `StreamSpeedTester` runs IO-bound eight-second baseline or three-connection ranged tests through `PlayerPlaybackNetworking`, calculates bits per second, and obtains content length with HEAD followed by GET fallback.

## Flow

Repositories and source resolvers emit `NetworkResult` states around Retrofit calls; UI and playback consumers branch on the sealed result. Hilt installs the DNS policy on shared clients, while stream diagnostics use the player's existing HTTP client and `ParallelRangeDataSource`, preserving the same playback path they measure.

## Integration

- `NetworkModule` uses `IPv4FirstDns` for Xtream, shared, direct-debrid, and related clients.
- `AddonRepository`, catalog/meta/stream repositories, and collection resolvers use `NetworkResult` and `safeApiCall`.
- `NuvioApplication` uses the DNS policy for Coil image fetching; trailer and player code also reuse the networking utilities.
- `DeepLinkHandler` maps addon fetch results into installation outcomes, while player diagnostics consume `StreamSpeedTester` measurements.
