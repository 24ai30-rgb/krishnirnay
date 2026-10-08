package com.krishinirnay.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.krishinirnay.core.data.local.WeatherStateCache
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.network.LiveWeatherRepositoryImpl
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.network.WeatherApiService
import com.krishinirnay.core.network.dto.WeatherResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

private fun profileWithLocation(state: String) = FarmerProfile(
    name = "Test",
    phone = "0",
    location = "",
    farmSizeAcres = 1f,
    crops = listOf("Cotton"),
    farmLocation = FarmLocation(state = state),
)

private fun profileWithCoordinates(latitude: Double, longitude: Double, state: String = "") = FarmerProfile(
    name = "Test",
    phone = "0",
    location = "",
    farmSizeAcres = 1f,
    crops = listOf("Cotton"),
    farmLocation = FarmLocation(state = state, latitude = latitude, longitude = longitude),
)

private fun stubProfile(repo: ProfileRepository, profile: FarmerProfile) {
    every { repo.profile } returns MutableStateFlow(profile)
}

private fun successResponse(tempC: Float = 30f) = Response.success(
    WeatherResponseDto(
        location_label = "Nagpur",
        current_temp_c = tempC,
        condition = "SUNNY",
        wind_kph = 10f,
        wind_direction = "NE",
        humidity_pct = 50f,
        rain_chance_pct = 10f,
        source = "test-provider",
    ),
)

/** A cache mock whose `load()`/`save()` never touch disk — for tests that only care about behavior around a single repository instance. */
private fun noopCache(): WeatherStateCache {
    val cache = mockk<WeatherStateCache>()
    coEvery { cache.load() } returns null
    coEvery { cache.save(any()) } returns Unit
    return cache
}

/** A real, file-backed cache — for tests proving persistence actually survives repository recreation (simulated app restart). */
private fun realCache(tempDir: File) = WeatherStateCache(
    PreferenceDataStoreFactory.create(
        scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
        produceFile = { File(tempDir, "weather_test.preferences_pb") },
    ),
)

private fun createTempDir(): File = File.createTempFile("weather_cache_test", "").let {
    it.delete()
    it.mkdirs()
    it
}

class LiveWeatherRepositoryImplTest {

    @Test
    fun `no farm location set is honestly UNAVAILABLE, never a fabricated reading`() = runTest(UnconfinedTestDispatcher()) {
        val weatherApiService = mockk<WeatherApiService>()
        val profileRepository = mockk<ProfileRepository>()
        stubProfile(profileRepository, profileWithLocation(state = ""))

        val repo = LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.UNAVAILABLE, repo.weather.value.status)
    }

    @Test
    fun `a failed poll after a real success keeps the last known reading but marks it CACHED`() = runTest(UnconfinedTestDispatcher()) {
        val weatherApiService = mockk<WeatherApiService>()
        val profileRepository = mockk<ProfileRepository>()
        stubProfile(profileRepository, profileWithLocation(state = "Maharashtra"))

        coEvery { weatherApiService.getWeather(state = "Maharashtra", district = null) } returns successResponse()

        val repo = LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.LIVE, repo.weather.value.status)
        assertEquals(30, repo.weather.value.currentTempC)
        assertEquals("NE", repo.weather.value.windDirection)

        coEvery { weatherApiService.getWeather(state = "Maharashtra", district = null) } throws RuntimeException("unreachable")
        repo.refresh()

        // Value preserved, but no longer claimed fresh.
        assertEquals(30, repo.weather.value.currentTempC)
        assertEquals(DataSourceStatus.CACHED, repo.weather.value.status)
    }

    // TEST 1: Successful Weather data is persisted.
    @Test
    fun `a successful fetch persists the reading to WeatherStateCache`() = runTest(UnconfinedTestDispatcher()) {
        val weatherApiService = mockk<WeatherApiService>()
        val profileRepository = mockk<ProfileRepository>()
        stubProfile(profileRepository, profileWithLocation(state = "Maharashtra"))
        coEvery { weatherApiService.getWeather(state = "Maharashtra", district = null) } returns successResponse(tempC = 31f)

        val cache = noopCache()
        LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, cache)

        coVerify { cache.save(match { it.currentTempC == 31 && it.status == DataSourceStatus.LIVE }) }
    }

    // TEST 2 + TEST 3: Persisted Weather data survives repository recreation, and is reloaded as CACHED.
    @Test
    fun `persisted weather survives repository recreation and reloads as CACHED`() = runTest(UnconfinedTestDispatcher()) {
        val tempDir = createTempDir()
        val cache = realCache(tempDir)

        val firstProcessApiService = mockk<WeatherApiService>()
        val firstProcessProfile = mockk<ProfileRepository>()
        stubProfile(firstProcessProfile, profileWithLocation(state = "Maharashtra"))
        coEvery { firstProcessApiService.getWeather(state = "Maharashtra", district = null) } returns successResponse(tempC = 33f)

        // "First app run": a real fetch succeeds and is persisted.
        val firstRunRepo = LiveWeatherRepositoryImpl(backgroundScope, firstProcessApiService, firstProcessProfile, cache)
        assertEquals(DataSourceStatus.LIVE, firstRunRepo.weather.value.status)
        assertEquals(33, firstRunRepo.weather.value.currentTempC)

        // "App restart": a brand new repository instance, sharing only the on-disk cache —
        // never the in-memory StateFlow — with a provider that is now unreachable.
        val secondProcessApiService = mockk<WeatherApiService>()
        val secondProcessProfile = mockk<ProfileRepository>()
        stubProfile(secondProcessProfile, profileWithLocation(state = "Maharashtra"))
        coEvery { secondProcessApiService.getWeather(state = "Maharashtra", district = null) } throws RuntimeException("unreachable after restart")

        val afterRestartRepo = LiveWeatherRepositoryImpl(backgroundScope, secondProcessApiService, secondProcessProfile, cache)

        // The persisted reading survived the "restart" and is honestly marked CACHED, not LIVE.
        assertEquals(33, afterRestartRepo.weather.value.currentTempC)
        assertEquals(DataSourceStatus.CACHED, afterRestartRepo.weather.value.status)
    }

    // TEST 4: Weather provider failure does not erase the last known valid Weather data —
    // specifically for a value that arrived via cache restore, not a live fetch in this
    // process. This is the exact bug Phase 4A fixes: the old code only preserved values
    // whose status was already LIVE, so a restored CACHED value was wiped by the very next
    // failed poll (which, before a real provider exists, is every poll).
    @Test
    fun `a fetch failure never erases a value that was restored from cache`() = runTest(UnconfinedTestDispatcher()) {
        val weatherApiService = mockk<WeatherApiService>()
        val profileRepository = mockk<ProfileRepository>()
        stubProfile(profileRepository, profileWithLocation(state = "Maharashtra"))
        coEvery { weatherApiService.getWeather(state = "Maharashtra", district = null) } throws RuntimeException("provider not configured")

        // Exactly what WeatherStateCache.load() hands back for a genuinely persisted reading.
        val restoredFromDisk = com.krishinirnay.core.data.model.WeatherState(
            locationLabel = "Nagpur",
            currentTempC = 29,
            condition = com.krishinirnay.core.data.model.WeatherCondition.SUNNY,
            windKph = 10,
            humidityPct = 50,
            rainChancePct = 10,
            rainInHoursLabel = "24",
            daily = emptyList(),
            status = DataSourceStatus.CACHED,
        )
        val cache = mockk<WeatherStateCache>()
        coEvery { cache.load() } returns restoredFromDisk
        coEvery { cache.save(any()) } returns Unit

        // init{} restores the cached value, then immediately attempts a fetch that fails.
        val repo = LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, cache)

        assertEquals(29, repo.weather.value.currentTempC)
        assertEquals(DataSourceStatus.CACHED, repo.weather.value.status)
    }

    // TEST 5: No cached Weather data + provider unavailable = UNAVAILABLE.
    @Test
    fun `no cache and an unreachable provider settles to UNAVAILABLE, not a fabricated reading`() = runTest(UnconfinedTestDispatcher()) {
        val weatherApiService = mockk<WeatherApiService>()
        val profileRepository = mockk<ProfileRepository>()
        stubProfile(profileRepository, profileWithLocation(state = "Maharashtra"))
        coEvery { weatherApiService.getWeather(state = "Maharashtra", district = null) } throws RuntimeException("provider not configured")

        val repo = LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.UNAVAILABLE, repo.weather.value.status)
    }

    // --- Farmer's real saved latitude/longitude ---

    @Test
    fun `the farmer's saved latitude and longitude are sent to the server`() = runTest(UnconfinedTestDispatcher()) {
        val weatherApiService = mockk<WeatherApiService>()
        val profileRepository = mockk<ProfileRepository>()
        stubProfile(profileRepository, profileWithCoordinates(latitude = 21.15, longitude = 79.09, state = "Maharashtra"))
        coEvery {
            weatherApiService.getWeather(state = "Maharashtra", district = null, latitude = 21.15, longitude = 79.09)
        } returns successResponse()

        LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, noopCache())

        coVerify {
            weatherApiService.getWeather(state = "Maharashtra", district = null, latitude = 21.15, longitude = 79.09)
        }
    }

    @Test
    fun `coordinates alone with no state text still triggers a real fetch, never UNAVAILABLE just for blank state`() =
        runTest(UnconfinedTestDispatcher()) {
            val weatherApiService = mockk<WeatherApiService>()
            val profileRepository = mockk<ProfileRepository>()
            stubProfile(profileRepository, profileWithCoordinates(latitude = 21.15, longitude = 79.09, state = ""))
            coEvery {
                weatherApiService.getWeather(state = "", district = null, latitude = 21.15, longitude = 79.09)
            } returns successResponse(tempC = 29f)

            val repo = LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, noopCache())

            assertEquals(DataSourceStatus.LIVE, repo.weather.value.status)
            assertEquals(29, repo.weather.value.currentTempC)
        }

    @Test
    fun `the extended WeatherAPI fields map through to WeatherState, including per-day rain chance and amount`() =
        runTest(UnconfinedTestDispatcher()) {
            val weatherApiService = mockk<WeatherApiService>()
            val profileRepository = mockk<ProfileRepository>()
            stubProfile(profileRepository, profileWithLocation(state = "Maharashtra"))
            coEvery { weatherApiService.getWeather(state = "Maharashtra", district = null) } returns Response.success(
                WeatherResponseDto(
                    location_label = "Nagpur",
                    current_temp_c = 31f,
                    feelslike_c = 33f,
                    condition = "SUNNY",
                    condition_code = 1000,
                    condition_icon_url = "https://cdn.weatherapi.com/weather/64x64/day/113.png",
                    wind_kph = 14f,
                    wind_direction = "SW",
                    humidity_pct = 55f,
                    cloud_pct = 10,
                    pressure_mb = 1008f,
                    visibility_km = 10f,
                    uv_index = 6f,
                    rain_chance_pct = 15f,
                    rainfall_mm = 2.5f,
                    daily = listOf(
                        com.krishinirnay.core.network.dto.DailyForecastDto(
                            day_label = "Sat",
                            condition = "RAIN",
                            high_c = 30f,
                            low_c = 23f,
                            rain_chance_pct = 70,
                            rainfall_mm = 4.2f,
                        ),
                    ),
                    source = "weatherapi.com",
                ),
            )

            val repo = LiveWeatherRepositoryImpl(backgroundScope, weatherApiService, profileRepository, noopCache())
            val state = repo.weather.value

            assertEquals(33, state.feelsLikeC)
            assertEquals("https://cdn.weatherapi.com/weather/64x64/day/113.png", state.conditionIconUrl)
            assertEquals(10, state.cloudPct)
            assertEquals(1008f, state.pressureMb)
            assertEquals(10f, state.visibilityKm)
            assertEquals(6f, state.uvIndex)
            assertEquals(1, state.daily.size)
            assertEquals(70, state.daily[0].rainChancePct)
            assertEquals(4.2f, state.daily[0].rainfallMm)
        }
}
