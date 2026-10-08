package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DayForecast
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore-backed persistence for the last known-good [WeatherState] — same pattern
 * as [FieldStateCache]/[FarmerProfileStore] (same shared `DataStore<Preferences>`, no
 * new persistence mechanism). [status] is deliberately never persisted — a value loaded
 * from here is, by definition, no longer fresh, so [load] always hands back
 * [DataSourceStatus.CACHED] regardless of what status was in effect when [save] was
 * called; only a genuinely successful live fetch is allowed to mark it LIVE again.
 */
@Singleton
class WeatherStateCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(state: WeatherState) {
        dataStore.edit { it[Keys.CACHED_WEATHER] = json.encodeToString(CachedWeatherDto.serializer(), state.toDto()) }
    }

    suspend fun load(): WeatherState? {
        val raw = dataStore.data.map { it[Keys.CACHED_WEATHER] }.first() ?: return null
        return runCatching { json.decodeFromString(CachedWeatherDto.serializer(), raw).toDomain() }.getOrNull()
    }

    private object Keys {
        val CACHED_WEATHER = stringPreferencesKey("cached_weather_state")
    }
}

@Serializable
private data class CachedDayForecastDto(
    val dayLabel: String,
    val condition: String,
    val highC: Int,
    val lowC: Int,
)

@Serializable
private data class CachedWeatherDto(
    val locationLabel: String,
    val currentTempC: Int,
    val condition: String,
    val windKph: Int,
    val humidityPct: Int,
    val rainChancePct: Int,
    val rainInHoursLabel: String,
    val daily: List<CachedDayForecastDto>,
    val rainfallMm: Float?,
)

private fun conditionFromOrDefault(name: String): WeatherCondition =
    runCatching { WeatherCondition.valueOf(name) }.getOrDefault(WeatherCondition.CLOUDY)

private fun WeatherState.toDto() = CachedWeatherDto(
    locationLabel = locationLabel,
    currentTempC = currentTempC,
    condition = condition.name,
    windKph = windKph,
    humidityPct = humidityPct,
    rainChancePct = rainChancePct,
    rainInHoursLabel = rainInHoursLabel,
    daily = daily.map { CachedDayForecastDto(it.dayLabel, it.condition.name, it.highC, it.lowC) },
    rainfallMm = rainfallMm,
)

private fun CachedWeatherDto.toDomain() = WeatherState(
    locationLabel = locationLabel,
    currentTempC = currentTempC,
    condition = conditionFromOrDefault(condition),
    windKph = windKph,
    humidityPct = humidityPct,
    rainChancePct = rainChancePct,
    rainInHoursLabel = rainInHoursLabel,
    daily = daily.map { DayForecast(it.dayLabel, conditionFromOrDefault(it.condition), it.highC, it.lowC) },
    rainfallMm = rainfallMm,
    status = DataSourceStatus.CACHED,
)
