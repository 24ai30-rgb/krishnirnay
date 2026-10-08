# KrishiNirnay UI/UX Redesign — "Fresh Field" + Expressive Motion

Date: 2026-10-08 · Status: approved in brainstorming, all 3 phases to ship today (event 9 Oct)

## Goal

Replace the app's whole visual design: palette, typography, shapes, motion and screen layouts. Add light and dark mode, and make the app feel faster. Business logic is untouched.

**Success criteria**
- Every screen uses the Fresh Field tokens. No hard-coded `Color(0x…)` remains in `feature/`.
- Light, dark and system themes are selectable in Settings and persisted.
- Expressive motion is present on every screen, and nothing waits on an animation.
- A release build with R8, resource shrinking and `profileinstaller` is used for the demo.
- After every phase: the build compiles, the 207 unit tests pass, lint shows 0 errors, and the phase is committed and tagged `ui-phase-N`.

**Out of scope:** ViewModels, repositories, the Decision Engine, navigation routes, server, and string content.

## 1. Tokens

Approach: change the existing token values in place. The `KrishiTheme.colors` and `MaterialTheme` APIs stay, and a dark scheme is added.

| Token | Light | Dark |
|---|---|---|
| primary | #0E5A34 | #5FD08B |
| onPrimary | #FFFFFF | #062213 |
| primaryContainer | #DDF1E3 | #14402A |
| heroGradient | #0E5A34 → #1F8A4C | #0E5A34 → #14703F |
| accent (lime) | #C6F36B (only on green or dark surfaces) | #C6F36B |
| onAccent | #0E3B22 | #0E3B22 |
| background | #F6F8F3 | #0B1410 |
| surface | #FFFFFF | #13201A |
| surfaceAlt | #EDF2E8 | #1A2A22 |
| text / onSurface | #0F2A1C | #E6F0E9 |
| textSecondary | #4E6357 | #9FB3A6 |
| outline | #DDE5D7 | #24362C |
| riskLow | #1F9D57 | #4ADE80 |
| riskMedium | #D9920B | #FBBF24 |
| riskHigh | #E5484D | #FF6B6B |
| riskUnknown | #8A968E | #7C8B82 |
| info | #2F7BC4 | #6AB0F3 |

Risk containers are the risk colour at about 12% opacity over the surface.

- **Typography:** Poppins (SemiBold and Bold) for display, headline and title styles. Poppins includes Devanagari. Noto Sans and Noto Sans Devanagari stay for body and label styles.
- **Shapes:** 12 / 16 / 22 / 28 dp. Hero cards use 22 dp, and the bottom bar and chips are fully rounded.

**Theme mode:** a new `ThemeMode { SYSTEM, LIGHT, DARK }` setting, stored in `AppPreferences` under the key `theme_mode` and exposed as `SettingsRepository.themeMode` and `setThemeMode`. `MainActivity` collects it and passes `darkTheme` to `KrishiNirnayTheme`. Settings gets a 3-way segmented control. Status-bar icons follow the theme.

## 2. Motion (Expressive)

`core/designsystem/motion/KrishiMotion.kt`:
- **Durations:** quick 150 ms, standard 280 ms, emphasis 400 ms.
- **Springs:** bouncy (dampingRatio 0.6, stiffness Medium) for cards and badges, and firm (0.85, MediumLow) for navigation.
- **Components:**
  - `Modifier.enterStagger(index)`: fade plus a 24 dp rise. 40 ms stagger capped at index 8. Runs only on first composition.
  - `AnimatedNumber`
  - `RingGauge`: Canvas arc animated from 0 to its value.
  - `Modifier.pressScale()`: scales to 0.96 while pressed.
  - `Modifier.shimmer()`
  - `Modifier.pulse(enabled)`
- **Navigation:**
  - Outer NavHost: the new screen slides in horizontally and fades (enter from +30%, and the old screen exits to −10%). Back navigation mirrors this.
  - Inner tabs: fade through with a 0.96 → 1 scale.
- **Reduced motion:** if `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, all motion is skipped and content renders at its final state immediately.
- **Performance:** only `graphicsLayer` alpha, translation and scale are animated. No layout-affecting animations.

## 3. Shared components (restyled)

- **Restyled:** KnCard, KnButton (primary, secondary, ghost, all with pressScale), KnTopBar / DrillDownTopBar (large title that collapses), RiskBadge (pill with icon, pulse when HIGH), StatusBadge, MetricTile, EmptyState, LoadingState (shimmer skeleton), KrishiTextField, ProfileCompletionCard, SimpleLineChart (gradient fill, animated draw-in).
- **New:** `HeroCard` (gradient container), `RingGauge`, `QuickActionTile`, `SectionHeader`, and `KnBottomBar` (floating pill bar whose selected-tab highlight slides between tabs).

## 4. Screens

**Shared pattern:** top bar → a hero card with the screen's primary answer → staggered section cards → data-source status chips → the shared loading, empty and error states.

- **Phase 1:** tokens, dark mode and theme toggle, motion, all shared components, Dashboard (greeting and weather chip, decision hero with Mark done / Listen, 3 ring gauges, quick actions, mandi card), bottom bar and mic FAB, nav transitions, splash screen, speed fixes.
- **Phase 2:** Monitoring, Crop Health, Pest, Weather, Market.
- **Phase 3:** Advisory, Alerts, Analytics, Schemes, Chatbot, Insights, What-if, Profile, Settings, Farm setup, Offline, Simulation, My Documents, How it works, Welcome, Login, Register, Onboarding.

Screens keep their existing ViewModel / UiState contracts. Only composables change.

## 5. Performance

- **Release build:** `isMinifyEnabled` and `isShrinkResources` for release. Add the `androidx.profileinstaller` dependency, so the baseline profiles that ship with Compose get installed on sideloaded APKs.
- **Splash screen:** `androidx.core:core-splashscreen` with the brand icon on a primary background.
- **Lists:** `key` and `contentType` on every `items()` call. Number and date formatting moves out of composition into `remember` or the UiState.
- **Debug:** demo with `assembleRelease`, signed with the debug key for the event.

## 6. Verification and safety

After each phase:
1. `gradlew :app:testDebugUnitTest :app:lintDebug` passes.
2. `assembleRelease` succeeds.
3. Commit and tag `ui-phase-N`.
4. The user checks the result on a device (or I take screenshots over adb if a device is connected).

If a phase can't be finished cleanly, the previous tag remains the demo build.
