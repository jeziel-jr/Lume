# Project State

## Decisions

### AD-001: TMDB owns the catalog

- Status: active
- Decision: TMDB is the source of truth for discovery, search, metadata, artwork, seasons, and episodes.
- Rationale: Xtream is used only to resolve playback availability.

### AD-002: Shared and personal credentials have separate lifecycles

- Status: active
- Decision: the application-level TMDB key is compiled into `BuildConfig`, the Xtream provider endpoint is operator-controlled (published as a signed remote document, never editable by the user; AD-008), and Xtream username/password are configured per device at runtime and encrypted with an Android Keystore-backed key.
- Rationale: one APK can be shared without exposing the owner's playback credentials, while TMDB remains an application-level integration.

### AD-005: Xtream setup is local and QR-first

- Status: active
- Decision: an unconfigured installation must complete Xtream setup before Home. The TV serves a short-lived local page opened by QR, validates submitted credentials, and requires confirmation on the TV before persisting them.
- Rationale: phone entry avoids typing long credentials with a TV remote without adding a cloud account or backend.

### AD-003: Personal state remains local

- Status: active
- Decision: library, playlists, and watch progress use local persistence without account synchronization.
- Rationale: The app has one user and no cross-device synchronization requirement.


### AD-004: Legacy providers are absent from the UX

- Status: active
- Decision: Stremio addons, plugins, debrid, torrents, Supabase login, and Nuvio setup flows are not exposed by Lume.
- Rationale: Lume is focused exclusively on TMDB browsing and authorized Xtream VOD playback.

### AD-006: Xtream live TV is a snapshot with parental control

- Status: active
- Decision: live TV loads one in-memory snapshot (`get_live_categories` + `get_live_streams` in parallel, 20-minute TTL) and one lazy EPG lookup per channel. Channels play through `{base}/live/{user}/{password}/{streamId}.m3u8`. Adult categories are recognized by a single regex (`adult|adulto|onlyfans|18+`), hidden behind a salted SHA-256 4-digit PIN stored locally (never plain text), unlocked once per session; "Todos" always excludes adult channels.
- Rationale: 3008-channel catalogs would be unresponsive per-channel; a single snapshot keeps navigation lazy and cheap, and local PIN state follows the personal-state-stays-local decision.

### AD-007: In-app updates ship through GitHub Releases on push to main

- Status: active
- Decision: the full flavor checks `jeziel-jr/Lume` `releases/latest` on every release-build launch (plus the existing manual check in About) and installs updates in-app. A failed check stays silent and never suppresses later checks; the "ignore this version" action silences a release tag until a newer one appears. Every push to main runs `.github/workflows/auto-beta-release.yml`, which bumps `versionCode`/`versionName`, builds the signed `assembleFullRelease`, and publishes the universal APK as a non-draft GitHub Release. A `[skip release]` marker in a commit message skips publishing.
- Rationale: sideloaded Fire TV installs have no Play/Appstore channel; GitHub Releases is free on the already-public repo, and the existing updater compares release tags against the installed `versionName`.

### AD-008: The Xtream endpoint is operator-controlled and rotatable without an app update

- Status: active
- Decision: the provider endpoint lives in `remote-config/xtream.json` plus a detached ECDSA P-256 signature (`remote-config/xtream.json.sig`), published from the public repository and fetched by the app from `raw.githubusercontent.com` with jsDelivr as mirror. A device accepts only a document whose signature verifies against a public key embedded in `RemoteConfigKeys.kt` and whose monotonic `revision` is newer than the last applied one, then probes candidates before switching. Precedence: local operator override, stored account endpoint, last applied endpoint, compiled `BOOTSTRAP_ENDPOINT`. Endpoint changes are applied non-destructively (`XtreamCatalogRepository.switchEndpoint()` keeps serving the previous catalog until the new one is indexed) and never touch username or password. Users cannot edit the server: the QR page and the remote-control form collect only username and password, and the TV supplies the endpoint. A hidden five-OK gesture reveals a manual server field for the operator alone, marked in Settings and resettable there.
- Rationale: a blocked or burned provider domain previously required a full release and user action; the signed document makes rotation a one-commit operation (revision bump, `[skip release]`) that propagates in minutes while a compromised GitHub account or mirror cannot redirect credentials to an unapproved host. Non-destructive switching keeps Home playable during the reindex.

## Handoff

Estado atual, operação e pendências. O histórico por versão está em `changelogs/` e no `git log`.

- Release: a última publicada é `0.7.40-beta` (versionCode 1065, tag `0.7.40-beta`, asset `app-full-release.apk`), publicada pelo `auto-beta-release` no runner self-hosted (label `lume-release`); o updater in-app (flavor `full`) checa `releases/latest` na abertura e instala preservando dados. `scripts/release_beta.py` faz o bump de versão, assina e publica.
- Endpoint do provedor: `remote-config/xtream.json` (revision 2) tem `http://capone.icu` como primário e `https://capone.icu` como fallback. `https://capone.icu` não é usável pelo app hoje — o certificado é self-signed (`CN=XUI.one`) e o client HTTP da API valida TLS (só o de playback é trust-all) —, por isso http, que exige `--allow-insecure` no script de assinatura e deixa usuário/senha legíveis na rede. Verificado com a conta de teste em 2026-09-12: http responde 200 com XUI 1.5.13 e `user_info.auth=1`.
- Assinatura da config: a chave privada fica em `~/.lume/config-signing/xtream-config.key` (fora do repo) e perdê-la exige uma release com nova chave embutida. Editar o JSON sempre implica re-assinar o `.sig`, que cobre os bytes exatos.
- Aplicação no app: `XtreamEndpointResolver` aplica no boot, em TTL de 6 h, em falha de conectividade e por ação manual em Ajustes → Conta. Precedência: override do operador → endpoint da conta → último aplicado → `BOOTSTRAP_ENDPOINT` compilado (`http://capone.icu`). A troca é não destrutiva (`switchEndpoint()` mantém o snapshot antigo servindo até o novo índice ficar pronto) e nunca toca usuário/senha. O usuário não edita servidor; o gesto oculto 5×OK no setup revela `Servidor (manual)`.
- Operação: a rotação é feita por `python3 scripts/rotate_xtream_dns.py <endereço>` (valida o endereço, sobe a `revision`, edita o JSON, assina e verifica) ou pelo skill `.omp/skills/dns-rotation`; runbook completo em `REMOTE_ENDPOINT_RUNBOOK.local.md` (local, fora do git) e formato em `remote-config/README.md`.
- Release notes: o skill `.omp/skills/deploy-changelog` gera `changelogs/<próxima-versão>.md` em pt-BR antes de cada push que vira release; o workflow usa o arquivo como corpo da release (`--custom-notes-file`) quando ele existe no checkout, senão autogera bullets em inglês a partir dos commits.
- Disponibilidade: o filtro do Home usa `catalogState is Ready` como gate e exclui itens `UNKNOWN`/`UNAVAILABLE` quando o índice está pronto (a Home não mostra badges); `XtreamAlternativeTitleLookup` cobre títulos que o provedor indexa só por alternative titles. No cold start, a Home fica atrás de `CatalogBootstrapScreen` até `state is Ready`.
- Baseline de testes: 8 falhas pré-existentes/ambientais (2 DolbyVision device-policy, 3 NuvioExoPlayerPerformanceHelper RAM-tier, 2 TmdbMetadataService date-range, 1 media3); o restante da suíte e o lint seguem não verdes fora do escopo focado de disponibilidade.
- Assinatura do APK: os aparelhos instalados usam a debug keystore (`~/.android/debug.keystore`, `androiddebugkey`), então o secret `NUVIO_RELEASE_KEYSTORE_BASE64` precisa codificar esse mesmo arquivo para o update in-app instalar sem reinstalação.
- Validação em aparelho é do usuário: o APK é reinstalado com `adb install -r` e deixado fechado, sem launch automático.
- Blockers: o card retangular no launcher oficial exige uma listagem na Amazon Appstore, que não existe hoje; o asset pronto é `design/brand/lume/lume-fire-tv-appstore-icon-1280x720.png`.
- Branch: `main`.
