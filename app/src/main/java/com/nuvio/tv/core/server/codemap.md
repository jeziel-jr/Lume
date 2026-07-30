# app/src/main/java/com/nuvio/tv/core/server/

## Responsibility

Run the short-lived local HTTP configuration surfaces used by a phone or browser on the same
network. The package serves setup and settings pages, exposes small JSON APIs, validates proposed
changes, and hands confirmed changes back to the application through callbacks.

## Design and patterns

- NanoHTTPD servers use route-based `serve` dispatch, Gson or kotlinx serialization for payloads,
  and `startOnAvailablePort` helpers that probe a bounded port range. `DeviceIpAddress` discovers
  Wi-Fi first and falls back to non-loopback IPv4 interfaces.
- `XtreamSetupServer` creates a random 32-byte session key. The QR fragment carries that key; the
  phone encrypts credentials with AES/CBC/PKCS7 and authenticates IV plus ciphertext with HMAC-SHA256.
  The server decrypts, normalizes, requires complete `XtreamCredentials`, and tracks validation,
  confirmation, applied, rejected, and error states by UUID.
- `AddonConfigServer` and `RepositoryConfigServer` stage changes in concurrent maps and expose
  status polling. They reject older pending proposals when a new proposal arrives. The addon mode
  flags and `sanitizePendingAddonChange` preserve disallowed fields for restricted pages.
- Stream badge and debrid servers read current settings through providers, parse request bodies,
  normalize imports or preferences, and invoke change callbacks. The badge server supports up to
  three normalized imports and active-source selection. Web page objects embed localized HTML and
  JavaScript, with escaping helpers where values enter markup or scripts.

## Flow

The TV starts a server and supplies providers plus an application callback. A phone loads the
localized page, reads state or settings, submits a proposal, then polls `/api/status/<id>` while the
TV validates or confirms it. Confirmed callbacks update local repositories or DataStores; rejected
callbacks leave the local state unchanged. The Xtream page is the exception: its encrypted payload
is accepted first, then `onCredentialsProposed` performs provider validation and updates server
status for the phone.

## Integration

The servers integrate with addon, collection, TMDB, Trakt, stream badge, debrid, repository, and
Xtream setup flows. They use Android resources for localization and optional logo providers for
`/logo.png`. `AddonWebPage` models TMDB and Trakt collection sources and home catalog ordering;
`XtreamSetupServer` is paired with `QrCodeGenerator` and the per-device setup flow. These are local
network boundaries, not cloud backends.
