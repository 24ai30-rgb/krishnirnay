package com.krishinirnay.core.mock

import java.time.Duration
import java.time.Instant

/**
 * Maps real elapsed time to accelerated simulated field time (default
 * 60x) so a multi-hour drying arc plays out within a 5-10 minute
 * demo/judge session. Pure function of [realNow] — no wall-clock reads
 * inside the class itself — so it's deterministic and trivial to
 * unit-test.
 */
class MockClock(
    private val accelerationFactor: Long = DEFAULT_ACCELERATION_FACTOR,
    private val realStart: Instant = Instant.now(),
    private val simulatedStart: Instant = realStart,
) {
    fun simulatedNow(realNow: Instant = Instant.now()): Instant {
        val realElapsedMillis = Duration.between(realStart, realNow).toMillis().coerceAtLeast(0)
        return simulatedStart.plusMillis(realElapsedMillis * accelerationFactor)
    }

    /** Simulated minutes elapsed since [realStart], as of [realNow]. */
    fun simulatedMinutesElapsed(realNow: Instant = Instant.now()): Long {
        val realElapsedMillis = Duration.between(realStart, realNow).toMillis().coerceAtLeast(0)
        return (realElapsedMillis * accelerationFactor) / 60_000L
    }

    companion object {
        const val DEFAULT_ACCELERATION_FACTOR = 60L
    }
}
