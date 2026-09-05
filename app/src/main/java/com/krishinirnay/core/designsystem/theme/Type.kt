package com.krishinirnay.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: swap FontFamily.Default for a bundled Noto Sans + Noto Sans
// Devanagari family once the fonts are added via Android Studio's
// Resource Manager > Fonts > Downloadable (search "Noto Sans") — that
// wizard generates the correct Google Play Services font-provider
// certificate resources, which must not be hand-typed/guessed. Noto Sans
// matters specifically because of the English/Hindi toggle: Roboto's
// Devanagari fallback renders inconsistently across Android versions.
private val AppFontFamily = FontFamily.Default

// Sizes/weights per the design system plan. bodyLarge is the floor for
// any primary body text (16sp) — never smaller, given the large-touch
// target / low-literacy accessibility principle. All sizes use sp so
// they respect the system font-scale setting.
val KrishiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
