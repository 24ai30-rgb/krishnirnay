# PHASE UI/UX + FARMER REGISTRATION — Implementation Report

See `UI_UX_REDESIGN_AUDIT.md` for the full audit this implementation was based on.
This report covers what was concretely built/changed in the Android app across this
phase (spanning the onboarding-flow session and this session's Settings/Profile-
completion follow-up), and is deliberately explicit about what was deep-redesigned
vs. verified-already-adequate vs. left alone, per the "do not claim a feature works
unless verified" instruction.

## 1. Screens redesigned or extended this phase

- **Farmer Onboarding** (new): `feature/onboarding/{OnboardingUiState,
  OnboardingViewModel, OnboardingScreen}.kt` — 6-step flow with progress indicator,
  back/continue, per-step validation, resumable (seeded from the existing profile).
- **Settings**: reorganized from a flat control list into labeled sections — Profile
  / Language & Voice / AI / Advanced — with two new navigation entries (Farmer
  Profile, Farm Information) and a new Voice Assistant switch.
- **Profile**: added a real profile-completion card above the existing menu list.
- **Dashboard / Weather** (prior session): centralized the LIVE/CACHED/MOCK/
  UNAVAILABLE badge into `StatusBadge`, replacing two duplicated inline
  implementations; Weather's unavailable state now uses the shared `EmptyState`
  component with a real (non-hardcoded, localized) message instead of a raw `Text`.

## 2. Screens verified as already meeting the phase's UI requirements (not modified)

Checked directly against the spec's per-screen requirements rather than assumed:

- **Dashboard**: decision hierarchy is driven by `RiskBadge` (Hero size for the primary
  decision, Compact for secondary), which renders `DecisionEngine`'s actual output —
  no UI-side agricultural logic.
- **Weather**: `WeatherScreen.kt` branches on `DataSourceStatus.UNAVAILABLE` explicitly
  and shows `StatusBadge` for LIVE/CACHED — never labels cached data as live.
- **Schemes**: `SchemesViewModel` calls `GovernmentSchemeMatcher.match(profile,
  schemes)` — deterministic, not LLM-based, confirmed by reading the call site.
- **Feedback**: `FeedbackViewModel` is wired directly to `FeedbackRepository` — persists
  through the existing DataStore-backed repository, not a new store.
- **Simulation**: already has all requested scenarios (dry/normal/wet soil, high/low
  temp, high/low humidity, disconnect/reconnect) and is visually marked as simulation
  mode, gated to Mock mode only.

These were **not** rewritten — they already satisfy the requirement, and per the
phase's own "do not replace working business logic/UI just to change UI" and STOP-RULE
guidance, a screen that already renders the correct real data with the correct honest
status is left alone rather than churned for its own sake.

## 3. Screens NOT deep-redesigned this pass (honest gap)

Fertilizer/Advisory, Pest, Disease, Market, Sensor and Voice screens were **not**
given a fresh visual pass in this session — they use the existing `KnCard`/typography/
spacing system already, so they are visually consistent with the rest of the app, but
they were not individually re-audited line-by-line against every sub-requirement in
sections 13–19 of the brief (e.g. explicit "Latest available mandi price" wording,
per-sensor "last updated" timestamps). This is a real, acknowledged gap, not a claim
of completeness — flagged here rather than glossed over.

## 4. Components created

- `StatusBadge` (prior session) — single source of truth for LIVE/CACHED/MOCK/
  UNAVAILABLE across Dashboard and Weather (Market/Sensor screens still render status
  inline; not migrated this pass).
- `KrishiTextField` (prior session) — the one labeled text field used throughout
  Onboarding.
- `ProfileCompletionCard` (this session) — animated progress bar + missing-field list,
  driven entirely by `ProfileCompletion.kt`'s pure calculation (no hardcoded percent).

## 5. Farmer onboarding flow

6 steps: Welcome → Personal (name, phone, alternate mobile, gender, address, state,
district, taluka, village) → Farm (lat/lng, acres, ownership, irrigation available,
water source) → Crop (primary/secondary crop, variety, stage) → Preferences (language,
farming experience, soil type, voice assistance) → Confirmation (summary + Save).
Writes through the existing `ProfileRepository`/`FarmerProfile` — confirmed no second
persistence mechanism was introduced. Gates via `SettingsRepository
.hasCompletedOnboarding`.

## 6. Profile fields

`FarmerProfile` carries (all pre-existing from the prior onboarding session, none
added this session): name, phone, alternateMobile, gender, address, farmLocation
(state/district/taluka/village/lat/lng), farmSizeAcres, ownershipType, waterSource,
crops, cropVariety, seedlingStage, irrigationMethod, soilType, farmingExperienceYears,
voiceAssistanceEnabled, dateOfBirth, expectedHarvestDate. None of the onboarding-added
fields are read by `DecisionEngine`/`FieldDecisionResolver` (documented in the model's
own code comment) — profile completeness is cosmetic/UX only, never a decision input.

## 7. Profile completion (new this session)

`ProfileCompletion.kt` checks 13 fields that are genuinely blank when unset (name,
phone, state, district, village, primary crop, farm size, gender, address, ownership
type, water source, crop variety, farming experience years). `soilType` and
`irrigationMethod` are deliberately excluded — both default to a real, valid value
("Black Soil" / rain-fed), so an untouched default can't be distinguished from a
farmer's deliberate choice, and counting it would produce a false "100% minus one"
reading. Percent = filled/13 × 100, computed fresh from the live `FarmerProfile` on
every read — never hardcoded. Unit-tested in `ProfileCompletionTest.kt` (4 tests: 0%,
100%, partial, and confirming soil/irrigation defaults never count as "missing").

## 8. Localization changes

All new strings (Settings section headers, nav row labels, Voice Assistant
label/description, profile-completion template/missing-label/complete-message) added
to `AppStrings.kt`'s EN/HI/MR blocks — none hardcoded into a composable. Reused
existing onboarding/farmSetup keys for `ProfileCompletion`'s missing-field labels
rather than duplicating strings.

## 9. Animation changes

No new animation library. `ProfileCompletionCard`'s progress bar animates via
`animateFloatAsState` (400ms tween), consistent with the existing pattern already used
elsewhere (Profile menu-row press scale, RiskBadge transitions, Onboarding's
`AnimatedContent` fade between steps).

## 10. Accessibility improvements

No regressions introduced: `KrishiTextField` continues to show explicit "*" (required)
or "(optional)" labels; `ProfileCompletionCard` uses the existing typography scale
(no new small text sizes below the established 16sp body floor); Settings' new nav
rows use the same touch-target sizing (`padding(vertical = 14.dp)`) as the existing
Profile menu rows they were modeled on.

## 11. Mock/Live behavior

Not modified this session; verified unchanged: `DefaultFieldStateRepository` still
delegates to `MockControls` only when `isMockActive()`, Settings' Mock/Live toggle is
unchanged (moved into the "Advanced" section, same `SegmentedToggle`, same
`viewModel::setAppMode`).

## 12. Tests added/updated

- `ProfileCompletionTest.kt` (new, 4 tests): blank profile → 0%; fully filled → 100%,
  no missing labels; soil type/irrigation method never counted as missing; partial
  profile → proportional percent.
- No existing tests were weakened or deleted.
- Fixed a real, pre-existing-to-this-session regression: `AppStrings` was a `data
  class` with a 264-parameter primary constructor (one field per localized string) —
  the prior session's ~31 new onboarding keys pushed it past the JVM's 255-parameter-
  slot limit on a single method, producing `ClassFormatError: Too many arguments in
  method signature` at class-load time (would have crashed the real app on first
  launch, not just tests). Fixed by converting `AppStrings` to a plain class with
  individual `var` properties, assigned via `.apply { }` blocks in `EnglishStrings`/
  `HindiStrings`/`MarathiStrings` — no call site (`strings.xxx` reads) needed to
  change. Root-caused via the actual `ClassFormatError` message, not guessed.

## 13. Build result

```
./gradlew testDebugUnitTest --rerun-tasks   → BUILD SUCCESSFUL, 125 tests, 0 failures, 0 skipped
./gradlew assembleDebug                     → BUILD SUCCESSFUL
```

Server-side Python tests were not touched this session (no shared contract — DTOs,
routes — was changed).

## 14. Remaining blockers / honestly deferred work

- Market/Sensor screens still render their status badges inline rather than through
  the shared `StatusBadge` component — functionally correct (verified LIVE/CACHED/
  MOCK/UNAVAILABLE are never mislabeled) but not migrated for consistency.
- Fertilizer/Pest/Disease/Voice screens were not individually re-audited against every
  sub-bullet of the phase brief (see §3) — flagged, not silently assumed complete.
- Dark mode: still out of scope, per the phase brief's own "light theme is the
  priority" instruction and the pre-existing `Theme.kt` scope note.
- Named components `WeatherCard`/`MarketCard`/`SensorCard`/`RecommendationCard`/
  `FeedbackCard`/`SchemeCard`/`VoiceButton`/`LanguageSelector` exist as inline
  per-screen composables, not extracted into `core/designsystem/components/` files —
  a mechanical, non-behavior-changing refactor judged out of proportion to this
  session's time budget and risk tolerance; not done.
- Onboarding remains 6 steps (folding "farming information"/history fields into
  existing steps) rather than the phase brief's literal 7-step breakdown — a
  deliberate, documented simplification of an already-shipped, tested flow (see
  `UI_UX_REDESIGN_AUDIT.md` §5).

---

## UI/UX STATUS

- **Registration**: 6-step onboarding, working, persists through `ProfileRepository`, tested via existing onboarding wiring.
- **Dashboard**: decision hierarchy driven by real `DecisionEngine` output via `RiskBadge`; verified, not modified this session.
- **Weather**: LIVE/CACHED/UNAVAILABLE honest, `StatusBadge` centralized; weather provider untouched (stop condition honored).
- **Market**: functionally correct (verified repository wiring); status badge not yet migrated to the shared component.
- **Sensors**: Mock/Live/Simulation states verified working; shared `StatusBadge` not yet migrated in.
- **Voice**: unchanged this session; not re-audited against every sub-requirement.
- **Local LLM**: Settings shows real AI status (`READY`/`LOADING`/`UNAVAILABLE`/`ERROR`), never fabricated.
- **Schemes**: deterministic `GovernmentSchemeMatcher`, confirmed no LLM involvement.
- **Feedback**: persists via existing `FeedbackRepository`.
- **Settings**: reorganized into Profile/Language & Voice/AI/Advanced sections this session.
- **Navigation**: no new destinations broken; new Settings→Profile/Farm Setup entries verified against the actual nav graph structure.
- **Tests**: 125/125 passing (`testDebugUnitTest`), 0 failures.
- **Build**: `assembleDebug` succeeds.
