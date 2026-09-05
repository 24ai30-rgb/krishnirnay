package com.krishinirnay.core.data.mock

import com.krishinirnay.core.data.model.DayForecast
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Static demo forecast — no real weather API is wired up in Phase 1. */
@Singleton
class MockWeatherRepositoryImpl @Inject constructor() : WeatherRepository {

    private val _weather = MutableStateFlow(
        WeatherState(
            locationLabel = "Kolhapur, Maharashtra",
            currentTempC = 29,
            condition = WeatherCondition.PARTLY_CLOUDY,
            windKph = 12,
            humidityPct = 67,
            rainChancePct = 18,
            rainInHoursLabel = "48",
            daily = listOf(
                DayForecast("Wed", WeatherCondition.PARTLY_CLOUDY, highC = 30, lowC = 24),
                DayForecast("Thu", WeatherCondition.CLOUDY, highC = 29, lowC = 24),
                DayForecast("Fri", WeatherCondition.RAIN, highC = 31, lowC = 24),
                DayForecast("Sat", WeatherCondition.RAIN, highC = 28, lowC = 23),
                DayForecast("Sun", WeatherCondition.PARTLY_CLOUDY, highC = 30, lowC = 24),
                DayForecast("Mon", WeatherCondition.SUNNY, highC = 32, lowC = 25),
                DayForecast("Tue", WeatherCondition.SUNNY, highC = 33, lowC = 25),
            ),
        ),
    )
    override val weather: StateFlow<WeatherState> = _weather.asStateFlow()
}
