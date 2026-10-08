package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponseDto(
    val location_label: String,
    val current_temp_c: Float,
    val feelslike_c: Float? = null,
    val condition: String,
    val condition_code: Int? = null,
    val condition_icon_url: String? = null,
    val wind_kph: Float,
    val wind_direction: String? = null,
    val humidity_pct: Float,
    val cloud_pct: Int? = null,
    val pressure_mb: Float? = null,
    val visibility_km: Float? = null,
    val uv_index: Float? = null,
    val rain_chance_pct: Float,
    val rainfall_mm: Float? = null,
    val daily: List<DailyForecastDto> = emptyList(),
    val source: String,
)

@Serializable
data class DailyForecastDto(
    val day_label: String,
    val condition: String,
    val high_c: Float,
    val low_c: Float,
    val rain_chance_pct: Int? = null,
    val rainfall_mm: Float? = null,
)
