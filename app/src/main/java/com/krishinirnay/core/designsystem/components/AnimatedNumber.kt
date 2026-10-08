package com.krishinirnay.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import com.krishinirnay.core.designsystem.motion.LocalMotionEnabled

/** Counts up to [target]. Snaps instantly when motion is off. */
@Composable
fun AnimatedNumber(
    target: Float,
    format: (Float) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val motion = LocalMotionEnabled.current
    val value = remember { Animatable(if (motion) 0f else target) }
    LaunchedEffect(target, motion) {
        if (motion) value.animateTo(target, tween(KrishiMotion.EMPHASIS * 2, easing = FastOutSlowInEasing))
        else value.snapTo(target)
    }
    Text(format(value.value), style = style, color = color, modifier = modifier)
}
