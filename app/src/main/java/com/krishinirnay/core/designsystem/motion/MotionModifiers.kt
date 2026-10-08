package com.krishinirnay.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import kotlinx.coroutines.delay

/** Fade + rise + slight scale-up entrance, staggered by [index]. Plays once per item (survives scroll). */
fun Modifier.enterStagger(index: Int): Modifier = composed {
    val delayMs = KrishiMotion.staggerDelayMillis(index)
    if (!LocalMotionEnabled.current || delayMs == null) return@composed this
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(delayMs.toLong())
            progress.animateTo(1f, KrishiMotion.bouncy())
            played = true
        }
    }
    val risePx = with(LocalDensity.current) { 24.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * risePx
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/** Shrinks to 96% while [interactionSource] is pressed. Pair with the same source on clickable. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && LocalMotionEnabled.current) 0.96f else 1f
    val scale by animateFloatAsState(target, KrishiMotion.bouncy(), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** clickable + ripple + press-scale in one. */
fun Modifier.pressClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source)
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Skeleton shimmer. Static surfaceAlt fill when motion is off. */
fun Modifier.shimmer(): Modifier = composed {
    val base = KrishiTheme.colors.surfaceAlt
    val highlight = MaterialTheme.colorScheme.surface
    if (!LocalMotionEnabled.current) return@composed drawBehind { drawRect(base) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerX",
    )
    drawBehind {
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(size.width * (x - 1f), 0f),
                end = Offset(size.width * x, size.height),
            ),
        )
    }
}

/** Gentle breathing scale for attention (HIGH risk badge, mic FAB). */
fun Modifier.pulse(enabled: Boolean): Modifier = composed {
    if (!enabled || !LocalMotionEnabled.current) return@composed this
    val transition = rememberInfiniteTransition(label = "pulse")
    val s by transition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}
