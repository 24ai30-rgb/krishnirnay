package com.krishinirnay.core.designsystem.motion

import androidx.compose.runtime.staticCompositionLocalOf

/** False when the user turned animations off system-wide — every primitive then renders its end state. */
val LocalMotionEnabled = staticCompositionLocalOf { true }
