package com.krishinirnay.core.data.repository

/**
 * Presenter-triggerable overrides, implemented by whichever
 * [FieldStateRepository] is backed by the Narrative Engine. Callers
 * reach for this via `fieldStateRepository as? MockControls` and no-op
 * if absent — correctly disabled when Live Mode is active, since you
 * can't "simulate irrigation" on real sensor data.
 */
interface MockControls {
    fun triggerIrrigation()
    fun triggerDeviceDisconnect()
    fun triggerDeviceReconnect()
}
