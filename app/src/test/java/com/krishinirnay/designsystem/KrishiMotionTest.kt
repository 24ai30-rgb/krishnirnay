package com.krishinirnay.designsystem

import com.krishinirnay.core.designsystem.motion.KrishiMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KrishiMotionTest {
    @Test fun staggerGrowsByStep() {
        assertEquals(0, KrishiMotion.staggerDelayMillis(0))
        assertEquals(120, KrishiMotion.staggerDelayMillis(3))
    }

    @Test fun staggerCapped() {
        // Beyond the cap an item gets no entrance at all (null) — never a long delay.
        assertEquals(320, KrishiMotion.staggerDelayMillis(8))
        assertNull(KrishiMotion.staggerDelayMillis(9))
        assertNull(KrishiMotion.staggerDelayMillis(500))
    }

    @Test fun negativeIndexTreatedAsFirst() {
        assertEquals(0, KrishiMotion.staggerDelayMillis(-1))
    }

    @Test fun motionDisabledWhenScaleZero() {
        assertFalse(KrishiMotion.motionEnabled(0f))
        assertTrue(KrishiMotion.motionEnabled(1f))
        assertTrue(KrishiMotion.motionEnabled(0.5f))
    }
}
