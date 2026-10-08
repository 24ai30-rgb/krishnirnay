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
