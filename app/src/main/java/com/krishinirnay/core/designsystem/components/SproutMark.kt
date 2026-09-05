package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap

/**
 * A small custom two-leaf sprout mark — the app's own visual signature in
 * place of a generic stock icon on the Login screen. Built from plain
 * quadratic-bezier leaf shapes (no external asset, no Matrix/rotation math)
 * to keep the geometry simple and predictable.
 */
@Composable
fun SproutMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseX = w / 2f
        val baseY = h * 0.95f
        val leafHeight = h * 0.65f
        val leafSpread = w * 0.42f

        fun leaf(dxSign: Int): Path = Path().apply {
            moveTo(baseX, baseY)
            quadraticTo(baseX + dxSign * leafSpread, baseY - leafHeight * 0.55f, baseX + dxSign * leafSpread * 0.15f, baseY - leafHeight)
            quadraticTo(baseX, baseY - leafHeight * 0.7f, baseX, baseY)
            close()
        }

        drawPath(leaf(1), color = color)
        drawPath(leaf(-1), color = color)
        drawLine(
            color = color,
            start = Offset(baseX, baseY),
            end = Offset(baseX, h),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round,
        )
    }
}
