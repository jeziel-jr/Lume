# app/src/main/java/com/nuvio/tv/core/config/

## Responsibility

Owns the operator-published provider configuration: the signed document that defines which Xtream
endpoint every installation should use, the verification of that document, and the download of it
from the public repository. Nothing here touches credentials or the catalog.

## Design

- `RemoteConfig` plus `RemoteConfigParser` model the document (`schema`, monotonic `revision`,
  `keyId`, `issuedAt`, ordered `endpoints`) and reject anything unsupported: unknown schema, a
  non-positive revision, a blank key identifier, or malformed JSON.
- `RemoteConfigVerifier` checks a detached ECDSA P-256 signature (`SHA256withECDSA`) over the raw
  bytes of the document against `RemoteConfigKeys.ACCEPTED`. `RemoteConfigSignature` holds the pure
  rule so tests can exercise it with generated keys instead of the shipped ones.
- `RemoteConfigKeys` is the trust anchor embedded in the APK: a map of key identifier to SPKI DER
  public key. Rotating the signing key means adding a new entry and keeping the previous one until
  every installed device has the release that contains it.
- `RemoteConfigFetcher` downloads `remote-config/xtream.json` and its `.sig` from
  `raw.githubusercontent.com` (`BuildConfig.GITHUB_OWNER`/`GITHUB_REPO`, branch `main`) with
  jsDelivr as mirror, using a dedicated OkHttp client: no disk cache (a cached document would defeat
  a rotation), strict TLS, IPv4-first DNS, and five-second timeouts so an unreachable host cannot
  delay startup. It returns the first location whose signature verifies.

## Flow

A caller asks for the current document. Each mirror is tried in order: the JSON is downloaded, the
signature downloaded, the document parsed, and the signature verified before anything is returned.
A failure at any step — HTTP error, unparseable body, unknown key, invalid signature — yields
`null`, and the caller keeps the configuration it already trusts.

## Integration

`XtreamEndpointResolver` is the only consumer: it decides when to refresh (startup, six-hour TTL,
connectivity failure reported by the server-health monitor, or the manual action in Settings),
persists the accepted document through `RemoteConfigStore`, and applies endpoint changes through
`XtreamSourceSwitcher`. Keys and the private signing tool live outside the app: the private key
stays on the operator machine and `scripts/sign_remote_config.py` produces the published signature.
