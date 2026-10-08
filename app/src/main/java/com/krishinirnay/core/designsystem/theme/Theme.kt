package com.krishinirnay.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.motion.LocalMotionEnabled

/**
 * Risk + surface tokens that don't map onto a named Material3
 * [androidx.compose.material3.ColorScheme] slot. Read via
 * [KrishiTheme.colors], the same way Material3 colors are read via
 * `MaterialTheme.colorScheme`.
 */
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
