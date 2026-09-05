package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LatestSensorResponseDto(
    val data: SensorDataDto? = null,
)

@Serializable
data class SensorDataDto(
    val temperature: Float,
    val humidity: Float,
    val soil_moisture: Float,
    val timestamp: String? = null,
)