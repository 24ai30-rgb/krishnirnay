package com.krishinirnay.feature.monitoring

import java.time.Instant

data class LiveMonitoringUiState(
    val isOnline: Boolean = true,
    val batteryPct: Int? = null,
    val soilMoisturePct: Float = 0f,
    val temperatureC: Float = 0f,
    val humidityPct: Float = 0f,
    val ph: Float? = null,
    val lastUpdatedAt: Instant = Instant.EPOCH,
)
