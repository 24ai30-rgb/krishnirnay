package com.krishinirnay.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Risk + surface tokens that don't map onto a named Material3
 * [androidx.compose.material3.ColorScheme] slot. Read via
 * [KrishiTheme.colors], the same way Material3 colors are read via
 * `MaterialTheme.colorScheme`.
 */
data class KrishiExtendedColors(
    val surfaceAlt: Color,
    val riskLow: Color,
    val riskLowContainer: Color,
    val riskMedium: Color,
    val riskMediumContainer: Color,
    val riskHigh: Color,
    val riskHighContainer: Color,
    val riskUnknown: Color,
    val riskUnknownContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val accent: Color,
    val accentContainer: Color,
    val info: Color,
    val infoContainer: Color,
)

private val LightExtendedColors = KrishiExtendedColors(
    surfaceAlt = SurfaceAlt,
    riskLow = RiskLow,
    riskLowContainer = RiskLowContainer,
    riskMedium = RiskMedium,
    riskMediumContainer = RiskMediumContainer,
    riskHigh = RiskHigh,
    riskHighContainer = RiskHighContainer,
    riskUnknown = RiskUnknown,
    riskUnknownContainer = RiskUnknownContainer,
    secondary = Secondary,
    secondaryContainer = SecondaryContainer,
    accent = Accent,
    accentContainer = AccentContainer,
    info = Info,
    infoContainer = InfoContainer,
)

private val LocalKrishiExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = PrimaryDark,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = RiskHigh,
    errorContainer = RiskHighContainer,
    onError = Color.White,
    onErrorContainer = RiskHigh,
)

// 4dp base grid; 16dp cards/sheets, 12dp buttons/chips, 24dp fully
// rounded (FAB, risk badges — those use RoundedCornerShape(50) directly
// where needed rather than through this Shapes object).
private val KrishiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object KrishiTheme {
    val colors: KrishiExtendedColors
        @Composable
        get() = LocalKrishiExtendedColors.current
}

/**
 * App-wide theme. Light-only for Phase 1 — see the design system plan's
 * "Dark mode" note; this is a deliberate scope decision, not an
 * oversight. Tokens are named semantically so a dark [KrishiExtendedColors]
 * + [androidx.compose.material3.darkColorScheme] can be added later as a
 * token swap rather than a rewrite.
 */
@Composable
fun KrishiNirnayTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKrishiExtendedColors provides LightExtendedColors) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = KrishiTypography,
            shapes = KrishiShapes,
            content = content,
        )
    }
}
