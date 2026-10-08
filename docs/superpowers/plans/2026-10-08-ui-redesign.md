# KrishiNirnay UI Redesign (Fresh Field + Expressive Motion) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the whole Android app with the Fresh Field palette, add light, dark and system theme modes, add expressive motion, rebuild every screen's layout, and make the app faster. Business logic is not touched.

**Architecture:** Token values are swapped in place inside the existing `KrishiTheme` / `MaterialTheme` API, so every screen restyles at once. A dark scheme and a persisted `ThemeMode` are added. A new `core/designsystem/motion` package holds the reusable animation primitives, and the new and restyled shared components live in `core/designsystem/components`. Screens are then rebuilt composable by composable on top of these, keeping their ViewModel and UiState contracts unchanged.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2024.09.03, Material 3), Navigation-Compose 2.8.1, Hilt, DataStore, JUnit 4 / MockK / Turbine.

**Spec:** `docs/superpowers/specs/2026-10-08-ui-redesign-design.md`

## Global Constraints

- Colours come only from `MaterialTheme.colorScheme` or `KrishiTheme.colors`. No `Color(0x…)` in `feature/` when the plan is finished.
- Lime (`#C6F36B`) is only ever drawn on green or dark surfaces, never as text or an icon on white.
- Risk colour always comes with an icon and a text label.
- Durations: quick 150 ms, standard 280 ms, emphasis 400 ms. Stagger step 40 ms, capped at index 8.
- Only `graphicsLayer` (alpha, translation, scale) and draw-phase values are animated. No layout-size animation.
- When motion is off (`ANIMATOR_DURATION_SCALE == 0`), every animated element renders in its final state immediately.
- Body text is never smaller than 13 sp, and primary body text stays at 16 sp (`bodyLarge`).
- Every new user-facing string goes into `AppStrings` in all three blocks: EN (~line 468), HI (~858) and MR (~1248).
- No changes to ViewModel public APIs, except the additions named in Tasks 2 and 8.
- **Phase gate:** `gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` all succeed, then commit and `git tag ui-phase-N`.

## Review Focus

1. **Animations switched off on the phone.** Content must appear at full opacity and in its final position, not stay at alpha 0. Pinned by `KrishiMotionTest.motionDisabledWhenScaleZero` (Task 3) and the `LocalMotionEnabled` branches in every primitive.
2. **Unknown or missing data** (risk UNKNOWN, null market, no sensor reading yet). Gauges must show "—" with an empty ring, not a fake "0%". Pinned by `GaugeFormatTest` (Task 4).
3. **Long Hindi and Marathi strings** in the hero card, bottom bar and quick actions. Text must truncate with an ellipsis or wrap, never clip or overlap. Every single-line label gets `maxLines = 1, overflow = TextOverflow.Ellipsis` (Tasks 5 and 6).
4. **Theme mode persistence and corrupt values.** An unknown stored string falls back to SYSTEM. Pinned by `ThemeModeTest` (Task 2).
5. **Long lists scrolled quickly.** Items deeper than the stagger cap must appear instantly, with no 300 ms+ delay on each scroll-in. Pinned by `KrishiMotionTest.staggerCapped` (Task 3).

---

## File Structure

| File | Responsibility |
|---|---|
| `core/designsystem/theme/Color.kt` (modify) | Light + dark raw tokens |
| `core/designsystem/theme/Theme.kt` (modify) | `KrishiExtendedColors` (+hero, lime), light/dark schemes, `KrishiNirnayTheme(darkTheme)` |
| `core/designsystem/theme/Type.kt` (modify) | Poppins display/headline/title, Noto body |
| `core/data/model/ThemeMode.kt` (create) | `enum ThemeMode` + `fromStored()` + `isDark()` |
| `core/data/repository/SettingsRepository.kt`, `core/data/local/AppPreferences.kt` (modify) | persist theme mode |
| `core/designsystem/motion/KrishiMotion.kt` (create) | tokens, pure helpers, `LocalMotionEnabled` |
| `core/designsystem/motion/MotionModifiers.kt` (create) | `enterStagger`, `pressScale`, `pressClickable`, `shimmer`, `pulse` |
| `core/designsystem/components/HeroCard.kt`, `RingGauge.kt`, `AnimatedNumber.kt`, `QuickActionTile.kt`, `SectionHeader.kt` (create) | new shared components |
| `core/designsystem/components/KnCard.kt`, `KnButton.kt`, `LoadingState.kt`, `RiskBadge.kt`, `MetricTile.kt`, `KnTopBar.kt`, `DrillDownTopBar.kt`, `StatusBadge.kt`, `EmptyState.kt`, `SimpleLineChart.kt` (modify) | restyle |
| `navigation/BottomNavBar.kt`, `navigation/KrishiNavGraph.kt` (modify) | floating pill bar, transitions |
| `MainActivity.kt`, `res/values/themes.xml`, `res/values/colors.xml`, `res/values-night/colors.xml` (create) | theme wiring, splash, DayNight |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | splashscreen, profileinstaller, shrinkResources |
| `feature/**/…Screen.kt` | per-screen rebuilds (Tasks 8–13) |

---

# PHASE 1 — Foundation + Dashboard

### Task 1: Fresh Field tokens, light + dark schemes, typography

**Files:**
- Modify: `app/src/main/java/com/krishinirnay/core/designsystem/theme/Color.kt`
- Modify: `app/src/main/java/com/krishinirnay/core/designsystem/theme/Theme.kt`
- Modify: `app/src/main/java/com/krishinirnay/core/designsystem/theme/Type.kt`
- Create: `app/src/main/res/font/poppins_semibold.ttf`, `app/src/main/res/font/poppins_bold.ttf`

**Interfaces:**
- Produces: `KrishiNirnayTheme(darkTheme: Boolean = isSystemInDarkTheme(), motionEnabled: Boolean = true, content)`.
- Produces: `KrishiExtendedColors` gains `heroStart`, `heroEnd`, `lime`, `onLime`. Existing fields keep their names. `accent` stays a warm gold (`#C98A12` light / `#F2B84B` dark), because 4 existing call sites draw it on white, and a lime value there would fail contrast.

- [ ] **Step 1: Replace `Color.kt` contents**

```kotlin
package com.krishinirnay.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// "Fresh Field" tokens — see docs/superpowers/specs/2026-10-08-ui-redesign-design.md.
// Composables read these through MaterialTheme.colorScheme / KrishiTheme.colors only.

// ---- Light ----
val Primary = Color(0xFF0E5A34)
val PrimaryDark = Color(0xFF0A4427)
val PrimaryContainer = Color(0xFFDDF1E3)
val HeroStart = Color(0xFF0E5A34)
val HeroEnd = Color(0xFF1F8A4C)
val Lime = Color(0xFFC6F36B)
val OnLime = Color(0xFF0E3B22)
val Secondary = Color(0xFF3F9B63)
val SecondaryContainer = Color(0xFFE2F2E6)
val Accent = Color(0xFFC98A12)
val AccentContainer = Color(0xFFFAEBCB)
val Info = Color(0xFF2F7BC4)
val InfoContainer = Color(0xFFE1EDF9)
val Background = Color(0xFFF6F8F3)
val Surface = Color(0xFFFFFFFF)
val SurfaceAlt = Color(0xFFEDF2E8)
val TextPrimary = Color(0xFF0F2A1C)
val TextSecondary = Color(0xFF4E6357)
val BorderColor = Color(0xFFDDE5D7)
val RiskLow = Color(0xFF1F9D57)
val RiskLowContainer = Color(0xFFE1F4E8)
val RiskMedium = Color(0xFFD9920B)
val RiskMediumContainer = Color(0xFFFBEED5)
val RiskHigh = Color(0xFFE5484D)
val RiskHighContainer = Color(0xFFFCE4E4)
val RiskUnknown = Color(0xFF8A968E)
val RiskUnknownContainer = Color(0xFFECEFEA)

// ---- Dark ----
val DarkPrimary = Color(0xFF5FD08B)
val DarkOnPrimary = Color(0xFF062213)
val DarkPrimaryContainer = Color(0xFF14402A)
val DarkHeroStart = Color(0xFF0E5A34)
val DarkHeroEnd = Color(0xFF14703F)
val DarkSecondary = Color(0xFF7FD6A0)
val DarkSecondaryContainer = Color(0xFF173A28)
val DarkAccent = Color(0xFFF2B84B)
val DarkAccentContainer = Color(0xFF3A2C10)
val DarkInfo = Color(0xFF6AB0F3)
val DarkInfoContainer = Color(0xFF14283C)
val DarkBackground = Color(0xFF0B1410)
val DarkSurface = Color(0xFF13201A)
val DarkSurfaceAlt = Color(0xFF1A2A22)
val DarkTextPrimary = Color(0xFFE6F0E9)
val DarkTextSecondary = Color(0xFF9FB3A6)
val DarkBorderColor = Color(0xFF24362C)
val DarkRiskLow = Color(0xFF4ADE80)
val DarkRiskLowContainer = Color(0xFF16351F)
val DarkRiskMedium = Color(0xFFFBBF24)
val DarkRiskMediumContainer = Color(0xFF3A2E0E)
val DarkRiskHigh = Color(0xFFFF6B6B)
val DarkRiskHighContainer = Color(0xFF3D1A1A)
val DarkRiskUnknown = Color(0xFF7C8B82)
val DarkRiskUnknownContainer = Color(0xFF1F2924)
```

- [ ] **Step 2: Update `Theme.kt`.** Add fields to `KrishiExtendedColors`, add a dark instance and a dark scheme, and change the theme signature. Keep all existing imports and add `androidx.compose.foundation.isSystemInDarkTheme`, `androidx.compose.material3.darkColorScheme` and `com.krishinirnay.core.designsystem.motion.LocalMotionEnabled`.

```kotlin
data class KrishiExtendedColors(
    val surfaceAlt: Color,
    val riskLow: Color, val riskLowContainer: Color,
    val riskMedium: Color, val riskMediumContainer: Color,
    val riskHigh: Color, val riskHighContainer: Color,
    val riskUnknown: Color, val riskUnknownContainer: Color,
    val secondary: Color, val secondaryContainer: Color,
    val accent: Color, val accentContainer: Color,
    val info: Color, val infoContainer: Color,
    val heroStart: Color, val heroEnd: Color,
    val lime: Color, val onLime: Color,
)

private val LightExtendedColors = KrishiExtendedColors(
    surfaceAlt = SurfaceAlt,
    riskLow = RiskLow, riskLowContainer = RiskLowContainer,
    riskMedium = RiskMedium, riskMediumContainer = RiskMediumContainer,
    riskHigh = RiskHigh, riskHighContainer = RiskHighContainer,
    riskUnknown = RiskUnknown, riskUnknownContainer = RiskUnknownContainer,
    secondary = Secondary, secondaryContainer = SecondaryContainer,
    accent = Accent, accentContainer = AccentContainer,
    info = Info, infoContainer = InfoContainer,
    heroStart = HeroStart, heroEnd = HeroEnd,
    lime = Lime, onLime = OnLime,
)

private val DarkExtendedColors = KrishiExtendedColors(
    surfaceAlt = DarkSurfaceAlt,
    riskLow = DarkRiskLow, riskLowContainer = DarkRiskLowContainer,
    riskMedium = DarkRiskMedium, riskMediumContainer = DarkRiskMediumContainer,
    riskHigh = DarkRiskHigh, riskHighContainer = DarkRiskHighContainer,
    riskUnknown = DarkRiskUnknown, riskUnknownContainer = DarkRiskUnknownContainer,
    secondary = DarkSecondary, secondaryContainer = DarkSecondaryContainer,
    accent = DarkAccent, accentContainer = DarkAccentContainer,
    info = DarkInfo, infoContainer = DarkInfoContainer,
    heroStart = DarkHeroStart, heroEnd = DarkHeroEnd,
    lime = Lime, onLime = OnLime,
)

private val LocalKrishiExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Primary, onPrimary = Color.White,
    primaryContainer = PrimaryContainer, onPrimaryContainer = PrimaryDark,
    secondary = Secondary, onSecondary = Color.White,
    secondaryContainer = SecondaryContainer, onSecondaryContainer = PrimaryDark,
    background = Background, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt, onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface, surfaceContainerHigh = Surface, surfaceContainerLow = Background,
    outline = BorderColor, outlineVariant = BorderColor,
    error = RiskHigh, errorContainer = RiskHighContainer,
    onError = Color.White, onErrorContainer = RiskHigh,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkTextPrimary,
    secondary = DarkSecondary, onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSecondaryContainer, onSecondaryContainer = DarkTextPrimary,
    background = DarkBackground, onBackground = DarkTextPrimary,
    surface = DarkSurface, onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceAlt, onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = DarkSurface, surfaceContainerHigh = DarkSurfaceAlt, surfaceContainerLow = DarkBackground,
    outline = DarkBorderColor, outlineVariant = DarkBorderColor,
    error = DarkRiskHigh, errorContainer = DarkRiskHighContainer,
    onError = DarkOnPrimary, onErrorContainer = DarkRiskHigh,
)

private val KrishiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object KrishiTheme {
    val colors: KrishiExtendedColors
        @Composable
        get() = LocalKrishiExtendedColors.current
}

/** App-wide theme. [darkTheme] comes from the persisted ThemeMode (see MainActivity). */
@Composable
fun KrishiNirnayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKrishiExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
        LocalMotionEnabled provides motionEnabled,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = KrishiTypography,
            shapes = KrishiShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 3: Download Poppins** (SIL OFL, includes Devanagari)

```bash
curl -fL -o app/src/main/res/font/poppins_semibold.ttf https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-SemiBold.ttf
curl -fL -o app/src/main/res/font/poppins_bold.ttf https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Bold.ttf
```

If either download fails, skip Step 4. Headings then keep Noto Sans Bold, and nothing else depends on Poppins.

- [ ] **Step 4: In `Type.kt`, add a display family and use it for the display, headline and title slots.** Do this below `KrishiFontFamily`:

```kotlin
// Poppins (Latin + Devanagari) for headings; Noto for body text.
private val KrishiDisplayFamily = FontFamily(
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_semibold, FontWeight.Medium),
)
```

Add a `family: FontFamily = KrishiFontFamily` parameter to `style(...)` and pass `fontFamily = family`. Pass `KrishiDisplayFamily` for `displayLarge` through `titleSmall`. Also bump `displaySmall` to `28.sp / 34.sp` (hero action text).

- [ ] **Step 5: Create the motion tokens file now.** `Theme.kt` imports `LocalMotionEnabled`, so create `core/designsystem/motion/KrishiMotion.kt` with the exact contents from Task 3, Step 3, before compiling. Task 3 then only adds its test and `MotionModifiers.kt`. Every existing `KrishiNirnayTheme(` call site (`grep -rn "KrishiNirnayTheme(" app/src`) compiles unchanged, because both new parameters have defaults.

- [ ] **Step 6: Compile**

Run: `./gradlew.bat --no-daemon -q :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit** `git commit -am "feat(ui): Fresh Field tokens, dark scheme, Poppins headings"` (also `git add` the font files).

---

### Task 2: Persisted ThemeMode + Settings toggle + MainActivity wiring

**Files:**
- Create: `app/src/main/java/com/krishinirnay/core/data/model/ThemeMode.kt`
- Create: `app/src/test/java/com/krishinirnay/data/ThemeModeTest.kt`
- Modify: `core/data/repository/SettingsRepository.kt`, `core/data/local/AppPreferences.kt`
- Modify: `app/src/test/java/com/krishinirnay/data/DefaultFieldStateRepositoryTest.kt` (`FakeSettingsRepository`)
- Modify: `MainActivity.kt`, `feature/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `core/designsystem/strings/AppStrings.kt`

**Interfaces:**
- Produces: `enum class ThemeMode { SYSTEM, LIGHT, DARK; fun isDark(systemDark: Boolean): Boolean; companion object { fun fromStored(raw: String?): ThemeMode } }`
- Produces: `SettingsRepository.themeMode: StateFlow<ThemeMode>`, `suspend fun setThemeMode(mode: ThemeMode)`
- Produces: `AppStrings.settingsTheme`, `themeSystem`, `themeLight`, `themeDark`

- [ ] **Step 1: Write the failing test** `ThemeModeTest.kt`

```kotlin
package com.krishinirnay.data

import com.krishinirnay.core.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun fromStored_knownValues() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("LIGHT"))
    }

    @Test fun fromStored_nullOrCorrupt_fallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("purple"))
    }

    @Test fun isDark_followsSystemOnlyForSystemMode() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests "com.krishinirnay.data.ThemeModeTest"`
Expected: compilation failure, `Unresolved reference: ThemeMode`.

- [ ] **Step 3: Create `ThemeMode.kt`**

```kotlin
package com.krishinirnay.core.data.model

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Same command. Expected: 3 tests PASS.

- [ ] **Step 5: Extend the repository**

In `SettingsRepository.kt`, add:

```kotlin
    val themeMode: StateFlow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
```

In `AppPreferences.kt`, add `val THEME_MODE = stringPreferencesKey("theme_mode")` to `Keys`, then:

```kotlin
    override val themeMode: StateFlow<ThemeMode> = dataStore.data
        .map { ThemeMode.fromStored(it[Keys.THEME_MODE]) }
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
```

In `FakeSettingsRepository` (test), add:

```kotlin
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
```

- [ ] **Step 6: Wire `MainActivity`**

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            val motionEnabled = rememberSystemMotionEnabled()
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
                onDispose {}
            }
            KrishiNirnayTheme(darkTheme = darkTheme, motionEnabled = motionEnabled) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KrishiNavGraph(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
```

Imports: `androidx.activity.SystemBarStyle`, `androidx.compose.foundation.isSystemInDarkTheme`, `androidx.compose.runtime.DisposableEffect`, `androidx.compose.runtime.getValue`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen` (from Task 7), `com.krishinirnay.core.designsystem.motion.rememberSystemMotionEnabled`, `com.krishinirnay.core.data.repository.SettingsRepository` and `javax.inject.Inject`.

- [ ] **Step 7: Add strings to `AppStrings`.** Declare `var settingsTheme: String = ""`, `var themeSystem`, `var themeLight` and `var themeDark` next to `settingsLanguage`, then assign them in each block:
  - EN: `"Theme"`, `"System"`, `"Light"`, `"Dark"`
  - HI: `"थीम"`, `"सिस्टम"`, `"लाइट"`, `"डार्क"`
  - MR: `"थीम"`, `"सिस्टम"`, `"लाइट"`, `"डार्क"`

- [ ] **Step 8: Add the Settings UI.** Add `val themeMode: ThemeMode = ThemeMode.SYSTEM` to `SettingsUiState`. In `SettingsViewModel`, combine `settingsRepository.themeMode` into the state the same way `language` is mapped, and add:

```kotlin
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
```

In `SettingsScreen`, directly under the language selector, add a `SingleChoiceSegmentedButtonRow` with three `SegmentedButton`s: System, Light and Dark, with `selected = uiState.themeMode == mode` and `onClick = { viewModel.setThemeMode(mode) }`, labelled with the strings from Step 7 and `maxLines = 1`.

- [ ] **Step 9: Run all unit tests**

Run: `./gradlew.bat --no-daemon -q :app:testDebugUnitTest`
Expected: 210 tests, 0 failures.

- [ ] **Step 10: Commit** `git add -A app/src && git commit -m "feat(ui): persisted System/Light/Dark theme mode"`

---

### Task 3: Motion system

**Files:**
- Create: `core/designsystem/motion/KrishiMotion.kt`
- Create: `core/designsystem/motion/MotionModifiers.kt`
- Create: `app/src/test/java/com/krishinirnay/designsystem/KrishiMotionTest.kt`

**Interfaces:**
- Produces: `object KrishiMotion { QUICK, STANDARD, EMPHASIS, STAGGER_STEP_MS, STAGGER_MAX_INDEX; fun staggerDelayMillis(index: Int): Int?; fun motionEnabled(animatorScale: Float): Boolean; fun <T> bouncy(): SpringSpec<T>; fun <T> firm(): SpringSpec<T> }`
- Produces: `val LocalMotionEnabled: ProvidableCompositionLocal<Boolean>`, `@Composable fun rememberSystemMotionEnabled(): Boolean`
- Produces: `Modifier.enterStagger(index: Int)`, `Modifier.pressScale(interactionSource: MutableInteractionSource)`, `Modifier.pressClickable(onClick: () -> Unit, enabled: Boolean = true)`, `Modifier.shimmer()`, `Modifier.pulse(enabled: Boolean)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.krishinirnay.designsystem

import com.krishinirnay.core.designsystem.motion.KrishiMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KrishiMotionTest {
    @Test fun staggerGrowsByStep() {
        assertEquals(0, KrishiMotion.staggerDelayMillis(0))
        assertEquals(120, KrishiMotion.staggerDelayMillis(3))
    }

    @Test fun staggerCapped() {
        // Beyond the cap an item gets no entrance at all (null) — never a long delay.
        assertEquals(320, KrishiMotion.staggerDelayMillis(8))
        assertNull(KrishiMotion.staggerDelayMillis(9))
        assertNull(KrishiMotion.staggerDelayMillis(500))
    }

    @Test fun negativeIndexTreatedAsFirst() {
        assertEquals(0, KrishiMotion.staggerDelayMillis(-1))
    }

    @Test fun motionDisabledWhenScaleZero() {
        assertFalse(KrishiMotion.motionEnabled(0f))
        assertTrue(KrishiMotion.motionEnabled(1f))
        assertTrue(KrishiMotion.motionEnabled(0.5f))
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests "com.krishinirnay.designsystem.KrishiMotionTest"`
Expected: `Unresolved reference: KrishiMotion`.

- [ ] **Step 3: Create `KrishiMotion.kt`**

```kotlin
package com.krishinirnay.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Expressive-motion tokens. Short durations keep the app feeling fast; nothing waits on an animation. */
object KrishiMotion {
    const val QUICK = 150
    const val STANDARD = 280
    const val EMPHASIS = 400
    const val STAGGER_STEP_MS = 40
    const val STAGGER_MAX_INDEX = 8

    /** Entrance delay for the [index]-th item, or null when past the cap (item appears instantly). */
    fun staggerDelayMillis(index: Int): Int? =
        if (index > STAGGER_MAX_INDEX) null else index.coerceAtLeast(0) * STAGGER_STEP_MS

    fun motionEnabled(animatorScale: Float): Boolean = animatorScale > 0f

    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun <T> firm(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

/** False when the user turned animations off system-wide — every primitive then renders its end state. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberSystemMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        KrishiMotion.motionEnabled(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes.** Same command. Expected: 4 tests PASS.

- [ ] **Step 5: Create `MotionModifiers.kt`**

```kotlin
package com.krishinirnay.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import kotlinx.coroutines.delay

/** Fade + rise + slight scale-up entrance, staggered by [index]. Plays once per item (survives scroll). */
fun Modifier.enterStagger(index: Int): Modifier = composed {
    val delayMs = KrishiMotion.staggerDelayMillis(index)
    if (!LocalMotionEnabled.current || delayMs == null) return@composed this
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(delayMs.toLong())
            progress.animateTo(1f, KrishiMotion.bouncy())
            played = true
        }
    }
    val risePx = with(LocalDensity.current) { 24.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * risePx
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/** Shrinks to 96% while [interactionSource] is pressed. Pair with the same source on clickable. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && LocalMotionEnabled.current) 0.96f else 1f
    val scale by animateFloatAsState(target, KrishiMotion.bouncy(), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** clickable + ripple + press-scale in one. */
fun Modifier.pressClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source)
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Skeleton shimmer. Static surfaceAlt fill when motion is off. */
fun Modifier.shimmer(): Modifier = composed {
    val base = KrishiTheme.colors.surfaceAlt
    val highlight = MaterialTheme.colorScheme.surface
    if (!LocalMotionEnabled.current) return@composed drawBehind { drawRect(base) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerX",
    )
    drawBehind {
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(size.width * (x - 1f), 0f),
                end = Offset(size.width * x, size.height),
            ),
        )
    }
}

/** Gentle breathing scale for attention (HIGH risk badge, mic FAB). */
fun Modifier.pulse(enabled: Boolean): Modifier = composed {
    if (!enabled || !LocalMotionEnabled.current) return@composed this
    val transition = rememberInfiniteTransition(label = "pulse")
    val s by transition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}
```

- [ ] **Step 6: Compile and test**

Run: `./gradlew.bat --no-daemon -q :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: success, 214 tests passing.

- [ ] **Step 7: Commit** `git add -A app/src && git commit -m "feat(ui): expressive motion primitives"`

---

### Task 4: New shared components (HeroCard, RingGauge, AnimatedNumber, QuickActionTile, SectionHeader)

**Files:**
- Create: `core/designsystem/components/HeroCard.kt`, `RingGauge.kt`, `AnimatedNumber.kt`, `QuickActionTile.kt`, `SectionHeader.kt`
- Create: `app/src/test/java/com/krishinirnay/designsystem/GaugeFormatTest.kt`

**Interfaces:**
- Produces: `HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`. Content colour is white.
- Produces: `RingGauge(fraction: Float?, valueText: String, label: String, color: Color, modifier: Modifier = Modifier, size: Dp = 64.dp)`
- Produces: `object GaugeFormat { fun percent(value: Float?): String; fun fraction(value: Float?, max: Float): Float? }`
- Produces: `AnimatedNumber(target: Float, format: (Float) -> String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified)`
- Produces: `QuickActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary)`
- Produces: `SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.krishinirnay.designsystem

import com.krishinirnay.core.designsystem.components.GaugeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GaugeFormatTest {
    @Test fun missingValueShowsDash_notZero() {
        assertEquals("—", GaugeFormat.percent(null))
        assertNull(GaugeFormat.fraction(null, 100f))
    }

    @Test fun percentRoundsToWholeNumber() {
        assertEquals("18%", GaugeFormat.percent(18.4f))
    }

    @Test fun fractionClampedToUnitRange() {
        assertEquals(0.5f, GaugeFormat.fraction(25f, 50f)!!, 0.0001f)
        assertEquals(1f, GaugeFormat.fraction(140f, 100f)!!, 0.0001f)
        assertEquals(0f, GaugeFormat.fraction(-3f, 100f)!!, 0.0001f)
    }

    @Test fun nonFiniteTreatedAsMissing() {
        assertEquals("—", GaugeFormat.percent(Float.NaN))
        assertNull(GaugeFormat.fraction(Float.NaN, 100f))
    }
}
```

- [ ] **Step 2: Run it and confirm it fails.** Expected: `Unresolved reference: GaugeFormat`.

- [ ] **Step 3: Create `RingGauge.kt`**

```kotlin
package com.krishinirnay.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import com.krishinirnay.core.designsystem.motion.LocalMotionEnabled
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import kotlin.math.roundToInt

object GaugeFormat {
    fun percent(value: Float?): String =
        if (value == null || !value.isFinite()) "—" else "${value.roundToInt()}%"

    fun fraction(value: Float?, max: Float): Float? =
        if (value == null || !value.isFinite() || max <= 0f) null else (value / max).coerceIn(0f, 1f)
}

/** Circular gauge whose arc sweeps in on first show. [fraction] null = no data: empty track, "—". */
@Composable
fun RingGauge(
    fraction: Float?,
    valueText: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val motion = LocalMotionEnabled.current
    val target = fraction ?: 0f
    val sweep = remember { Animatable(if (motion) 0f else target) }
    LaunchedEffect(target, motion) {
        if (motion) sweep.animateTo(target, tween(KrishiMotion.EMPHASIS * 2, easing = FastOutSlowInEasing))
        else sweep.snapTo(target)
    }
    val track = KrishiTheme.colors.surfaceAlt
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .drawBehind {
                    val stroke = 7.dp.toPx()
                    val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (sweep.value > 0f) {
                        drawArc(color, -90f, 360f * sweep.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(valueText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

- [ ] **Step 4: Create `HeroCard.kt`**

```kotlin
package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.krishinirnay.core.designsystem.theme.KrishiTheme

/** Gradient "primary answer" card at the top of every screen. Content is white; lime accents allowed inside. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = KrishiTheme.colors
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
                .padding(18.dp),
            content = content,
        )
    }
}
```

- [ ] **Step 5: Create `AnimatedNumber.kt`**

```kotlin
package com.krishinirnay.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import com.krishinirnay.core.designsystem.motion.LocalMotionEnabled

/** Counts up to [target]. Snaps instantly when motion is off. */
@Composable
fun AnimatedNumber(
    target: Float,
    format: (Float) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val motion = LocalMotionEnabled.current
    val value = remember { Animatable(if (motion) 0f else target) }
    LaunchedEffect(target, motion) {
        if (motion) value.animateTo(target, tween(KrishiMotion.EMPHASIS * 2, easing = FastOutSlowInEasing))
        else value.snapTo(target)
    }
    Text(format(value.value), style = style, color = color, modifier = modifier)
}
```

- [ ] **Step 6: Create `QuickActionTile.kt` and `SectionHeader.kt`**

```kotlin
package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.motion.pressClickable

@Composable
fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .pressClickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
```

```kotlin
package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, maxLines = 1) }
    }
}
```

- [ ] **Step 7: Run the tests.** `GaugeFormatTest` should pass (4 tests) and the full suite should be green.
- [ ] **Step 8: Commit** `git add -A app/src && git commit -m "feat(ui): HeroCard, RingGauge, AnimatedNumber, QuickActionTile, SectionHeader"`

---

### Task 5: Restyle the existing shared components

**Files:** Modify `KnCard.kt`, `KnButton.kt`, `LoadingState.kt`, `RiskBadge.kt`, `MetricTile.kt`, `StatusBadge.kt`, `EmptyState.kt`, `KnTopBar.kt`, `DrillDownTopBar.kt`, `SimpleLineChart.kt`, all in `core/designsystem/components/`

**Interfaces:**
- Consumes: Task 3 modifiers and Task 1 tokens.
- Produces: the same public signatures as today, plus a new optional parameter `KnCard(onClick: (() -> Unit)? = null)`.

- [ ] **Step 1: KnCard.** Use a 16 dp `MaterialTheme.shapes.medium` with no border in dark mode, replace the heavy shadow with a soft 1 dp elevation, and make the card pressable when `onClick` is set:

```kotlin
@Composable
fun KnCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .shadow(elevation = 1.dp, shape = shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.pressClickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}
```

(Imports: `androidx.compose.ui.graphics.Color`, `com.krishinirnay.core.designsystem.motion.pressClickable`. Remove `border`.)

- [ ] **Step 2: KnButton.**
  - Add `val source = remember { MutableInteractionSource() }` and pass `interactionSource = source` plus `modifier = modifier.pressScale(source)` to all three M3 buttons.
  - Primary: 52 dp tall, fully rounded (`RoundedCornerShape(50)`), `containerColor = primary`, `contentColor = onPrimary` (replaces the hard-coded `Color.White`, which breaks dark mode).
  - Secondary: the same shape with a 1.5 dp primary border.
  - Loading spinner colour: `MaterialTheme.colorScheme.onPrimary` for Primary.

- [ ] **Step 3: LoadingState.** Replace the spinner with skeleton blocks:

```kotlin
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.large).shimmer())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(84.dp).clip(MaterialTheme.shapes.medium).shimmer()) }
        }
        Box(Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.medium).shimmer())
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
```

- [ ] **Step 4: RiskBadge.** Read the file and keep its public API. Make the badge a fully rounded pill (`RoundedCornerShape(50)`), container = the risk container token, and content = the risk colour icon plus label. Append `.pulse(enabled = level == RiskLevel.HIGH)` to the root modifier. Add `maxLines = 1, overflow = TextOverflow.Ellipsis` to the label.

- [ ] **Step 5: MetricTile.** Use a `surface` background (not `surfaceAlt`) with `shapes.medium`. Put the icon in a 36 dp circle tinted `accentColor.copy(alpha = 0.12f)`. Use `titleLarge` for the value.

- [ ] **Step 6: StatusBadge and EmptyState.**
  - StatusBadge: pill shape. Replace any hard-coded colour with the matching token (LIVE → riskLow, CACHED → accent, MOCK → info, UNAVAILABLE → riskUnknown).
  - EmptyState: put the icon in a 72 dp `primaryContainer` circle and add `Modifier.enterStagger(0)` to the root.

- [ ] **Step 7: Top bars.**
  - KnTopBar: transparent background (`MaterialTheme.colorScheme.background`), title in `headlineSmall`, and action icons in 40 dp circular `surface` buttons.
  - DrillDownTopBar: same treatment, with the back button in a 40 dp circular `surface` button.
  - Replace any `Color.White` / `Color(0x…)` with tokens.

- [ ] **Step 8: SimpleLineChart.** Draw the line in `primary`, with an area fill underneath using `Brush.verticalGradient(listOf(primary.copy(alpha = .25f), Color.Transparent))`. Animate the reveal by clipping the drawn path width to `progress.value * size.width` (Animatable 0 → 1, `tween(EMPHASIS * 2)`, snapping when motion is off).

- [ ] **Step 9: Remove hard-coded colours in components.** Run `grep -rn "Color(0x\|Color.White" app/src/main/java/com/krishinirnay/core/designsystem/components`. Each remaining hit must be on a hero or primary surface, where `Color.White` is the correct content colour. Swap anything else for a token.

- [ ] **Step 10: Compile, test, commit**

Run: `./gradlew.bat --no-daemon -q :app:compileDebugKotlin :app:testDebugUnitTest`
Commit: `git commit -am "feat(ui): restyle shared components with Fresh Field + motion"`

---

### Task 6: Floating pill bottom bar, mic FAB, navigation transitions

**Files:** Modify `navigation/BottomNavBar.kt` and `navigation/KrishiNavGraph.kt`

**Interfaces:**
- Consumes: `KrishiMotion.firm()`, `pressClickable`, and `KrishiTheme.colors.lime` / `onLime`.
- Produces: the `BottomNavBar(currentRoute, onNavigate)` signature, unchanged.

- [ ] **Step 1: Rewrite the `BottomNavBar` body** (keep `iconFor` / `labelFor`):

```kotlin
@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (Destination) -> Unit) {
    val strings = LocalAppStrings.current
    val c = KrishiTheme.colors
    val barColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF0F2A1C) else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(barColor)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomTabDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val bg by animateColorAsState(if (selected) c.lime else Color.Transparent, tween(KrishiMotion.STANDARD), label = "tabBg")
            val fg by animateColorAsState(if (selected) c.onLime else Color(0xFF9FB3A6), tween(KrishiMotion.STANDARD), label = "tabFg")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .pressClickable { onNavigate(destination) }
                    .animateContentSize(KrishiMotion.firm())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(iconFor(destination), contentDescription = labelFor(destination, strings), tint = fg, modifier = Modifier.size(22.dp))
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text(labelFor(destination, strings), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
                }
            }
        }
    }
}
```

`Color(0xFF0F2A1C)` and `Color(0xFF9FB3A6)` are the bar's own fixed dark-ink colours in both modes. Move them into `Color.kt` as `NavBarInk` and `NavBarMuted` instead of leaving literals. Imports: `animateColorAsState`, `animateContentSize`, `tween`, `luminance`, `navigationBarsPadding`, `widthIn`, `shadow`, plus the motion and theme packages.

- [ ] **Step 2: Make the `MainScaffold` content draw under the floating bar.** In `KrishiNavGraph.kt` `MainScaffold`, set `Scaffold(containerColor = MaterialTheme.colorScheme.background, …)`. The inner padding stays as it is, which keeps content above the bar.

- [ ] **Step 3: Outer NavHost transitions** (replace the four lambdas):

```kotlin
        enterTransition = {
            slideInHorizontally(KrishiMotion.firm()) { it * 3 / 10 } + fadeIn(tween(KrishiMotion.STANDARD))
        },
        exitTransition = {
            slideOutHorizontally(KrishiMotion.firm()) { -it / 10 } + fadeOut(tween(KrishiMotion.QUICK), targetAlpha = 0.6f)
        },
        popEnterTransition = {
            slideInHorizontally(KrishiMotion.firm()) { -it / 10 } + fadeIn(tween(KrishiMotion.STANDARD), initialAlpha = 0.6f)
        },
        popExitTransition = {
            slideOutHorizontally(KrishiMotion.firm()) { it * 3 / 10 } + fadeOut(tween(KrishiMotion.QUICK))
        },
```

(`slideInHorizontally(animationSpec: FiniteAnimationSpec<IntOffset>, initialOffsetX)` takes the spring typed as `SpringSpec<IntOffset>`, and `KrishiMotion.firm<IntOffset>()` infers the type.)

- [ ] **Step 4: Inner tab transitions**

```kotlin
            enterTransition = { fadeIn(tween(KrishiMotion.STANDARD)) + scaleIn(tween(KrishiMotion.STANDARD), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(KrishiMotion.QUICK)) },
```

- [ ] **Step 5: Compile and commit.** `git commit -am "feat(ui): floating pill bottom bar and expressive nav transitions"`

---

### Task 7: Speed — release build, profileinstaller, splash screen, DayNight window

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`, `res/values/themes.xml`, `res/values/colors.xml`, `AndroidManifest.xml`. Create `res/values-night/colors.xml`.

- [ ] **Step 1: Add the libraries** to `libs.versions.toml`:

```toml
# [versions]
splashscreen = "1.0.1"
profileinstaller = "1.3.1"
# [libraries]
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }
androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "profileinstaller" }
```

In `app/build.gradle.kts` dependencies, add `implementation(libs.androidx.core.splashscreen)` and `implementation(libs.androidx.profileinstaller)`. In `buildTypes.release`, add `isShrinkResources = true`, plus `signingConfig = signingConfigs.getByName("debug")` so `assembleRelease` produces an installable demo APK.

- [ ] **Step 2: Window theme.**

`res/values/colors.xml`:

```xml
<resources>
    <color name="window_background">#F6F8F3</color>
    <color name="launcher_background">#DDF1E3</color>
    <color name="splash_background">#0E5A34</color>
</resources>
```

`res/values-night/colors.xml`:

```xml
<resources>
    <color name="window_background">#0B1410</color>
    <color name="splash_background">#0B1410</color>
</resources>
```

`res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.KrishiNirnay" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
    </style>

    <style name="Theme.KrishiNirnay.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.KrishiNirnay</item>
    </style>
</resources>
```

In `AndroidManifest.xml`, change the `<activity …MainActivity>` theme to `@style/Theme.KrishiNirnay.Splash`. The application theme stays as it is.

- [ ] **Step 3: List performance.** Run `grep -rn "items(" app/src/main/java/com/krishinirnay/feature`. For every `items(list)` / `itemsIndexed(list)` without a key, add `key = { it.<stable id or text> }` and `contentType = { "<kind>" }`. Use an id field if the model has one, otherwise the item's display text plus its index.

- [ ] **Step 4: Verify the release build**

Run: `./gradlew.bat --no-daemon -q :app:assembleRelease`
Expected: BUILD SUCCESSFUL, with the APK at `app/build/outputs/apk/release/app-release.apk`. If R8 reports missing classes for Retrofit or kotlinx-serialization DTOs, add the keep rules R8 prints to `app/proguard-rules.pro` and re-run.

- [ ] **Step 5: Commit** `git commit -am "perf: release shrinking, profileinstaller, splash screen, DayNight window"`

---

### Task 8: Dashboard rebuild (Phase 1 showcase)

**Files:** Modify `feature/dashboard/DashboardScreen.kt`, `feature/dashboard/DashboardViewModel.kt` and `core/designsystem/strings/AppStrings.kt`

**Interfaces:**
- Consumes: HeroCard, RingGauge, GaugeFormat, QuickActionTile, SectionHeader, KnCard, enterStagger, pulse, and `FeedbackViewModel.submit(FeedbackAction, FeedbackResult?)`.
- Produces: `DashboardViewModel.speakDecision(text: String)`, which takes a new constructor parameter `textToSpeechManager: TextToSpeechManager` and `settingsRepository: SettingsRepository`.
- Produces: `AppStrings.dashboardListen`, `dashboardMarkDone`, `dashboardQuickActions`

- [ ] **Step 1: Add strings** to all three blocks:
  - EN: `"Listen"`, `"Mark done"`, `"Quick actions"`
  - HI: `"सुनें"`, `"हो गया"`, `"त्वरित कार्य"`
  - MR: `"ऐका"`, `"झाले"`, `"जलद कृती"`

- [ ] **Step 2: Add `speakDecision` to `DashboardViewModel`**

```kotlin
    fun speakDecision(text: String) {
        val tag = when (settingsRepository.language.value) { "hi" -> "hi-IN"; "mr" -> "mr-IN"; else -> "en-IN" }
        textToSpeechManager.speak(text, languageTag = tag)
    }

    override fun onCleared() { textToSpeechManager.stop(); super.onCleared() }
```

Add the two constructor parameters (Hilt provides both: `TextToSpeechManager` is `@Inject`, and `SettingsRepository` is bound in `RepositoryModule`).

- [ ] **Step 3: Replace the body of `DashboardContent`** with a `LazyColumn` (`contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)`, `verticalArrangement = spacedBy(14.dp)`). Each item applies `Modifier.enterStagger(i)`, where `i` is the item's position:
  0. `HomeGreetingHeader(uiState, strings)`. Keep the existing composable and restyle its weather chip as a `surface` pill.
  1. **Decision hero**, a new private `DecisionHero(uiState, strings, onListen, onMarkDone, onOpenInsights)`:
     - `HeroCard` containing:
       - a small uppercase label with the farm name and crop (from `uiState.profile`),
       - the `strings.textFor(uiState.recommendation)` text in `displaySmall`, bold,
       - a lime pill showing the risk (`c.lime` / `c.onLime`, icon `▲`/`●` + `strings.riskLabel(uiState.overallRisk)`, with `.pulse(uiState.overallRisk == RiskLevel.HIGH)`),
       - up to 3 `uiState.reasons` as `• text` in `bodyMedium` at 92% alpha,
       - a row of two pill buttons: **Mark done** (lime background, which calls `FeedbackViewModel.submit(FeedbackAction.FOLLOWED, null)`) and **Listen** (white at 15% alpha, which calls `viewModel.speakDecision(heroSpokenText)`).
     - Make the whole card open Insights through `pressClickable(onClick = onOpenInsights)`.
     - The recommendation text, reasons and risk label come from the existing `FarmTodayCard`. Move them over, then delete `FarmTodayCard`.
  2. **Gauges row**: three `RingGauge`s with `Modifier.weight(1f)` inside a `KnCard`. When `uiState.isDeviceOnline` is false and the status is UNAVAILABLE, pass null fractions:
     - Soil: fraction `GaugeFormat.fraction(soil, 100f)`, text `GaugeFormat.percent(soil)`, colour `riskColor(uiState.waterStressRisk)`
     - Temp: fraction `GaugeFormat.fraction(temp, 50f)`, text `"${temp.roundToInt()}°"`, colour `riskColor(uiState.heatRisk)`
     - Humidity: fraction `GaugeFormat.fraction(hum, 100f)`, text `GaugeFormat.percent(hum)`, colour `MaterialTheme.colorScheme.primary`
     - Tapping the card opens Monitoring.
  3. `SectionHeader(strings.dashboardQuickActions)`
  4. **Quick actions**: a `Row` of 4 `QuickActionTile`s with `weight(1f)` for Scan leaf (CropHealth), Pests, Weather and Schemes, reusing the existing labels and icons from the current quick-action / ScanChip code. Then delete `ScanChip` and the old `QuickActionTile` private function (the new shared component replaces it).
  5. **Sub-risks**: keep `SubRiskCard` in a 2×2 grid, restyled with `KnCard` and the risk container background.
  6. `WeatherDetailCard` (existing, inside `KnCard`)
  7. `MarketDetailCard` (existing). Replace the `!!` price line with the already-guarded `market.currentPricePerQuintal?.let { … }`.
  8. Fertilizer and `FeedbackWidget` (existing).
- [ ] **Step 4: Replace the dashboard FAB** with a mic-styled FAB: `containerColor = MaterialTheme.colorScheme.primary`, icon `Icons.Rounded.Mic`, `tint = MaterialTheme.colorScheme.onPrimary`, `shape = RoundedCornerShape(18.dp)`, `modifier = Modifier.pulse(enabled = false)`. Pulse stays off, so it doesn't distract; only HIGH risk pulses. The FAB still navigates to the Chatbot.
- [ ] **Step 5: Remove hard-coded colours.** Run `grep -n "Color(0x" feature/dashboard/DashboardScreen.kt`. Map every hit to a token (`KrishiTheme.colors.*` or `colorScheme.*`). Expected: 0 hits.
- [ ] **Step 6: Run the phase gate**

Run: `./gradlew.bat --no-daemon -q :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`
Expected: all succeed.

- [ ] **Step 7: Commit and tag.** `git commit -am "feat(ui): Fresh Field dashboard with decision hero, ring gauges, quick actions"` then `git tag ui-phase-1`
- [ ] **Step 8: Have the user check it on a device** (install `app-release.apk`): light, dark, the theme toggle, Hindi, and animations off.

---

# PHASE 2 — Core screens

**Screen recipe** (applies to Tasks 9–10; every step of the recipe is mandatory for each screen):
1. Root: `Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { DrillDownTopBar(...) })`. The existing top bar call stays, restyled by Task 5.
2. Body: a `LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp))`. Every top-level item gets `Modifier.enterStagger(position)`.
3. Item 0 is a `HeroCard` holding the screen's primary answer (defined per screen below).
4. The remaining existing sections are wrapped in `KnCard`, with a `SectionHeader` above each group.
5. Loading uses `LoadingState()` (skeleton), errors and empty states use `EmptyState`, and data-source status uses `StatusBadge`.
6. `grep -n "Color(0x" <file>` must return 0 hits. Map each hit to the nearest token.
7. Every single-line label gets `maxLines = 1, overflow = TextOverflow.Ellipsis`.

### Task 9: Monitoring, Crop Health, Pest

**Files:** `feature/monitoring/LiveMonitoringScreen.kt`, `feature/crophealth/CropHealthScreen.kt`, `feature/pest/PestDetectionScreen.kt`

- [ ] **Step 1: Monitoring.**
  - Hero: "Live" or "Offline" status (with a `pulse(true)` dot when live), the last-sync time, and three `RingGauge`s (soil, temperature, humidity) using the same mapping as the Dashboard (Task 8 step 3.2).
  - Below the hero: the existing `MetricTile` grid and the `SimpleLineChart` history inside a `KnCard`.
- [ ] **Step 2: Crop Health.**
  - Hero: the result when present. Show the crop and disease name in `headlineSmall`, the confidence as `AnimatedNumber(target = confidence, format = { "${it.roundToInt()}%" }, style = displaySmall)`, and the status pill (lime for HEALTHY; `riskHighContainer` with `riskHigh` for DISEASE DETECTED).
  - When there's no result, the hero instead holds the camera and gallery buttons as `KnButton` Primary / Secondary.
  - The top-3 predictions become a `KnCard` with horizontal bars (`LinearProgressIndicator`, `progress = { conf / 100f }`, colour `primary`, track `surfaceAlt`).
- [ ] **Step 3: Pest.**
  - Hero: the detected count as an `AnimatedNumber` and the top pest name, or "No pests detected" with a lime check.
  - The image with bounding boxes goes in a `KnCard` with `shapes.large` clipping. Box stroke colour is `KrishiTheme.colors.riskHigh`. Keep the existing `String.format` and pass `Locale.US` to it.
- [ ] **Step 4: Compile and test.** `./gradlew.bat --no-daemon -q :app:compileDebugKotlin :app:testDebugUnitTest`, then commit with `feat(ui): monitoring, crop health, pest redesign`.

### Task 10: Weather, Market + phase gate

**Files:** `feature/weather/WeatherScreen.kt`, `feature/market/MarketScreen.kt`

- [ ] **Step 1: Weather.**
  - Hero: the current temperature as an `AnimatedNumber` in `displayLarge`, the condition label, location, and the rain outlook line.
  - Forecast: a horizontal `LazyRow` of day pills (`KnCard`, 72 dp wide, `key = { it.date }`, `enterStagger(index)`).
  - The existing detail rows go into a `KnCard`.
- [ ] **Step 2: Market.**
  - Hero: the crop name, today's modal price as an `AnimatedNumber` with the `₹…/q` format, the trend chip (riskLow for up, riskHigh for down, riskUnknown otherwise, each with an arrow icon and text), and the mandi name.
  - The list of mandis: `items(markets, key = { it.market + it.district })`, each a `KnCard` row.
- [ ] **Step 3: Phase gate.** Run `./gradlew.bat --no-daemon -q :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`, then commit with `feat(ui): weather and market redesign`, then `git tag ui-phase-2`.

---

# PHASE 3 — Remaining screens

Apply the same **Screen recipe** as Phase 2.

### Task 11: Advisory, Alerts, Analytics, Insights, What-if

**Files:** `feature/advisory/CropAdvisoryScreen.kt`, `feature/alerts/AlertsScreen.kt`, `feature/analytics/AnalyticsScreen.kt`, `feature/insights/AiInsightsScreen.kt`, `feature/whatif/*Screen.kt`

- [ ] **Step 1: Advisory.** Hero: the crop and stage, plus the single most important advice line. The advice list items each go in a `KnCard` with a leading 36 dp icon circle.
- [ ] **Step 2: Alerts.** Hero: the unread or HIGH alert count as an `AnimatedNumber`. Each alert row is a `KnCard` with a 4 dp leading stripe in its risk colour, a `RiskBadge`, and `key = { it.id }`.
- [ ] **Step 3: Analytics.** Hero: the 7-day average soil moisture as an `AnimatedNumber`, with each chart in its own `KnCard` using the restyled `SimpleLineChart`.
- [ ] **Step 4: Insights.** Hero: the overall risk, the explanation's first sentence, and the provider chip (Local / On-device / Cloud). The full LLM explanation goes in a `KnCard`, revealed with `AnimatedVisibility(fadeIn + expandVertically)`.
- [ ] **Step 5: What-if.** Hero: the projected outcome for the selected delay. The slider uses `SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary, inactiveTrackColor = surfaceAlt)`, and the result value is an `AnimatedNumber`.
- [ ] **Step 6: Compile, test and commit** with `feat(ui): advisory, alerts, analytics, insights, what-if redesign`.

### Task 12: Schemes, Chatbot, Profile, Settings, Farm setup, Offline, Simulation, My Documents, How it works

**Files:** the matching `feature/*/…Screen.kt` files. Run `ls app/src/main/java/com/krishinirnay/feature/{schemes,chatbot,profile,settings,farmsetup,offline,simulation,howitworks}` and `grep -rln "MyDocuments" app/src/main/java/com/krishinirnay/feature`.

- [ ] **Step 1: Schemes.** Hero: the number of schemes you're eligible for. Each scheme is a `KnCard` with an eligibility `StatusBadge`, `key = { it.id }`.
- [ ] **Step 2: Chatbot.**
  - User bubbles: `primary` background, `onPrimary` text, shape `RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)`.
  - Bot bubbles: `surface` background, shape `RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)`.
  - New messages enter with `Modifier.animateItem()` (Compose 1.7) inside the `LazyColumn`, with `key = { it.id }`.
  - The input bar is a fully rounded `surface` pill. The mic button uses `.pulse(enabled = isListening)`.
- [ ] **Step 3: Profile.** Hero: an avatar initial in a lime circle, the name, the village and district, and the `ProfileCompletionCard` percent as a `RingGauge`. Detail rows go in `KnCard`s.
- [ ] **Step 4: Settings.** Group the rows into `KnCard`s under `SectionHeader`s: Appearance (theme toggle from Task 2, language), Data (mode, sync), AI (cloud fallback), and About. No hero.
- [ ] **Step 5: Farm setup, Offline, Simulation, My Documents, How it works.**
  - Apply recipe steps 1, 2 and 4–7.
  - Hero only on Offline (cached-data age) and Simulation (the active scenario name).
  - Simulation scenario buttons become `QuickActionTile`s in a 3-column grid.
- [ ] **Step 6: Compile, test and commit** with `feat(ui): schemes, chatbot, profile, settings and utility screens redesign`.

### Task 13: Welcome, Login, Register, Onboarding + final gate

**Files:** `feature/auth/welcome/WelcomeScreen.kt`, `feature/auth/login/LoginScreen.kt`, `feature/auth/register/RegisterScreen.kt`, `feature/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Welcome.**
  - A full-bleed hero gradient (`heroStart` → `heroEnd`) covering the top 55%, with `SproutMark` at 96 dp using `.pulse(true)`, the app name in `displayMedium` white, and the tagline.
  - The bottom sheet area is `background` coloured, with Primary "Get started" and Secondary "Log in" buttons. Each of the 4 elements uses `enterStagger(0..3)`.
- [ ] **Step 2: Login and Register.** A compact hero header (120 dp gradient band with the title in white) and a form in a `KnCard`. `KrishiTextField` uses a `shapes.medium` outline that switches to the `primary` colour when focused. The error text uses `riskHigh`.
- [ ] **Step 3: Onboarding.** Pager pages enter with `enterStagger`. The progress indicator is a row of dots with the active dot widened through `animateDpAsState(if (active) 24.dp else 8.dp)` in lime on primary. The CTA is a Primary `KnButton`.
- [ ] **Step 4: Global hard-coded colour check.** Run `grep -rn "Color(0x" app/src/main/java/com/krishinirnay/feature`. Expected: 0 hits.
- [ ] **Step 5: Final gate.** Run `./gradlew.bat --no-daemon -q :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`, commit with `feat(ui): auth and onboarding redesign`, then `git tag ui-phase-3`, then `git push origin master --tags`. Pushing needs the user's confirmation at the time.
