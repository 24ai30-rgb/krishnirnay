package com.krishinirnay.feature.analytics

enum class AnalyticsMetric { MOISTURE, TEMPERATURE, HUMIDITY }

data class AnalyticsUiState(
    val hasEnoughData: Boolean = false,
    val isOnline: Boolean = true,
    val soilMoistureHistory: List<Float> = emptyList(),
    val temperatureHistory: List<Float> = emptyList(),
    val humidityHistory: List<Float> = emptyList(),
)
