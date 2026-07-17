# Design QA: Search availability badge

- Source visual truth: `/Users/jeziel/.codex/generated_images/019f6f93-14c3-7f21-ba34-6b422fbf7bf5/exec-d012bedf-1d6c-4261-93f9-6f27bd89d373.png`
- Implementation screenshot: not captured
- Intended viewport: 1920 × 1080 Android TV / Fire TV
- Intended state: TMDB search results containing available, unavailable, and focused poster cards

**Findings**

- [P2] Runtime visual comparison is unavailable
  Location: Search results poster badge.
  Evidence: the selected source mock was inspected, but the native implementation was not launched or captured because the handoff explicitly requires installation only and the app must remain closed.
  Impact: typography rasterization, sofa-distance legibility, and poster-specific overlap cannot be confirmed before the user's manual test.
  Fix: during user-owned testing, compare an unavailable search result against the approved mock and report any overlap or legibility drift.

**Required Fidelity Surfaces**

- Fonts and typography: implementation uses the existing Lume `labelSmall` typography; runtime rendering not captured.
- Spacing and layout rhythm: implementation uses a 20 dp badge, 6 dp radius, 6 dp horizontal padding, 8 dp poster inset, 2 dp accent, and 5 dp internal gap; runtime rendering not captured.
- Colors and visual tokens: charcoal card surface at 92% opacity, ivory primary text, and the existing amber warning token match the approved direction in code; runtime sampling not available.
- Image quality and asset fidelity: existing TMDB poster assets remain unchanged; no replacement or generated runtime asset was introduced.
- Copy and content: `Indisponível` and `Verificando` are localized for Brazilian Portuguese.

**Full-view Comparison Evidence**

- Source mock inspected at 1920 × 1080.
- No equivalent implementation screenshot exists because the app was intentionally not launched.

**Focused Region Comparison Evidence**

- Blocked for the same reason; the poster badge cannot be visually compared from source code alone.

**Comparison History**

- Initial pass: blocked by the no-launch handoff constraint; no visual fixes were made from runtime evidence.

**Implementation Checklist**

- Confirm the badge remains inside the poster crop on Fire TV.
- Confirm `Indisponível` stays readable from normal TV distance.
- Confirm the badge does not collide with important poster artwork or expanded-card metadata.

**Follow-up Polish**

- Adjust only token-level padding or opacity if the user-observed Fire TV render differs from the approved mock.

final result: blocked
