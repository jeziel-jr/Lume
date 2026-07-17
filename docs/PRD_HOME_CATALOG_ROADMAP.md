# PRD: Home, Catalog Availability, and Personalization Roadmap

## Document Status

- Status: active
- Product: Lume for Android TV / Fire TV
- Baseline release: `0.8.0-beta` (`versionCode=1054`)
- Last verified device: Fire TV `AFTMM` (`mantis`)
- Product direction: authorized Xtream content owns discovery and playback; TMDB only enriches provider-backed content
- Implementation status: `0.8.0-beta` makes Xtream authoritative across Home, Search, Catalog, details availability, and grouped playback sources, with resumable Room identity hydration and TMDB enrichment; the release APK is installed on the Fire TV and left closed for user-owned visual/playback validation

This document is the source of truth for the next product phases. All agents must read it before planning or changing Home, TMDB catalogs, Xtream indexing, playback availability, personalization, or catalog settings.

## 0.8 Catalog Authority Amendment

The previous TMDB-first catalog direction is superseded. From `0.8.0-beta`, a title may appear in Home, Search, or Catalog only when it originates in the configured Xtream inventory. Provider variants sharing a confirmed TMDB identity, or the same normalized title and release year while identity hydration is pending, form one visible title with multiple playback sources. TMDB remains available for artwork, localized copy, credits, trailers, episodes, ratings, and other enrichment after the provider title has been established.

The Xtream snapshot remains the immediate startup source. Movie identity hydration is incremental, persisted in Room, rate-limited to two concurrent detail requests, retried for transient failures, and resumed from missing rows after restart. Adult, XXX, and ReelsShort categories are never published to user-facing catalog surfaces.

## Executive Summary

Lume now has a rich catalog with 42 Home rails covering trends, popular and top-rated content, recent releases, currently airing series, genres, Brazilian productions, streaming platforms in Brazil, studios, decades, and editorial TMDB lists.

The next product goal is not to add more rows immediately. It is to make the existing catalog fast, reliably playable, personal, and manageable.

The recommended delivery order is:

1. Render Home progressively instead of awaiting every rail.
2. Persist and refresh the Xtream catalog instead of rebuilding it on every cold process start.
3. Prefer titles that can actually be resolved for playback.
4. Add local, watch-history-based recommendations.
5. Let the user organize a large Home safely.
6. Expand editorial collections only after the earlier phases meet their performance targets.

## Current Product Baseline

### What works

- Xtream is the source for discovery, search, catalog categories, and movie/episode playback.
- TMDB enriches metadata, artwork, credits, trailers, and episode presentation only for provider-backed content.
- Exact normalized Xtream titles can fall back to a unique candidate with no provider year; ambiguous candidates remain unavailable.
- Single-season Xtream entries mislabeled with the two-digit release year are normalized to TMDB season 1.
- Home catalog definitions are data-driven and share pagination behavior.
- The four original rails remain available.
- Weekly movie and series trends are supported.
- Recent releases and series with new episodes are supported.
- Genre, country, language, studio, decade, platform, and editorial-list rails are supported.
- Streaming-provider rails use TMDB availability for Brazil.
- Mixed movie/series rails preserve the type of each item and do not show a false `Filme` suffix.
- A failed individual TMDB rail does not fail the entire Home response.
- The automatic in-app update prompt no longer runs on startup; manual update checking remains available.

### Current catalog groups

- Core: trending, popular, top rated, recent releases, and currently airing series.
- Genres: action, adventure, comedy, horror, thriller, science fiction, fantasy, animation, documentary, crime, mystery, romance, family, war, history, western, music, and reality.
- Regional: Brazilian productions, anime, and Korean dramas.
- Platforms in Brazil: Netflix, Prime Video, Disney+, Apple TV, HBO Max, Paramount+, and Globoplay.
- Editorial and studios: Oscar Best Picture winners, Marvel Cinematic Universe, Pixar, Marvel Studios, Studio Ghibli, A24, and Lucasfilm.
- Decades: 1980s, 1990s, and 2000s.

### Verified performance problem

On the target Fire TV, a cold launch after reinstall spent approximately 50–80 seconds in Xtream preparation and Home catalog loading before useful content became visible.

The main causes are:

1. `MainActivity` blocks navigation while the Xtream catalog state is not ready.
2. `TmdbCatalogService.home()` waits for the full set of Home rails before returning.
3. Mixed platform and genre rails can require both movie and TV requests.
4. The in-memory Home cache does not survive process death or reinstall.
5. The Xtream catalog index is rebuilt instead of immediately serving a persisted last-known-good snapshot.

### Availability problem

TMDB describes what is discoverable or available on commercial providers in Brazil. It does not guarantee that a title exists in the authorized Xtream catalog used for playback.

Therefore, a visually valid Home item can still fail playback resolution. The product should progressively move from “interesting content” to “interesting content that Lume can play.”

## Product Principles

1. Home should become useful before all data is ready.
2. A stale but valid catalog is better than an empty loading screen.
3. Every visible title should have a high probability of successful playback.
4. Background refresh must never replace good cached data with an empty or failed response.
5. Personalization remains local to the device.
6. The user controls Home density and ordering.
7. More catalogs are valuable only when startup, navigation, and playback remain reliable.
8. Existing Continue Watching and playback behavior must not regress.

## Goals and Success Metrics

### Primary goals

- Make Home interactive quickly on the target Fire TV.
- Reuse a persisted Xtream index across process starts.
- Reduce dead-end titles that cannot resolve to a playable Xtream item.
- Provide useful local recommendations without adding an account or backend.
- Make 42 or more rails manageable.

### Performance targets

- Warm start: Home shell and cached useful content visible within 3 seconds.
- Cold process start with a valid cache: useful Home content visible within 5 seconds.
- First install or invalidated cache: visible progress and partial Home within 10 seconds when network conditions permit.
- Initial TMDB batch: no more than 6 rails required before Home is usable.
- Background TMDB request concurrency: bounded and configurable; default target is 4 concurrent calls on TV hardware.
- Vertical navigation must remain responsive while remaining rails load.

### Quality targets

- A failure in one rail never hides successful rails.
- Cached Xtream data remains available when refresh fails.
- No duplicate title appears twice in the same rail.
- Mixed rails preserve movie/series item type and correct playback routing.
- Availability-filtered rails contain at least 12 playable items when enough source data exists.
- The app explains an empty rail or retry state without blocking unrelated content.

## Non-Goals

- Building a cloud recommendation backend.
- Adding multi-user synchronization.
- Reintroducing Stremio addons, debrid, torrents, or Supabase login into the Lume UX.
- Replacing TMDB as the discovery and metadata source.
- Treating TMDB streaming-provider availability as proof of Xtream playback availability.
- Adding more catalog sources before startup and availability work is stable.
- Deleting the existing catalog expansion to solve performance problems.

## Phase 1: Progressive Home Loading

Implementation: complete in `0.7.20-beta`. The initial six catalogs publish independently, remaining rows retain stable placeholder positions and load near the viewport, and TMDB calls are limited to four real requests. Fire TV acceptance timing remains to be recorded.

### User outcome

The user reaches a useful Home quickly. Continue Watching, hero content, and a small core catalog appear first. Additional rails arrive progressively without a full-screen wait.

### Required behavior

1. Home must not wait for all catalog definitions before rendering.
2. Render the app shell and Continue Watching as soon as their local state is available.
3. Load an initial group of no more than 6 rails:
   - Filmes em alta nesta semana
   - Series em alta nesta semana
   - Filmes populares
   - Series populares
   - Lancamentos recentes
   - Series com episodios novos
4. Represent remaining rails as lightweight placeholders in their final order.
5. Load a placeholder when it is near the visible viewport, not only after it is already focused.
6. Bound catalog request concurrency to avoid saturating Fire TV networking and CPU.
7. Allow successful rails to render while other requests are pending or failed.
8. Preserve pagination and “load more” behavior for every real row.
9. Avoid resetting vertical focus or scroll position when a placeholder becomes a real row.
10. Keep the last successful in-memory row if a refresh fails.

### Suggested technical direction

- Replace the all-at-once `home(): List<CatalogRow>` dependency with either:
  - a stream of ordered catalog updates; or
  - catalog definitions plus `loadCatalog(id, page)` requests managed by `HomeViewModel`.
- Reuse the existing placeholder and lazy-catalog concepts already present in the legacy Home pipeline.
- Keep catalog definitions centralized in `TmdbCatalogService` or a dedicated catalog registry.
- Do not duplicate the filter and pagination logic back into Compose code.

### Acceptance criteria

- Home becomes navigable before all 42 rails finish loading.
- Slow or failed editorial lists do not delay the core Home.
- Focus remains stable while rows populate.
- Relaunching the app with a live process does not refetch every loaded row unnecessarily.
- Unit tests cover ordered partial success, individual failure, and pagination after lazy loading.
- Fire TV validation records time to shell, first content, and full visible initial group.

## Phase 2: Persistent Xtream Catalog Cache

Implementation: complete in `0.7.20-beta`. A compressed last-known-good snapshot is indexed off the UI thread, keyed by schema and a credential-free endpoint/user fingerprint, reused while stale, and refreshed without replacing valid data on failure or implausibly small responses. Fire TV size/load-time measurements remain to be recorded.

Clean-start update in `0.7.21-beta`: the Home no longer waits for an uncached Xtream download or index. Index construction uses a single normalization pass and one title map per media type on a background-priority dispatcher; details remain usable and show checking until availability becomes ready.

### User outcome

Returning users do not repeatedly wait on “Preparando catálogo…”. Lume opens from the last-known-good catalog and refreshes in the background.

### Required behavior

1. Persist a normalized Xtream movie and series index after successful refresh.
2. Load the persisted snapshot before starting a network refresh.
3. Treat the snapshot as usable even when stale; expose staleness separately.
4. Refresh in the background based on a configurable TTL. Initial recommendation: 6 hours.
5. Atomically replace the cache only after a complete, valid refresh.
6. Keep the prior cache if the refresh returns an error or an implausibly empty catalog.
7. Invalidate safely when the configured Xtream endpoint or username changes.
8. Never persist the Xtream password, TMDB key, or raw credentials in the catalog cache.
9. Store a schema version and support explicit migration or rebuild.
10. Provide a settings/debug action to refresh or rebuild the catalog manually.

### Storage guidance

The catalog may be too large for Preferences DataStore. Choose a storage format based on measured index size and lookup needs:

- Room if indexed queries, migrations, and incremental updates justify the dependency.
- An atomic compressed file if the dominant operation is loading a complete immutable snapshot.

The implementation decision must include measurements for serialized size, load time, lookup time, and memory impact on the Fire TV.

### Acceptance criteria

- A valid cached index allows navigation past bootstrap without waiting for the network.
- Background refresh does not interrupt Home or playback.
- Force-stopping and reopening the app reuses the cache.
- Failed refresh preserves prior playable results.
- Cache invalidation is covered by tests for endpoint/user/schema changes.
- No credential value appears in the persisted index or logs.

## Phase 3: Playback Availability Filtering

Implementation complete in `0.7.25-beta`: exact movie and series/episode availability results persist locally, focused-card prefetch remains debounced, Home filters confirmed or conservatively unmatched titles through the in-memory Xtream index, and sparse rails expand to a maximum of five TMDB pages. Search intentionally keeps the full TMDB result set and labels locally unavailable items with the approved charcoal, ivory, and amber `Indisponível` badge. When no Xtream index exists yet, the first Home page remains non-blocking and search uses a temporary `Verificando` state; loaded Home rails are refetched and filtered once the index becomes ready. Movie playback accepts a strict title/year candidate when the provider detail has a missing or zero TMDB ID, but still rejects any positive ID that points to a different TMDB movie.

### User outcome

Catalog rows prioritize titles that Lume can resolve and play from the authorized Xtream source.

### Matching strategy

Use the strongest available match in this order:

1. Exact TMDB ID from Xtream metadata.
2. Exact normalized title plus release year.
3. Conservative normalized title match with a compatible year tolerance.
4. No match: treat as unavailable rather than guessing aggressively.

Movies and series must use separate indexes. Episode playback still resolves through series and episode metadata.

### Required behavior

1. Use a fast local availability lookup that makes no metadata or Xtream detail calls while classifying a rail.
2. Filter Home items using the local Xtream index while keeping the first no-index startup non-blocking.
3. Fetch additional TMDB pages when filtering leaves a rail too sparse.
4. Bound page expansion to at most 5 TMDB pages per rail load.
5. Stop early once the target rail size reaches 20 items.
6. Keep a diagnostic reason for unavailable or ambiguous matches.
7. Do not silently route a title to a clearly different movie or series.
8. Keep all TMDB titles discoverable in Search, show `Indisponível` before navigation, and allow details to open with playback disabled.

### Acceptance criteria

- Selecting an availability-filtered item resolves without a second full-catalog scan.
- Exact TMDB ID wins over title-based candidates.
- Ambiguous same-title remakes do not resolve without compatible year evidence.
- Tests cover accents, punctuation, alternate year formats, remakes, movies, series, and no-match behavior.
- Fire TV smoke testing covers at least one movie and one episode from several rail types.
- Search visually distinguishes unavailable and checking states without marking playable titles.

## Phase 4: Local Personalization

### User outcome

Home becomes more useful over time based on what the user actually watches.

### Candidate rails

- Porque voce assistiu...
- Recomendados para voce
- Continue esta serie
- Novidades nos seus generos favoritos
- Parecidos com filmes que voce terminou
- Esquecidos na sua biblioteca
- Para assistir novamente

### Required behavior

1. Use local watch progress, completed titles, library state, and recent focus/click signals.
2. Use TMDB recommendations or similar-title endpoints as candidates, then apply Xtream availability filtering.
3. Exclude watched titles where repetition would be undesirable.
4. Explain the seed in user-facing titles such as `Porque voce assistiu Duna`.
5. Limit repeated franchises and duplicate recommendations across adjacent rails.
6. Keep all personalization local and resettable.
7. Provide sensible fallback rails when history is insufficient.

### Acceptance criteria

- A completed or substantially watched title can seed a recommendation rail.
- Personalized candidates are filtered for playback availability.
- Clearing local history removes derived personalization.
- Recommendations do not block core Home loading.

## Phase 5: Home Organization and Controls

### User outcome

The user can enjoy a large catalog without scrolling through unwanted sections.

### Required controls

- Hide or show a rail.
- Reorder rails.
- Pin favorite rails near the top.
- Restore the default order.
- Group rails by Core, Platforms, Genres, Editorial, Studios, Regional, and Decades.
- Keep Continue Watching and critical local rows in protected positions unless explicitly configured.

### UX guidance

- Default Home should remain curated and useful without configuration.
- Do not place editing controls directly in the D-pad playback path.
- Prefer a dedicated Home Catalog settings screen with clear previews and reset behavior.
- Preserve focus and order when a rail refreshes.
- Store user choices locally and migrate by stable catalog ID.

### Acceptance criteria

- Settings survive process death and app upgrades.
- New catalog IDs receive a deterministic default position.
- Removed catalog IDs do not break saved ordering.
- Reset restores the product default exactly.
- Hidden rails do not issue background TMDB requests unless required elsewhere.

## Phase 6: Editorial Expansion

Only begin this phase after the performance and availability acceptance criteria are met.

### Candidate editorial sources

- TMDB public or maintained lists.
- Trakt public lists already supported by the repository.
- Stable TMDB collections for true sequels.
- Dynamic company, keyword, genre, language, country, and decade filters.

### Candidate collections

- Cannes and Palme d'Or selections.
- Golden Globe winners.
- IMDb-style essentials, subject to a source with acceptable terms and maintenance.
- Star Wars.
- DC universes.
- Harry Potter and Wizarding World.
- The Lord of the Rings and Middle-earth.
- James Bond.
- Essential horror, animation, science fiction, and world cinema.
- Directors, actors, and studio spotlights.

### Editorial rules

1. Prefer maintained, attributable sources.
2. Record the external source ID and expected media type.
3. A stale or deleted list must fail independently.
4. Avoid several adjacent rails containing nearly identical items.
5. Apply playback availability filtering after source resolution.
6. Confirm that third-party data usage and attribution requirements are satisfied.

## Cross-Phase UX States

### Loading

- Never use a blank screen when cached or partial content exists.
- Use row-level placeholders for pending remote content.
- Keep the Home shell and navigation responsive.

### Empty

- Distinguish “no source results” from “no playable matches.”
- Allow retry without restarting the app.
- Do not render an empty rail heading with no explanation.

### Error

- Keep successful rows.
- Show compact row-level retry affordances where useful.
- Log enough source and catalog ID context for diagnosis without logging secrets.

### Stale cache

- Use stale data normally unless it is structurally invalid.
- Refresh silently in the background.
- Show cache age only in diagnostics or when refresh repeatedly fails.

## Diagnostics and Measurement

Add structured timing around:

- Activity creation.
- Xtream cache read.
- Xtream network refresh.
- Xtream index construction.
- Home shell composition.
- First useful Home content.
- Initial six rails complete.
- Individual catalog request duration and result count.
- Availability filtering input/output counts.

Diagnostics must avoid credentials and full playback URLs. A debug/settings diagnostic card should make the latest timings inspectable on the TV.

## Test Strategy

### Unit tests

- Catalog registry order and stable IDs.
- Progressive partial success and failure isolation.
- Pagination for movie, series, mixed, platform, and editorial rails.
- Persistent-cache serialization, schema, invalidation, and atomic replacement.
- Availability matching and ambiguity handling.
- Saved Home order migration and reset behavior.
- Personalization seed selection, deduplication, and exclusion rules.

### Integration tests

- MockWebServer tests for delayed, failed, empty, and paginated TMDB responses.
- Cached Xtream startup while the network refresh is delayed or unavailable.
- Process restart with a populated cache.
- Concurrent Home loading while playback resolution reads the same index.

### Device validation

On the target Fire TV:

1. Measure cold start with no cache.
2. Measure cold process start with a valid cache.
3. Measure warm start.
4. Navigate vertically while rails load.
5. Open mixed, platform, editorial, and personalized rows.
6. Play at least one movie and one series episode.
7. Force a TMDB failure and verify partial Home remains usable.
8. Force an Xtream refresh failure and verify cached playback still works.

## Delivery Sequence

Each phase should be implemented and validated independently:

1. Progressive Home loading.
2. Persistent Xtream cache.
3. Availability filtering.
4. Local personalization.
5. Home organization.
6. Editorial expansion.

Do not combine all phases into one large change. Each phase must leave the app buildable, installable, and testable on the Fire TV.

## Definition of Done for Each Phase

A phase is complete only when:

- Its acceptance criteria are satisfied.
- Focused automated regression tests pass.
- `:app:assembleFullRelease` succeeds using the authorized local signing mode.
- The version is incremented for the test release.
- The APK is installed with `adb install -r` so data is preserved.
- The release APK is reinstalled on the Fire TV with data preserved and left closed for user-owned manual launch and testing.
- There is no immediate AndroidRuntime crash.
- Relevant screenshots or timing evidence are recorded.
- This PRD's implementation status and `.specs/STATE.md` are updated.

## Open Product Decisions

These decisions should be resolved with the user when their phase begins:

1. Should platform rails mean TMDB commercial availability in Brazil, Xtream-provided category labels, or both as separate concepts?
2. What is the preferred TTL for Xtream refresh: 3, 6, 12, or 24 hours?
3. Should Home organization use grouped section headers or only a flat ordered list?
4. Which personal signals may influence recommendations beyond watch progress and library membership?
5. Which editorial sources are trustworthy enough to ship by default?

Until changed explicitly, use these defaults:

- Favor playable titles on the primary Home.
- Hide unavailable titles on Home; keep them discoverable in Search with the approved badge and accessible metadata details.
- Keep TMDB Brazil platform rails labeled as platform availability, not as Xtream categories.
- Use a 6-hour Xtream refresh TTL.
- Keep the current flat Home presentation during performance work.
- Keep personalization local and based on watch/library state only.
