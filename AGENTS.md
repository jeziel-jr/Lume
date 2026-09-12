# Repository Guidelines

## Required Project Context

Before starting any work in this repository, read these files completely:

1. `.specs/STATE.md` — current architecture decisions, handoff, and verified runtime state.
2. `docs/PRD_HOME_CATALOG_ROADMAP.md` — active product roadmap and acceptance criteria for startup, catalog availability, personalization, and home organization.

Treat both documents as required context, not optional reference material. The latest explicit user request takes precedence if it conflicts with an older document. When implementation changes the documented state or completes a roadmap phase, update the relevant repository document in the same change.

## Project Structure & Module Organization

Lume is a Kotlin/Jetpack Compose Android TV application. The main app lives in `app/`: production Kotlin is under `app/src/main/java/com/nuvio/tv`, Android resources under `app/src/main/res`, JVM tests under `app/src/test`, and device tests under `app/src/androidTest`. Distribution-specific code belongs in `app/src/full` or `app/src/playstore`. `baselineprofile/` generates startup profiles, while `ffmpeg-decoder-downmix/` contains the optional native decoder. Keep bundled AARs in their existing `app/libs/` location.

## Build, Test, and Development Commands

Copy `local.example.properties` to `local.properties` and provide the required TMDB and authorized Xtream credentials before running Gradle.

- `./gradlew :app:assembleFullDebug` builds the universal development APK in `app/build/outputs/apk/full/debug/`.
- `./gradlew :app:testFullDebugUnitTest` runs the local JVM test suite.
- `./gradlew :app:connectedFullDebugAndroidTest` runs instrumentation tests on a connected TV device or emulator.
- `./gradlew :app:lintFullDebug` runs Android lint for the primary development variant.

Use the checked-in Gradle wrapper; do not rely on a system Gradle installation.

## Fire TV Installation Handoff

After completing an app change that is ready for device testing, build and reinstall the updated APK on the configured Fire TV with `adb install -r` so existing app data is preserved. Do not launch, open, or bring the app to the foreground after installation. Leave the app closed; the user owns the manual launch and testing step. If the device is unavailable or installation cannot be completed, report that explicitly instead of treating the task as fully handed off.

## Coding Style & Naming Conventions

Follow the official Kotlin style configured in `gradle.properties`: four-space indentation, trailing commas where they improve diffs, and idiomatic null-safety. Use `PascalCase` for types and Compose functions, `camelCase` for functions/properties, and `UPPER_SNAKE_CASE` for constants. Keep packages lowercase beneath `com.nuvio.tv` and organize additions by feature/layer (`core`, `data`, `domain`, `ui`). Resource names use lowercase snake case, for example `ic_player_pause.xml`. No standalone formatter is configured; use Android Studio formatting and run lint.

## Testing Guidelines

Tests use JUnit 4, MockK, and `kotlinx-coroutines-test`; UI/device tests use AndroidX Test and Compose testing. Name files `*Test.kt` and describe behavior in backtick test names. Add focused regression tests beside the affected package. There is no fixed coverage threshold, but changed behavior must be exercised automatically or documented with targeted manual testing.

## Commit & Pull Request Guidelines

History favors concise imperative subjects, often Conventional Commit-style (`fix:`, `feat:`, `ref:`); keep each commit focused. Read `CONTRIBUTING.md` before opening a PR. Current policy accepts localization updates and critical bug fixes only. Complete the PR template, link the issue, include reproduction and testing notes, state scope boundaries, and attach before/after screenshots or video for UI fixes. Never commit `local.properties`, credentials, signing files, or generated build output.

## Deploy & Release (OTA)

Every push to `main` publishes a beta release via `.github/workflows/auto-beta-release.yml`. Before any push that should become a release:

1. MUST run the project skill `deploy-changelog` (`skill://deploy-changelog`) and produce `changelogs/<próxima-versão>.md` in Brazilian Portuguese.
2. Commit that changelog file together with the release changes — the workflow uses it as the GitHub release notes; without it, notes are auto-generated in English from commit subjects.
3. Put `[skip release]` in the commit message when the push must not publish a release (docs/infra-only).

## Provider DNS (remote endpoint rotation)

The Xtream provider address lives in `remote-config/xtream.json` (signed, published on `main`) and is applied by the app without a release. When the user asks to change the DNS/address ("trocar o dns", "mudar o endereço do servidor", "o domínio caiu"):

1. MUST run the project skill `dns-rotation` (`skill://dns-rotation`). If the new address was not given, ask for it first — one question, including the scheme (`http://` or `https://`).
2. MUST do the work through `python3 scripts/rotate_xtream_dns.py <endereço>` (validates the host, bumps `revision`, edits the JSON, signs and verifies) and publish both `remote-config/xtream.json` and its `.sig` in the same commit.
3. MUST use `[skip release]` for a config-only push, keep the previous address as fallback, and never edit the JSON without re-signing. `http://` requires `--allow-insecure` and an explicit risk note to the user.
4. Operational reference: `REMOTE_ENDPOINT_RUNBOOK.local.md` (local, not committed).

## Repository Map

A full codemap is available at `codemap.md` in the project root.

Before working on any task, read `codemap.md` to understand:
- Project architecture and entry points
- Directory responsibilities and design patterns
- Data flow and integration points between modules

For deep work on a specific folder, also read that folder's `codemap.md`.
