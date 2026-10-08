@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.krishinirnay.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.krishinirnay.R

/**
 * Bundled (not downloadable-provider) variable fonts — avoids Roboto's
 * inconsistent Devanagari fallback across Android versions/OEM skins, the
 * reason a farmer's Hindi/Marathi UI could look subtly different device to
 * device. Declaring both scripts' [Font] entries in one [FontFamily] gives
 * per-glyph fallback: Latin text resolves against noto_sans, Devanagari
 * text (Hindi/Marathi) resolves against noto_sans_devanagari, each at the
 * requested weight via variation settings (both are variable fonts) — a
 * farmer switching the app language never sees a font change, only script.
 */
private fun weight(value: Int) = FontVariation.Settings(FontVariation.weight(value))

private val KrishiFontFamily = FontFamily(
    Font(R.font.noto_sans, FontWeight.Normal, variationSettings = weight(400)),
    Font(R.font.noto_sans, FontWeight.Medium, variationSettings = weight(500)),
    Font(R.font.noto_sans, FontWeight.SemiBold, variationSettings = weight(600)),
    Font(R.font.noto_sans, FontWeight.Bold, variationSettings = weight(700)),
    Font(R.font.noto_sans_devanagari, FontWeight.Normal, variationSettings = weight(400)),
    Font(R.font.noto_sans_devanagari, FontWeight.Medium, variationSettings = weight(500)),
    Font(R.font.noto_sans_devanagari, FontWeight.SemiBold, variationSettings = weight(600)),
    Font(R.font.noto_sans_devanagari, FontWeight.Bold, variationSettings = weight(700)),
)

// Poppins (Latin + Devanagari) for headings; Noto for body text.
private val KrishiDisplayFamily = FontFamily(
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_semibold, FontWeight.Medium),
)

// Slightly tighter default letter spacing than Material's stock Roboto
// metrics call for — part of what makes default Material3 text read as
// "generic": Noto Sans is a touch wider per-glyph, so 0 extra tracking on
// headings keeps things feeling deliberate rather than loose.
private fun style(weight: FontWeight, size: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, tracking: androidx.compose.ui.unit.TextUnit = 0.sp, family: FontFamily = KrishiFontFamily) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * Full Material3 scale, every slot explicitly set — an untouched slot
 * silently falls back to Material's own Roboto-based default, which is
 * exactly the "generic Material default" look this system exists to avoid.
 * bodyLarge stays the floor for any primary body text (16sp, never
 * smaller) — the large-touch-target / low-literacy accessibility principle
 * this app is built around.
 */
val KrishiTypography = Typography(
    displayLarge = style(FontWeight.Bold, 34.sp, 42.sp, (-0.25).sp, family = KrishiDisplayFamily),
    displayMedium = style(FontWeight.Bold, 30.sp, 38.sp, family = KrishiDisplayFamily),
    displaySmall = style(FontWeight.Bold, 28.sp, 34.sp, family = KrishiDisplayFamily),
    headlineLarge = style(FontWeight.Bold, 28.sp, 36.sp, family = KrishiDisplayFamily),
    headlineMedium = style(FontWeight.Bold, 24.sp, 30.sp, family = KrishiDisplayFamily),
    headlineSmall = style(FontWeight.SemiBold, 21.sp, 28.sp, family = KrishiDisplayFamily),
    titleLarge = style(FontWeight.SemiBold, 20.sp, 26.sp, family = KrishiDisplayFamily),
    titleMedium = style(FontWeight.Medium, 18.sp, 24.sp, family = KrishiDisplayFamily),
    titleSmall = style(FontWeight.Medium, 16.sp, 22.sp, family = KrishiDisplayFamily),
    bodyLarge = style(FontWeight.Normal, 16.sp, 22.sp),
    bodyMedium = style(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = style(FontWeight.Normal, 13.sp, 18.sp),
    labelLarge = style(FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),
    labelMedium = style(FontWeight.Medium, 12.sp, 16.sp, 0.1.sp),
    labelSmall = style(FontWeight.Medium, 12.sp, 16.sp, 0.1.sp),
)
