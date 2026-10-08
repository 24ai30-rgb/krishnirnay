package com.krishinirnay.core.data.repository

import com.krishinirnay.core.mock.SensorScenario

/**
 * Presenter/developer-triggerable overrides, implemented by whichever
 * [FieldStateRepository] is backed by the Narrative Engine. Callers
 * reach for this via `fieldStateRepository as? MockControls` and no-op
 * if absent — correctly disabled when Live Mode is active, since you
 * can't "simulate irrigation" (or any other scenario) on real sensor data.
 */
interface MockControls {
    fun triggerIrrigation()
    fun triggerDeviceDisconnect()
    fun triggerDeviceReconnect()

    /** Jumps Mock Mode's simulated sensor readings to a specific scenario — see [SensorScenario]. */
    fun applySensorScenario(scenario: SensorScenario)
}
