package com.krishinirnay.core.data.model

/** One day (or today) of forecast — condition is a coarse enum so the UI can pick one icon per value. */
enum class WeatherCondition {
    SUNNY,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    STORM,
}

data class DayForecast(
    val dayLabel: String,
    val condition: WeatherCondition,
    val highC: Int,
    val lowC: Int,
)

/** Mock-only for Phase 1 — no real weather API is wired up yet, see MockWeatherRepositoryImpl. */
data class WeatherState(
    val locationLabel: String,
    val currentTempC: Int,
    val condition: WeatherCondition,
    val windKph: Int,
    val humidityPct: Int,
    val rainChancePct: Int,
    val rainInHoursLabel: String,
    val daily: List<DayForecast>,
)
