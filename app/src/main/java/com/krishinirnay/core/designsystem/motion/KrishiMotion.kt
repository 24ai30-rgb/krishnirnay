package com.krishinirnay.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Expressive-motion tokens. Short durations keep the app feeling fast; nothing waits on an animation. */
object KrishiMotion {
    const val QUICK = 150
    const val STANDARD = 280
    const val EMPHASIS = 400
    const val STAGGER_STEP_MS = 40
    const val STAGGER_MAX_INDEX = 8

    /** Entrance delay for the [index]-th item, or null when past the cap (item appears instantly). */
    fun staggerDelayMillis(index: Int): Int? =
        if (index > STAGGER_MAX_INDEX) null else index.coerceAtLeast(0) * STAGGER_STEP_MS

    fun motionEnabled(animatorScale: Float): Boolean = animatorScale > 0f

    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun <T> firm(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

/** False when the user turned animations off system-wide — every primitive then renders its end state. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberSystemMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        KrishiMotion.motionEnabled(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
}
