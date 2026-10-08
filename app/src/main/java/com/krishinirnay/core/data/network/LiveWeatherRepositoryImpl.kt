package com.krishinirnay.core.data.network

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.local.WeatherStateCache
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DayForecast
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.network.WeatherApiService
import com.krishinirnay.core.network.dto.WeatherResponseDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Calls the server's `GET /v1/weather` (never a third-party API directly — the
 * server holds the provider key) whenever the farmer's
 * [com.krishinirnay.core.data.model.FarmLocation] is usable. The server calls
 * WeatherAPI.com — see `server/app/services/weather_provider.py`. When a request
 * fails this settles into [DataSourceStatus.CACHED] (if a real reading was
 * already on screen) or [DataSourceStatus.UNAVAILABLE], never a fabricated
 * temperature.
 *
 * Note the request is only ever built from [FarmerProfile.farmLocation]; a
 * profile whose structured location is blank produces no request at all, which
 * is why `FarmerProfileRepositoryImpl` backfills it from the legacy display
 * string.
 *
 * Phase 4A: the last successful reading is persisted via [WeatherStateCache]
 * (same DataStore pattern as [com.krishinirnay.core.data.local.FieldStateCache]) and
 * restored on init, marked [DataSourceStatus.CACHED] — a restart no longer resets
 * straight to UNAVAILABLE.
 */
@Singleton
class LiveWeatherRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val weatherApiService: WeatherApiService,
    private val profileRepository: ProfileRepository,
    private val weatherStateCache: WeatherStateCache,
) : WeatherRepository {

    // LOADING, not UNAVAILABLE: before the first response there is nothing to
    // report yet, and rendering that as "not available" is indistinguishable
    // from a permanent failure to the farmer.
    private val _weather = MutableStateFlow(unavailableState(status = DataSourceStatus.LOADING))
    override val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    init {
        scope.launch {
            // Restore the last known-good reading first, so cold start never shows
            // UNAVAILABLE when a valid (if stale) reading actually exists.
            weatherStateCache.load()?.let { cached ->
                _weather.value = cached
            }

            profileRepository.profile
                .map { it.farmLocation }
                .distinctUntilChanged()
                .collect { location -> fetchWeather(location) }
        }
    }

    override suspend fun refresh() {
        fetchWeather(profileRepository.profile.value.farmLocation)
    }

    private suspend fun fetchWeather(location: FarmLocation) {
        if (!location.isUsable()) {
            // Nothing to request yet — the farmer has no saved location. Distinct
            // from a failed request, so the UI can say "add your farm location"
            // instead of "weather unavailable".
            android.util.Log.i("WEATHER", "Skipping fetch: no usable farm location saved yet")
            preserveAsCachedOrUnavailable()
            return
        }

        if (_weather.value.status == DataSourceStatus.UNAVAILABLE ||
            _weather.value.status == DataSourceStatus.LOADING
        ) {
            _weather.value = _weather.value.copy(status = DataSourceStatus.LOADING)
        }

        try {
            val response = weatherApiService.getWeather(
                state = location.state,
                district = location.district.ifBlank { null },
                latitude = location.latitude,
                longitude = location.longitude,
            )

            if (!response.isSuccessful) {
                throw RuntimeException("Weather API HTTP ${response.code()}")
            }

            val body = response.body() ?: throw RuntimeException("Empty weather response")
            val next = body.toDomain()
            android.util.Log.i(
                "WEATHER",
                "Live weather OK: source=${body.source} temp=${body.current_temp_c}C days=${body.daily.size}",
            )
            _weather.value = next
            weatherStateCache.save(next)
        } catch (error: Exception) {
            android.util.Log.e("WEATHER", "Weather fetch failed: ${error.message}", error)
            preserveAsCachedOrUnavailable()
        }
    }

    /**
     * A failed/skippable fetch must never erase a value the farmer could still see
     * before this call — whether that value just arrived LIVE or was already
     * CACHED (e.g. restored at cold start, or from an earlier failed attempt).
     * Only when there was genuinely nothing to preserve does this settle to
     * UNAVAILABLE.
     */
    private fun preserveAsCachedOrUnavailable() {
        val current = _weather.value
        // Only LIVE/CACHED carry real numbers worth keeping. LOADING and
        // UNAVAILABLE hold placeholder zeros, so marking those CACHED would
        // present 0°C as a "last known reading" — a fabricated value.
        val hasRealReading = current.status == DataSourceStatus.LIVE ||
            current.status == DataSourceStatus.CACHED
        _weather.value = if (hasRealReading) {
            current.copy(status = DataSourceStatus.CACHED)
        } else {
            unavailableState(locationLabel = current.locationLabel)
        }
    }

    private fun unavailableState(
        locationLabel: String = "",
        status: DataSourceStatus = DataSourceStatus.UNAVAILABLE,
    ) = WeatherState(
        locationLabel = locationLabel,
        currentTempC = 0,
        condition = WeatherCondition.CLOUDY,
        windKph = 0,
        humidityPct = 0,
        rainChancePct = 0,
        rainInHoursLabel = "-",
        daily = emptyList(),
        rainfallMm = null,
        status = status,
    )
}

private fun WeatherResponseDto.toDomain(): WeatherState = WeatherState(
    locationLabel = location_label,
    currentTempC = current_temp_c.toInt(),
    feelsLikeC = feelslike_c?.toInt(),
    condition = conditionFrom(condition),
    conditionIconUrl = condition_icon_url,
    windKph = wind_kph.toInt(),
    windDirection = wind_direction,
    humidityPct = humidity_pct.toInt(),
    cloudPct = cloud_pct,
    pressureMb = pressure_mb,
    visibilityKm = visibility_km,
    uvIndex = uv_index,
    rainChancePct = rain_chance_pct.toInt(),
    rainInHoursLabel = "24",
    daily = daily.map {
        DayForecast(
            dayLabel = it.day_label,
            condition = conditionFrom(it.condition),
            highC = it.high_c.toInt(),
            lowC = it.low_c.toInt(),
            rainChancePct = it.rain_chance_pct,
            rainfallMm = it.rainfall_mm,
        )
    },
    rainfallMm = rainfall_mm,
    status = DataSourceStatus.LIVE,
)

private fun conditionFrom(raw: String): WeatherCondition =
    runCatching { WeatherCondition.valueOf(raw.uppercase()) }.getOrDefault(WeatherCondition.CLOUDY)
