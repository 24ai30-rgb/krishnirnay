package com.krishinirnay.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

enum class RiskBadgeSize { Compact, Hero }

/**
 * Color is never the only signal — every badge pairs a distinct icon
 * shape and a text label with the risk color, so the badge stays
 * legible under any color-vision deficiency. See the design system
 * plan's colorblind-accessibility loophole fix.
 */
@Composable
fun RiskBadge(
    level: RiskLevel,
    modifier: Modifier = Modifier,
    size: RiskBadgeSize = RiskBadgeSize.Compact,
) {
    if (size == RiskBadgeSize.Hero) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            val glowColor = riskBadgeStyle(level).containerColor
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.5f), glowColor.copy(alpha = 0f)))),
            )
            AnimatedContent(targetState = level, label = "riskBadgeHero", transitionSpec = { fadeIn().togetherWith(fadeOut()) }) { animatedLevel ->
                RiskBadgeContent(level = animatedLevel, size = size)
            }
        }
    } else {
        AnimatedContent(targetState = level, label = "riskBadgeCompact", transitionSpec = { fadeIn().togetherWith(fadeOut()) }, modifier = modifier) { animatedLevel ->
            RiskBadgeContent(level = animatedLevel, size = size)
        }
    }
}

@Composable
private fun RiskBadgeContent(level: RiskLevel, size: RiskBadgeSize) {
    val style = riskBadgeStyle(level)
    val horizontalPadding = if (size == RiskBadgeSize.Hero) 18.dp else 8.dp
    val verticalPadding = if (size == RiskBadgeSize.Hero) 10.dp else 4.dp
    val iconSize = if (size == RiskBadgeSize.Hero) 24.dp else 14.dp
    val textStyle = if (size == RiskBadgeSize.Hero) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(style.containerColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (size == RiskBadgeSize.Hero) 10.dp else 4.dp),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.contentColor,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = style.label,
            style = textStyle,
            color = style.contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class RiskBadgeStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun riskBadgeStyle(level: RiskLevel): RiskBadgeStyle {
    val colors = KrishiTheme.colors
    val strings = LocalAppStrings.current
    return when (level) {
        RiskLevel.LOW -> RiskBadgeStyle(colors.riskLowContainer, colors.riskLow, Icons.Rounded.CheckCircle, strings.riskLow)
        RiskLevel.MEDIUM -> RiskBadgeStyle(colors.riskMediumContainer, colors.riskMedium, Icons.Rounded.Warning, strings.riskMedium)
        RiskLevel.HIGH -> RiskBadgeStyle(colors.riskHighContainer, colors.riskHigh, Icons.Rounded.Error, strings.riskHigh)
        RiskLevel.UNKNOWN -> RiskBadgeStyle(colors.riskUnknownContainer, colors.riskUnknown, Icons.Rounded.HelpOutline, strings.riskNotAssessed)
    }
}
