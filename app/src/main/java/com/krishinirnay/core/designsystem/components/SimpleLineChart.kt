package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Minimal hand-rolled line chart with a faint area fill and an
 * emphasized endpoint — used for Analytics' session-depth trend lines.
 * Not the Vico library named in the design plan: Vico's exact Compose
 * API for the pinned version couldn't be verified without a compiler in
 * this environment, and a multi-call charting-library integration
 * guessed wrong fails as a single hard-to-diagnose block. This is a
 * deliberate, documented substitution — swapping in real Vico later is
 * a good follow-up once it can be iterated on with real compiler
 * feedback.
 */
@Composable
fun SimpleLineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
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

        drawPath(fillPath, color.copy(alpha = 0.12f))
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = color, radius = 5.dp.toPx(), center = points.last())
    }
}
