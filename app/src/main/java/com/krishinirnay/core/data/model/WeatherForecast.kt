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
    val rainChancePct: Int? = null,
    val rainfallMm: Float? = null,
)

/**
 * See [com.krishinirnay.core.data.repository.WeatherRepository] for how this is
 * sourced (Mock Mode's static demo forecast, or a Live-Mode call to the server's
 * `/v1/weather`, which itself is honestly UNAVAILABLE until a real provider is
 * configured — never fabricated). [status] must always be checked before treating
 * these numbers as fresh.
 */
data class WeatherState(
    val locationLabel: String,
    val currentTempC: Int,
    val feelsLikeC: Int? = null,
    val condition: WeatherCondition,
    val conditionIconUrl: String? = null,
    val windKph: Int,
    val windDirection: String? = null,
    val humidityPct: Int,
    val cloudPct: Int? = null,
    val pressureMb: Float? = null,
    val visibilityKm: Float? = null,
    val uvIndex: Float? = null,
    val rainChancePct: Int,
    val rainInHoursLabel: String,
    val daily: List<DayForecast>,
    val rainfallMm: Float? = null,
    val status: DataSourceStatus = DataSourceStatus.MOCK,
)
