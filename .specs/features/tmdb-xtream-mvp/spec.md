# Lume TMDB and Xtream MVP Specification

## Problem Statement

Create a personal Android TV application that presents a complete TMDB movie and series catalog while using one authorized Xtream account only as a playback source. Catalog visibility must not depend on Xtream availability.

## Goals

- [ ] Browse TMDB movies and series in Portuguese through dynamic rails and optional TMDB lists.
- [ ] Search TMDB titles and open complete movie or series details.
- [ ] Browse TMDB seasons and episodes without requiring addon metadata.
- [ ] Resolve an authorized Xtream movie or episode only when playback is requested.
- [ ] Keep library, playlists, and progress local.
- [ ] Ship a preconfigured Lume APK without login or provider setup screens.

## Out of Scope

| Feature | Reason |
| ------- | ------ |
| Live TV and EPG | The product is limited to movies and series. |
| Account and cloud synchronization | The APK is for one local user. |
| Runtime credential editing | Credentials are supplied at build time. |
| Publishing or distributing the APK | The user stated the APK is personal. |
| Guaranteed fuzzy match for ambiguous series | Unsafe matches must fail closed. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Product identity | Lume, `com.jeziel.lume` | User selected the name. | yes |
| Language | `pt-BR` | User accepted the recommendation. | yes |
| Home content | Dynamic TMDB rails plus optional configured TMDB lists | User requested both. | yes |
| Multiple playback matches | Prefer dubbed and highest-quality candidates; show selection on ambiguity | User accepted the recommendation. | yes |
| Legacy implementation | Remove it from user-facing flows first; retain dormant internals required by the mature player | Minimizes regression risk. | assumption |

**Open questions:** none.

## User Stories

### P1: Browse TMDB Catalog

**User Story**: As the local viewer, I want movie and series rails from TMDB so that discovery is independent of provider inventory.

**Acceptance Criteria**:

1. WHEN Lume opens with valid TMDB configuration THEN it SHALL show movie and series rails localized to `pt-BR`.
2. WHEN a TMDB rail is paginated THEN Lume SHALL append unique items without replacing existing items.
3. WHEN TMDB is unavailable and cached catalog data exists THEN Lume SHALL retain the cached catalog.
4. WHEN TMDB is unavailable without cached data THEN Lume SHALL show a retryable connection error.

**Independent Test**: Open Home with a mocked TMDB service and verify direct movie and series rails.

### P1: Search and Inspect Titles

**User Story**: As the local viewer, I want to search all TMDB movies and series and inspect metadata so that I can discover any title.

**Acceptance Criteria**:

1. WHEN a nonblank query is submitted THEN Lume SHALL return separate TMDB movie and series rows.
2. WHEN a TMDB movie is opened THEN Lume SHALL show its TMDB metadata and artwork without querying an addon.
3. WHEN a TMDB series is opened THEN Lume SHALL generate seasons and episodes from TMDB.
4. WHEN an episode has not aired THEN Lume SHALL retain its metadata but not treat it as playable.

**Independent Test**: Search a title and navigate through series detail to a generated episode.

### P1: Resolve Xtream Playback

**User Story**: As the local viewer, I want Lume to resolve the selected TMDB title against Xtream at play time so that catalog completeness remains independent from playback availability.

**Acceptance Criteria**:

1. WHEN a movie has a candidate whose Xtream detail `tmdb_id` equals the selected TMDB ID THEN Lume SHALL return a direct movie stream URL.
2. WHEN a series candidate confidently matches title and year and contains the selected season and episode THEN Lume SHALL return a direct episode stream URL.
3. WHEN no safe match exists after a successful provider lookup THEN Lume SHALL show `Em breve` and SHALL NOT play another title.
4. WHEN Xtream authentication, network, or parsing fails THEN Lume SHALL show a connection error distinct from `Em breve`.
5. WHEN multiple safe candidates exist THEN Lume SHALL rank Portuguese dubbed markers before other candidates and rank higher quality first.

**Independent Test**: Resolve fixtures for a matching movie, matching episode, missing item, and provider error.

### P1: Start Without Login

**User Story**: As the local viewer, I want Lume to open directly into the app so that no account or addon setup is required.

**Acceptance Criteria**:

1. WHEN Lume starts for the first time THEN it SHALL bypass Supabase QR login, addon setup, layout setup, and experience selection.
2. WHEN the sidebar opens THEN it SHALL expose Home, Search, Library, and Settings without addon, debrid, or account destinations.
3. WHEN local library or watch progress changes THEN it SHALL persist without a remote account.

**Independent Test**: Clear application data, launch Lume, and verify Home is the first interactive screen.

## Edge Cases

- WHEN an Xtream title differs only by accents or common quality/language tags THEN normalization SHALL still produce the canonical title.
- WHEN two titles share a normalized name but their years differ THEN matching SHALL prefer the exact year and reject an ambiguous result.
- WHEN the selected episode is absent from the matched series THEN Lume SHALL return unavailable rather than another episode.
- WHEN the provider catalog cache is stale THEN Lume SHALL use it for lookup and refresh it in the background.
- WHEN any credential is blank THEN the build SHALL fail with a clear configuration error.

## Requirement Traceability

| Requirement ID | Story | Status |
| -------------- | ----- | ------ |
| CAT-01 | Browse TMDB Catalog | Verified |
| CAT-02 | Search and Inspect Titles | Verified |
| PLAY-01 | Resolve Xtream Playback | Verified |
| PLAY-02 | Playback errors and ranking | Verified |
| APP-01 | Start Without Login | Verified |
| APP-02 | Local personal state | Verified |
| CFG-01 | Build-time configuration | Verified |

**Coverage:** 7 total, 7 mapped to tasks, 0 unmapped.

## Success Criteria

- [ ] A clean install reaches TMDB Home without login or addon setup.
- [ ] TMDB movie search, movie details, series details, seasons, and episodes work in `pt-BR`.
- [ ] Verified Xtream fixtures resolve the correct movie and episode URLs.
- [ ] Missing content shows `Em breve`; provider failures show a connection error.
- [ ] Unit tests, Kotlin compilation, and a full debug APK build pass.
