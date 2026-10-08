package com.krishinirnay.data

import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.network.dto.MarketResponseDto
import com.krishinirnay.core.network.dto.WeatherResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes JSON captured from the **real running FastAPI server** (which itself
 * had called WeatherAPI.com and data.gov.in live) using the exact production
 * DTOs and the exact production `Json` configuration from
 * `core/network/di/NetworkModule`.
 *
 * This is the regression guard for the failure mode that is invisible to both
 * sides on their own: the backend returns HTTP 200 with real data, the Android
 * DTOs compile fine, and yet the screen shows nothing because one field name,
 * nullability or type doesn't line up. Server-side tests can't catch that, and
 * neither can an Android test built from a hand-written fixture that quietly
 * agrees with the DTO instead of with the server.
 *
 * Fixtures: `app/src/test/resources/live_{weather,market}_response.json`.
 * Re-capture them with:
 *   curl -H "X-API-Key: <key>" "http://127.0.0.1:8000/v1/weather?state=Maharashtra&district=Kolhapur&latitude=16.705&longitude=74.243"
 *   curl -H "X-API-Key: <key>" "http://127.0.0.1:8000/v1/market?crop=Tomato&state=Maharashtra"
 */
class LiveResponseContractTest {

    /** Must match NetworkModule.provideJson() exactly, or this proves nothing. */
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `a real live weather response from the server parses into the production DTO`() {
        val dto = json.decodeFromString<WeatherResponseDto>(fixture("live_weather_response.json"))

        assertEquals("weatherapi.com", dto.source)
        assertEquals("Kolhapur, Maharashtra", dto.location_label)
        // Real values, not placeholders.
        assertTrue("temperature should be a real reading", dto.current_temp_c > -50f && dto.current_temp_c < 60f)
        assertTrue("humidity should be a real percentage", dto.humidity_pct in 0f..100f)
        assertTrue("wind should be a real speed", dto.wind_kph >= 0f)
        assertNotNull("wind direction should come through", dto.wind_direction)
        assertNotNull("feels-like should come through", dto.feelslike_c)
        assertNotNull("condition code should come through", dto.condition_code)
        // The forecast the WeatherScreen renders.
        assertEquals(7, dto.daily.size)
        assertTrue("every day needs a label", dto.daily.all { it.day_label.isNotBlank() })
        assertTrue("each day needs a high/low", dto.daily.all { it.high_c >= it.low_c })
    }

    @Test
    fun `a real live weather response maps onto the WeatherCondition enum the UI switches on`() {
        val dto = json.decodeFromString<WeatherResponseDto>(fixture("live_weather_response.json"))

        // WeatherScreen picks its icon from this enum; an unmapped condition
        // string would silently fall back to CLOUDY for every reading.
        val known = WeatherCondition.entries.map { it.name }
        assertTrue("current condition '${dto.condition}' must be a known enum", dto.condition in known)
        dto.daily.forEach { day ->
            assertTrue("daily condition '${day.condition}' must be a known enum", day.condition in known)
        }
    }

    @Test
    fun `a real live market response from the server parses into the production DTO`() {
        val dto = json.decodeFromString<MarketResponseDto>(fixture("live_market_response.json"))

        assertEquals("data.gov.in (AGMARKNET)", dto.source)
        // Every field the Dashboard market card displays.
        assertNotNull("mandi name", dto.market)
        assertNotNull("district/state location line", dto.location)
        assertNotNull("modal price", dto.current_price_per_quintal)
        assertNotNull("min price", dto.min_price_per_quintal)
        assertNotNull("max price", dto.max_price_per_quintal)
        assertNotNull("arrival date", dto.arrival_date)
        assertNotNull("state", dto.state)
        assertNotNull("district", dto.district)
        assertTrue("real mandi records expected", dto.markets.isNotEmpty())
        assertTrue(
            "modal price should be a real rupee figure",
            (dto.current_price_per_quintal ?: 0f) > 0f,
        )
    }

    @Test
    fun `a real live market response maps onto the MarketTrend enum the UI switches on`() {
        val dto = json.decodeFromString<MarketResponseDto>(fixture("live_market_response.json"))

        assertTrue(
            "trend '${dto.trend}' must be a known enum",
            dto.trend in MarketTrend.entries.map { it.name },
        )
    }

    @Test
    fun `every mandi record in a real response carries the fields the comparison list shows`() {
        val dto = json.decodeFromString<MarketResponseDto>(fixture("live_market_response.json"))

        dto.markets.forEach { mandi ->
            assertTrue("mandi name", mandi.market.isNotBlank())
            assertTrue("commodity", mandi.commodity.isNotBlank())
            assertTrue("state", mandi.state.isNotBlank())
            assertNotNull("arrival date", mandi.arrival_date)
        }
    }
}
