package com.krishinirnay.core.data.model

import java.time.Instant

/**
 * Mirrors the Firebase Realtime Database schema 1:1 (see
 * docs/firebase-schema.md `/devices/{deviceId}/latest`) so the
 * Firebase-backed repository implementation is a straight parse, no
 * remapping layer. NPK/pH are nullable Phase 2 sensors — absent on
 * Phase 1 hardware and on every Mock Mode reading.
 */
data class SensorReading(
    val soilMoisturePct: Float,
    val temperatureC: Float,
    val humidityPct: Float,
    val nitrogenPpm: Float? = null,
    val phosphorusPpm: Float? = null,
    val potassiumPpm: Float? = null,
    val ph: Float? = null,
    val timestamp: Instant,
)
