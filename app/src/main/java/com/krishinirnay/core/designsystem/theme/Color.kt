package com.krishinirnay.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Design tokens — see the project's UI/UX design system plan ("Modern
// minimal" direction) for the rationale behind each value. Every
// Composable should read colors through KrishiTheme.colors /
// MaterialTheme.colorScheme, never these constants directly.

val Primary = Color(0xFF1E7D44)
val PrimaryDark = Color(0xFF145C32)
val PrimaryContainer = Color(0xFFE3F5E9)

// Secondary — a lighter, "fresh leaf" green, for accents that need to read
// as "agricultural" but distinct from the primary brand green (e.g. subtle
// section backgrounds, secondary CTAs). Never used for risk meaning.
val Secondary = Color(0xFF5FA777)
val SecondaryContainer = Color(0xFFDCEFE2)

// Accent — warm harvest/golden tone, for highlight moments only (onboarding
// progress, a featured recommendation, market "good price" highlight) —
// never for risk/status meaning, which stays on the Risk* tokens below.
val Accent = Color(0xFFD79A2C)
val AccentContainer = Color(0xFFFBEAD0)

// Info — calm blue, for neutral informational banners (e.g. "cached data"
// notices) that are not warnings and not errors.
val Info = Color(0xFF3B7EC2)
val InfoContainer = Color(0xFFE1EDF9)

val Background = Color(0xFFFAFAF7)
val Surface = Color(0xFFFFFFFF)
val SurfaceAlt = Color(0xFFF1F3EF)

val TextPrimary = Color(0xFF1A1F1C)
val TextSecondary = Color(0xFF5B665F)
val BorderColor = Color(0xFFE4E7E2)

// Risk colors are always paired with an icon + text label in the UI —
// never color alone. See RiskBadge in core/designsystem/components.
val RiskLow = Color(0xFF2E9E5B)
val RiskLowContainer = Color(0xFFE1F3E8)
val RiskMedium = Color(0xFFE0A930)
val RiskMediumContainer = Color(0xFFFBEFDA)
val RiskHigh = Color(0xFFD64545)
val RiskHighContainer = Color(0xFFFBE3E3)
val RiskUnknown = Color(0xFF9CA3A0)
val RiskUnknownContainer = Color(0xFFEDEFEC)
