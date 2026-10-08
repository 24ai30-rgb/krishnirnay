package com.krishinirnay.core.mock

import kotlin.random.Random

/**
 * Mean-reverting bounded random walk — each [step] moves the current
 * value partway toward [target] plus small noise, clamped to
 * [min]/[max]. This is what makes Mock Mode data read as a believable
 * evolving story instead of pure noise: values drift purposefully
 * toward wherever the current [com.krishinirnay.core.mock.ScenarioScript]
 * stage says they should be heading.
 */
class SensorWalk(
    private val min: Float,
    private val max: Float,
    private val stepSize: Float,
    initialValue: Float = (min + max) / 2f,
    private val random: Random = Random.Default,
) {
    var value: Float = initialValue.coerceIn(min, max)
        private set

    /** Immediate override for a developer-triggered scenario — unlike [step], this never
     * gradually drifts; the farmer/tester sees the new value on the very next reading. */
    fun jumpTo(newValue: Float) {
        value = newValue.coerceIn(min, max)
    }

    fun step(target: Float) {
        val towardTarget = (target - value) * MEAN_REVERSION_FRACTION
        val noise = (random.nextFloat() * 2f - 1f) * stepSize
        value = (value + towardTarget + noise).coerceIn(min, max)
    }

    private companion object {
        const val MEAN_REVERSION_FRACTION = 0.15f
    }
}
