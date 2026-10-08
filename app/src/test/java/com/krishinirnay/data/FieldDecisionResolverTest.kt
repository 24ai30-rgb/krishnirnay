package com.krishinirnay.data

import com.krishinirnay.core.data.composite.FieldDecisionResolver
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.decision.MarketInsight
import com.krishinirnay.core.decision.TimingOutcome
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import com.krishinirnay.core.fertilizer.NutrientDeficiency
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the Phase 3A fix: region rules, crop stage, and rain outlook actually reach
 * [com.krishinirnay.core.decision.DecisionEngine] through the real production pathway
 * (this class), not just inside [com.krishinirnay.decision.DecisionEngineTest]'s direct
 * unit tests. Before this fix, `MockFieldStateRepositoryImpl`/`LiveFieldStateRepositoryImpl`
 * called `DecisionEngine.evaluate` directly and always defaulted these three inputs.
 */
class FieldDecisionResolverTest {

    private fun sensors(
        soilMoisturePct: Float = 60f,
        temperatureC: Float = 25f,
        humidityPct: Float = 55f,
        nitrogenPpm: Float? = null,
        phosphorusPpm: Float? = null,
        potassiumPpm: Float? = null,
    ) = SensorReading(
        soilMoisturePct = soilMoisturePct,
        temperatureC = temperatureC,
        humidityPct = humidityPct,
        nitrogenPpm = nitrogenPpm,
        phosphorusPpm = phosphorusPpm,
        potassiumPpm = potassiumPpm,
        timestamp = Instant.EPOCH,
    )

    private fun pest(risk: RiskLevel) = PestResult(
        detected = true,
        label = "aphid",
        confidence = 0.8f,
        riskLevel = risk,
        modelVersion = "pest-v1",
        scannedAt = Instant.EPOCH,
    )

    private fun profile(state: String, crop: String, stage: String) = FarmerProfile(
        name = "Test",
        phone = "0",
        location = "",
        farmSizeAcres = 1f,
        crops = listOf(crop),
        seedlingStage = stage,
        farmLocation = FarmLocation(state = state),
    )

    private fun weather(rainChancePct: Int, status: DataSourceStatus = DataSourceStatus.LIVE) = WeatherState(
        locationLabel = "",
        currentTempC = 28,
        condition = WeatherCondition.CLOUDY,
        windKph = 10,
        humidityPct = 50,
        rainChancePct = rainChancePct,
        rainInHoursLabel = "24",
        daily = emptyList(),
        status = status,
    )

    private fun unavailableMarket(crop: String = "") = MarketState(
        crop = crop,
        market = null,
        location = null,
        currentPricePerQuintal = null,
        minPricePerQuintal = null,
        maxPricePerQuintal = null,
        averagePricePerQuintal = null,
        fetchedAt = null,
        source = null,
        status = DataSourceStatus.UNAVAILABLE,
    )

    private fun resolver(
        profile: FarmerProfile,
        weather: WeatherState,
        market: MarketState = unavailableMarket(),
    ): FieldDecisionResolver {
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profile)
        val weatherRepository = mockk<WeatherRepository>()
        every { weatherRepository.weather } returns MutableStateFlow(weather)
        val marketRepository = mockk<MarketRepository>()
        every { marketRepository.market } returns MutableStateFlow(market)
        return FieldDecisionResolver(profileRepository, weatherRepository, marketRepository)
    }

    @Test
    fun `region rules are actually invoked - Vidarbha Cotton escalates a pest at Flowering`() {
        val withRegion = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(), null, null, pest(RiskLevel.MEDIUM), true)

        val withoutRegion = resolver(profile(state = "Punjab", crop = "Wheat", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(), null, null, pest(RiskLevel.MEDIUM), true)

        assertEquals(RiskLevel.HIGH, withRegion.pestRisk)
        assertEquals(RiskLevel.MEDIUM, withoutRegion.pestRisk)
    }

    @Test
    fun `crop stage is actually passed - no escalation outside the vulnerable stage`() {
        val output = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Germination"), weather(rainChancePct = 0))
            .evaluate(sensors(), null, null, pest(RiskLevel.MEDIUM), true)

        assertEquals(RiskLevel.MEDIUM, output.pestRisk)
    }

    @Test
    fun `rain outlook affects the decision when a disease scan exists under high humidity`() {
        val humidSensors = SensorReading(soilMoisturePct = 60f, temperatureC = 25f, humidityPct = 80f, timestamp = Instant.EPOCH)
        val disease = com.krishinirnay.core.data.model.DiseaseResult(
            label = "x", displayName = "X", confidence = 0.5f, riskLevel = RiskLevel.MEDIUM,
            modelVersion = "v", scannedAt = Instant.EPOCH,
        )

        val rainy = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 90))
            .evaluate(humidSensors, null, disease, null, true)
        val dry = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 5))
            .evaluate(humidSensors, null, disease, null, true)

        assertEquals(RiskLevel.HIGH, rainy.cropHealthRisk)
        assertEquals(RiskLevel.MEDIUM, dry.cropHealthRisk)
    }

    @Test
    fun `unavailable weather never invents a rain outlook`() {
        val disease = com.krishinirnay.core.data.model.DiseaseResult(
            label = "x", displayName = "X", confidence = 0.5f, riskLevel = RiskLevel.MEDIUM,
            modelVersion = "v", scannedAt = Instant.EPOCH,
        )
        val humidSensors = SensorReading(soilMoisturePct = 60f, temperatureC = 25f, humidityPct = 80f, timestamp = Instant.EPOCH)

        // rainChancePct is a placeholder 0 inside an UNAVAILABLE WeatherState — must not be read as "DRY".
        val output = resolver(
            profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"),
            weather(rainChancePct = 0, status = DataSourceStatus.UNAVAILABLE),
        ).evaluate(humidSensors, null, disease, null, true)

        // No escalation, since RainOutlook.UNKNOWN never triggers the humid+rain-expected branch.
        assertEquals(RiskLevel.MEDIUM, output.cropHealthRisk)
    }

    @Test
    fun `an unmodeled region and crop falls back to the default rule set, no escalation invented`() {
        val output = resolver(profile(state = "Kerala", crop = "Coconut", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(), null, null, pest(RiskLevel.MEDIUM), true)

        assertEquals(RiskLevel.MEDIUM, output.pestRisk)
    }

    // --- Phase 4B: fertilizer fusion ---

    // TEST 1 / 3: fertilizer recommendation reaches FieldDecisionResolver and appears
    // in the final DecisionOutput (this class *is* the real production pathway).
    @Test
    fun `a nitrogen-deficient soil reading produces a real fertilizer recommendation in the final DecisionOutput`() {
        val output = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(nitrogenPpm = 10f, phosphorusPpm = 60f, potassiumPpm = 200f), null, null, null, true)

        val recommendation = output.fertilizerRecommendation
        assertTrue(recommendation is FertilizerRecommendation.Recommended)
        assertEquals(NutrientDeficiency.NITROGEN, (recommendation as FertilizerRecommendation.Recommended).nutrient)
    }

    // TEST 6: a HIGH disease risk is never masked by an accompanying fertilizer recommendation.
    @Test
    fun `a HIGH disease risk is preserved alongside a real fertilizer recommendation`() {
        val disease = DiseaseResult(
            label = "x", displayName = "X", confidence = 0.9f, riskLevel = RiskLevel.HIGH,
            modelVersion = "v", scannedAt = Instant.EPOCH,
        )
        val output = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(nitrogenPpm = 10f), null, disease, null, true)

        assertEquals(RiskLevel.HIGH, output.overallRisk)
        assertTrue(output.fertilizerRecommendation is FertilizerRecommendation.Recommended)
    }

    // TEST 7: same for a HIGH pest risk.
    @Test
    fun `a HIGH pest risk is preserved alongside a real fertilizer recommendation`() {
        val output = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(nitrogenPpm = 10f), null, null, pest(RiskLevel.HIGH), true)

        assertEquals(RiskLevel.HIGH, output.overallRisk)
        assertTrue(output.fertilizerRecommendation is FertilizerRecommendation.Recommended)
    }

    // TEST 8: weather UNAVAILABLE must never make the fertilizer timing assume dry weather.
    @Test
    fun `fertilizer timing never assumes dry weather when weather is UNAVAILABLE`() {
        val output = resolver(
            profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"),
            weather(rainChancePct = 0, status = DataSourceStatus.UNAVAILABLE),
        ).evaluate(sensors(nitrogenPpm = 10f), null, null, null, true)

        val recommendation = output.fertilizerRecommendation as FertilizerRecommendation.Recommended
        assertEquals(TimingOutcome.Within3Days, recommendation.timing)
    }

    // TEST 9: no NPK reading at all (InsufficientData) must never affect the rest of the decision.
    @Test
    fun `insufficient fertilizer data never affects the rest of the decision`() {
        val output = resolver(profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"), weather(rainChancePct = 0))
            .evaluate(sensors(soilMoisturePct = 10f), null, null, null, true) // critical moisture, no NPK at all

        assertEquals(RiskLevel.HIGH, output.overallRisk) // water stress still computed correctly
        assertTrue(output.fertilizerRecommendation is FertilizerRecommendation.InsufficientData)
    }

    // --- Phase 4D: market fusion ---

    private fun realMarket(price: Float, trend: MarketTrend = MarketTrend.STABLE) = MarketState(
        crop = "Cotton",
        market = "Akola APMC",
        location = "Akola, Maharashtra",
        currentPricePerQuintal = price,
        minPricePerQuintal = price - 200f,
        maxPricePerQuintal = price + 200f,
        averagePricePerQuintal = null,
        fetchedAt = Instant.EPOCH,
        source = "data.gov.in (AGMARKNET)",
        status = DataSourceStatus.LIVE,
        arrivalDate = "10/09/2026",
        trend = trend,
    )

    // A real mandi price reaches the final DecisionOutput through this same resolver.
    @Test
    fun `a real market price produces a MarketInsight in the final DecisionOutput`() {
        val output = resolver(
            profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"),
            weather(rainChancePct = 0),
            market = realMarket(7200f, MarketTrend.RISING),
        ).evaluate(sensors(), null, null, null, true)

        val insight = output.marketInsight
        assertTrue(insight is MarketInsight.PriceAvailable)
        insight as MarketInsight.PriceAvailable
        assertEquals(7200f, insight.modalPricePerQuintal)
        assertEquals(MarketTrend.RISING, insight.trend)
    }

    // No market data ever fabricates a price.
    @Test
    fun `unavailable market data never fabricates a price`() {
        val output = resolver(
            profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"),
            weather(rainChancePct = 0),
            market = unavailableMarket(crop = "Cotton"),
        ).evaluate(sensors(), null, null, null, true)

        assertEquals(MarketInsight.Unavailable, output.marketInsight)
    }

    // A HIGH disease risk is never masked by an accompanying market insight.
    @Test
    fun `a HIGH disease risk is preserved alongside a real market insight`() {
        val disease = DiseaseResult(
            label = "x", displayName = "X", confidence = 0.9f, riskLevel = RiskLevel.HIGH,
            modelVersion = "v", scannedAt = Instant.EPOCH,
        )
        val output = resolver(
            profile(state = "Vidarbha", crop = "Cotton", stage = "Flowering"),
            weather(rainChancePct = 0),
            market = realMarket(7200f),
        ).evaluate(sensors(), null, disease, null, true)

        assertEquals(RiskLevel.HIGH, output.overallRisk)
        assertTrue(output.marketInsight is MarketInsight.PriceAvailable)
    }
}
