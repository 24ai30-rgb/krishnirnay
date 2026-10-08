package com.krishinirnay.designsystem

import com.krishinirnay.core.designsystem.components.GaugeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GaugeFormatTest {
    @Test fun missingValueShowsDash_notZero() {
        assertEquals("—", GaugeFormat.percent(null))
        assertNull(GaugeFormat.fraction(null, 100f))
    }

    @Test fun percentRoundsToWholeNumber() {
        assertEquals("18%", GaugeFormat.percent(18.4f))
    }

    @Test fun fractionClampedToUnitRange() {
        assertEquals(0.5f, GaugeFormat.fraction(25f, 50f)!!, 0.0001f)
        assertEquals(1f, GaugeFormat.fraction(140f, 100f)!!, 0.0001f)
        assertEquals(0f, GaugeFormat.fraction(-3f, 100f)!!, 0.0001f)
    }

    @Test fun nonFiniteTreatedAsMissing() {
        assertEquals("—", GaugeFormat.percent(Float.NaN))
        assertNull(GaugeFormat.fraction(Float.NaN, 100f))
    }
}
