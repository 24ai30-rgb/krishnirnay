# KRISHINIRNAY — UI/UX Redesign Audit

Audit of the Android app's current UI/UX state before this phase's changes, and the
decisions made about what to redesign vs. what to leave alone.

## 1. Current UI structure

**Screens** (`app/src/main/java/com/krishinirnay/feature/*`): advisory (fertilizer),
alerts, analytics, auth (login), chatbot (voice + text), crophealth (disease),
dashboard, farmsetup, feedback, howitworks, insights, monitoring (live sensors),
offline, **onboarding** (added in the prior session of this phase), pest, profile
(hub + documents), schemes, settings, simulation (mock-mode scenarios), weather,
whatif.

**Navigation**: `navigation/KrishiNavGraph.kt` — one outer `NavHost` (login → onboarding
→ Main → drill-down screens: FarmSetup, Settings, Monitoring, MyDocuments, Simulation,
OfflineMode) wrapping a `MainScaffold` with its own inner `NavHost` for the bottom-nav
tabs (Dashboard, Advisory, Schemes/Insights, Profile — see `BottomNavBar`).

**Design system** (`core/designsystem/`):
- `theme/{Color,Theme,Type}.kt` — Material3 `lightColorScheme` + a `KrishiExtendedColors`
  extension (`KrishiTheme.colors`) already carrying primary/secondary/accent/info tones,
  tinted surfaces (`surfaceAlt`), and risk colors (`riskLow/Medium/High/Unknown`).
  16sp floor on body text. Light-only by deliberate scope decision (documented in
  `Theme.kt`).
- `components/`: `KnCard` (soft shadow + hairline border, the base surface every screen
  already uses instead of plain white), `RiskBadge` (Hero/Compact sizes, animated,
  colorblind-safe icon+text), `StatusBadge` (LIVE/CACHED/MOCK/UNAVAILABLE, centralized
  this phase), `EmptyState`, `LoadingState`, `MetricTile`, `KnTopBar`/`DrillDownTopBar`,
  `KrishiTextField`, `SproutMark`, `SimpleLineChart`, and now `ProfileCompletionCard`.
- `strings/AppStrings.kt` — every user-facing string goes through this (EN/HI/MR),
  selected by `SettingsRepository.language` via `LocalAppStrings`. No screen hardcodes
  user-facing text.

## 2. What was found (this audit)

The app was **not** the plain, white, ad-hoc UI the phase brief assumed. A prior pass
in this same phase already: extended the color system (secondary/accent/info),
centralized the LIVE/CACHED/MOCK/UNAVAILABLE badge into `StatusBadge` (removing two
duplicated inline implementations in Dashboard/Weather), added an `EmptyState` for
Weather-unavailable, and built the entire 6-step Farmer Onboarding flow
(`feature/onboarding/*`) wired through Login → Onboarding → Main.

Concrete gaps found and fixed in this session:

| Gap | Where | Fix |
|---|---|---|
| Settings was one flat list of controls, no grouping | `SettingsScreen.kt` | Reorganized into **Profile / Language & Voice / AI / Advanced** sections per this phase's brief |
| No way to reach Profile/Farm Setup from Settings | `SettingsScreen.kt` | Added nav rows; Farm Setup routes via the existing outer `NavHost` destination, Profile pops back to the already-active Profile tab (Profile is inner-nav-only — see implementation note below) |
| `FarmerProfile.voiceAssistanceEnabled` was write-once (onboarding) with no way to change it afterward | `SettingsViewModel/Screen` | Added a real Voice Assistant switch in Settings, writing through the existing `ProfileRepository` |
| No profile-completion indicator anywhere, despite the model already carrying enough optional fields to compute one | `feature/profile/` | Added `ProfileCompletion.kt` (pure function, unit-tested) + `ProfileCompletionCard` component, wired into `ProfileScreen` |

Gaps found and **deliberately not touched** (see §4):

- Several spec-named components (`WeatherCard`, `MarketCard`, `SensorCard`,
  `RecommendationCard`, `FeedbackCard`, `SchemeCard`, `VoiceButton`, `LanguageSelector`)
  are implemented as inline composables inside their own screens rather than as
  separately named files in `core/designsystem/components/`. Visually and functionally
  they already do what the spec asks (status-labeled cards, deterministic scheme
  matching, persisted feedback, etc.) — see `PHASE_UI_UX_IMPLEMENTATION_REPORT.md` §2
  for the verified per-screen state. Extracting each into a standalone component file
  is a mechanical refactor with no behavior change and real regression risk across
  ~10 screens; not done this pass.
- Dark mode: not implemented. `Theme.kt` already documents this as an intentional
  Phase-1 scope decision, and light theme was explicitly prioritized in this phase's
  brief too.

## 3. What must remain untouched (verified, not modified this session)

- `DecisionEngine`, `FieldDecisionResolver`, `DecisionInput`/`DecisionOutput`,
  `DecisionRules`, `RegionCropRuleRegistry` — no changes.
- `WeatherRepository`/`LiveWeatherRepositoryImpl`/`MockWeatherRepositoryImpl` and the
  Open-Meteo provider on the server — no changes (explicit stop condition for this
  phase).
- `MarketRepository`, `FertilizerAdvisor`-equivalent (`CropAdvisoryViewModel`'s use of
  `DecisionEngine` output), sensor repositories, Mock/Live switching
  (`DefaultFieldStateRepository` + `MockControls`), Local LLM abstraction
  (`LocalLlmRepository`), voice architecture, IVR (`server/app/services/ivr_*`),
  `GovernmentSchemeMatcher`, `FeedbackRepository` — verified via grep that each is
  used as-is by its screen/ViewModel; none were modified.
- All existing API DTOs/contracts (Retrofit services, kotlinx.serialization schemas).

## 4. New design system additions this phase

Colors: `Secondary`, `SecondaryContainer`, `Accent`, `AccentContainer`, `Info`,
`InfoContainer` (added in the prior session of this phase, confirmed still wired into
`KrishiExtendedColors`/`LightExtendedColors`).

Components added/centralized: `StatusBadge`, `KrishiTextField`, `ProfileCompletionCard`
(this session).

## 5. Farmer onboarding flow (already implemented, verified working)

6 steps (Welcome → Personal → Farm → Crop → Preferences → Confirmation) —
`feature/onboarding/{OnboardingUiState,OnboardingViewModel,OnboardingScreen}.kt`.
Writes through the existing `ProfileRepository`/`FarmerProfile` — no second farmer
data store. Gated by `SettingsRepository.hasCompletedOnboarding`; `LoginViewModel`
routes a first-time login to Onboarding, a returning farmer straight to Main.

Note: the phase brief describes a 7-step flow with a separate "Previous crop /
fertilizer usage / pest history" step; the existing 6-step flow folds farming-history
fields (experience years, water source) into the Preferences/Farm steps instead of a
dedicated 7th step. Not restructured this session — reopening a working, tested,
already-shipped onboarding flow's step count for a naming/grouping difference alone
was judged higher-risk than value given the STOP-RULE guidance to make the smallest
safe change.

## 6. Animation strategy

Existing, lightweight, already in place — no new animation framework added:
`animateFloatAsState` (profile-row press scale, now also profile-completion progress),
`AnimatedContent` with fade (onboarding step transitions), `RiskBadge`'s built-in
transition. Nothing heavier was introduced.

## 7. Navigation note (implementation detail worth recording)

Settings is only ever reached from the Profile tab (`ProfileScreen.onNavigateToSettings`
→ `outerNavController.navigate(Settings)`), and Profile itself lives on the *inner*
`NavHost` inside `MainScaffold`, which the outer `NavHost` (where Settings lives) has
no route for. So Settings' new "Farmer Profile" row does `outerNavController
.popBackStack()` (returning to the already-active Profile tab) rather than a direct
route navigation — there is no outer-graph route to navigate to. This mirrors the
existing back-navigation pattern already used by every other outer-graph screen; no
new navigation infrastructure was added.
