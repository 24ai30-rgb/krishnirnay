package com.krishinirnay.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import com.krishinirnay.core.designsystem.motion.LocalMotionEnabled

/** Line + gradient area chart that draws itself left-to-right on first show. */
@Composable
fun SimpleLineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionEnabled.current
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) reveal.animateTo(1f, tween(KrishiMotion.EMPHASIS * 2, easing = FastOutSlowInEasing)) else reveal.snapTo(1f)
    }
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).takeIf { it > 0.01f } ?: 1f
        val stepX = size.width / (values.size - 1)

        val points = values.mapIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / range) * size.height
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        clipRect(right = size.width * reveal.value) {
            drawPath(fillPath, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(
                path = linePath,
                color = color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        if (reveal.value >= 1f) drawCircle(color = color, radius = 5.dp.toPx(), center = points.last())
    }
}
