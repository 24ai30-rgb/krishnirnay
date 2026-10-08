package com.krishinirnay.decision

import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.decision.DecisionEngine
import com.krishinirnay.core.decision.DecisionInput
import com.krishinirnay.core.decision.MarketInsight
import com.krishinirnay.core.decision.RainOutlook
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.decision.TimingOutcome
import com.krishinirnay.core.decision.region.DefaultRuleSet
import com.krishinirnay.core.decision.region.RegionCropRuleRegistry
import com.krishinirnay.core.decision.region.VidarbhaCottonRules
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import com.krishinirnay.core.ml.IrrigationModelOutput
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FLOAT_DELTA = 0.0001f

class DecisionEngineTest {

    private fun sensors(soilMoisturePct: Float = 60f, temperatureC: Float = 25f) = SensorReading(
        soilMoisturePct = soilMoisturePct,
        temperatureC = temperatureC,
        humidityPct = 55f,
        timestamp = Instant.EPOCH,
    )

    private fun input(
        soilMoisturePct: Float = 60f,
        temperatureC: Float = 25f,
        modelOutput: IrrigationModelOutput? = null,
        diseaseResult: DiseaseResult? = null,
        deviceOnline: Boolean = true,
    ) = DecisionInput(
        sensors = sensors(soilMoisturePct, temperatureC),
        modelOutput = modelOutput,
        diseaseResult = diseaseResult,
        deviceOnline = deviceOnline,
    )

    // --- Water stress threshold table ---

    @Test
    fun `soil moisture at or above 40 percent is LOW water stress risk`() {
        val output = DecisionEngine.evaluate(input(soilMoisturePct = 40f))
        assertEquals(RiskLevel.LOW, output.waterStressRisk)
    }

    @Test
    fun `soil moisture just below 40 percent is MEDIUM water stress risk`() {
        val output = DecisionEngine.evaluate(input(soilMoisturePct = 39.9f))
        assertEquals(RiskLevel.MEDIUM, output.waterStressRisk)
    }

    @Test
    fun `soil moisture just below 20 percent is HIGH water stress risk`() {
        val output = DecisionEngine.evaluate(input(soilMoisturePct = 19.9f))
        assertEquals(RiskLevel.HIGH, output.waterStressRisk)
    }

    @Test
    fun `soil moisture at exactly 20 percent is MEDIUM not HIGH`() {
        val output = DecisionEngine.evaluate(input(soilMoisturePct = 20f))
        assertEquals(RiskLevel.MEDIUM, output.waterStressRisk)
    }

    // --- Heat threshold table ---

    @Test
    fun `temperature below 33C is LOW heat risk`() {
        val output = DecisionEngine.evaluate(input(temperatureC = 32.9f))
        assertEquals(RiskLevel.LOW, output.heatRisk)
    }

    @Test
    fun `temperature at 33C is MEDIUM heat risk`() {
        val output = DecisionEngine.evaluate(input(temperatureC = 33f))
        assertEquals(RiskLevel.MEDIUM, output.heatRisk)
    }

    @Test
    fun `temperature at 38C is HIGH heat risk`() {
        val output = DecisionEngine.evaluate(input(temperatureC = 38f))
        assertEquals(RiskLevel.HIGH, output.heatRisk)
    }

    // --- modelOutput overrides the rule-based fallback ---

    @Test
    fun `model output overrides the rule-based water stress computation`() {
        // Rule-based alone would say LOW at 60% moisture — the model says HIGH.
        val output = DecisionEngine.evaluate(
            input(soilMoisturePct = 60f, modelOutput = IrrigationModelOutput(RiskLevel.HIGH, 0.91f)),
        )
        assertEquals(RiskLevel.HIGH, output.waterStressRisk)
        assertEquals(0.91f, output.confidence, FLOAT_DELTA)
    }

    @Test
    fun `falls back to rule-based water stress when model output is null`() {
        val output = DecisionEngine.evaluate(input(soilMoisturePct = 10f, modelOutput = null))
        assertEquals(RiskLevel.HIGH, output.waterStressRisk)
        assertEquals(1f, output.confidence, FLOAT_DELTA)
    }

    // --- cropHealthRisk / overallRisk aggregation ---

    @Test
    fun `cropHealthRisk is UNKNOWN when no disease scan has been run`() {
        val output = DecisionEngine.evaluate(input(diseaseResult = null))
        assertEquals(RiskLevel.UNKNOWN, output.cropHealthRisk)
    }

    @Test
    fun `overallRisk excludes UNKNOWN cropHealthRisk from the comparison`() {
        // Healthy water/heat, no crop scan yet — overall must be LOW, not UNKNOWN.
        val output = DecisionEngine.evaluate(input(soilMoisturePct = 60f, temperatureC = 25f, diseaseResult = null))
        assertEquals(RiskLevel.LOW, output.overallRisk)
        assertEquals(RiskLevel.UNKNOWN, output.cropHealthRisk)
    }

    @Test
    fun `overallRisk is the max severity across water heat and crop health`() {
        val diseaseResult = DiseaseResult(
            label = "tomato_early_blight",
            displayName = "Tomato — Early Blight",
            confidence = 0.82f,
            riskLevel = RiskLevel.HIGH,
            modelVersion = "disease-v1",
            scannedAt = Instant.EPOCH,
        )
        // Water/heat are both LOW — a HIGH-risk disease scan should still drive overallRisk to HIGH.
        val output = DecisionEngine.evaluate(
            input(soilMoisturePct = 60f, temperatureC = 25f, diseaseResult = diseaseResult),
        )
        assertEquals(RiskLevel.HIGH, output.overallRisk)
    }

    // --- offline behavior ---

    @Test
    fun `works fully offline and flags it in reasons`() {
        val output = DecisionEngine.evaluate(input(deviceOnline = false))
        assertTrue(output.reasons.contains(ReasonOutcome.DeviceOffline))
    }

    @Test
    fun `recommendation is never blank regardless of inputs`() {
        val output = DecisionEngine.evaluate(input())
        assertTrue(EnglishStrings.textFor(output.recommendation).isNotBlank())
    }

    // --- Pest Detection integration (Phase 2.9) ---

    private fun pestResult(detected: Boolean, risk: RiskLevel) = PestResult(
        detected = detected,
        label = "aphid",
        confidence = 0.8f,
        riskLevel = risk,
        modelVersion = "pest-v1",
        scannedAt = Instant.EPOCH,
    )

    @Test
    fun `pestRisk is UNKNOWN when no pest scan has been run`() {
        val output = DecisionEngine.evaluate(input())
        assertEquals(RiskLevel.UNKNOWN, output.pestRisk)
    }

    @Test
    fun `a real pest scan drives overallRisk even when water and heat are healthy`() {
        val output = DecisionEngine.evaluate(
            DecisionInput(
                sensors = sensors(soilMoisturePct = 60f, temperatureC = 25f),
                modelOutput = null,
                diseaseResult = null,
                deviceOnline = true,
                pestResult = pestResult(detected = true, risk = RiskLevel.HIGH),
            ),
        )
        assertEquals(RiskLevel.HIGH, output.pestRisk)
        assertEquals(RiskLevel.HIGH, output.overallRisk)
        assertEquals(RecommendationOutcome.TreatPestDetected, output.recommendation)
    }

    @Test
    fun `no pest detected is a real LOW assessment not UNKNOWN`() {
        val output = DecisionEngine.evaluate(
            DecisionInput(
                sensors = sensors(),
                modelOutput = null,
                diseaseResult = null,
                deviceOnline = true,
                pestResult = pestResult(detected = false, risk = RiskLevel.LOW),
            ),
        )
        assertEquals(RiskLevel.LOW, output.pestRisk)
    }

    // --- Region/crop rule layer (Phase 2.8) ---

    @Test
    fun `RegionCropRuleRegistry resolves Vidarbha plus Cotton to VidarbhaCottonRules`() {
        assertEquals(VidarbhaCottonRules, RegionCropRuleRegistry.forRegionAndCrop("Vidarbha", "Cotton"))
        // Case/whitespace-insensitive, since farmer-entered text won't always match exactly.
        assertEquals(VidarbhaCottonRules, RegionCropRuleRegistry.forRegionAndCrop(" vidarbha ", "cotton"))
    }

    @Test
    fun `an unmodeled region plus crop falls back to DefaultRuleSet`() {
        assertEquals(DefaultRuleSet, RegionCropRuleRegistry.forRegionAndCrop("Punjab", "Wheat"))
        assertEquals(DefaultRuleSet, RegionCropRuleRegistry.forRegionAndCrop(null, null))
    }

    @Test
    fun `a pest at a vulnerable crop stage is escalated one level under VidarbhaCottonRules`() {
        val input = DecisionInput(
            sensors = sensors(),
            modelOutput = null,
            diseaseResult = null,
            deviceOnline = true,
            pestResult = pestResult(detected = true, risk = RiskLevel.MEDIUM),
            cropStage = "Flowering",
        )
        val withRegionRules = DecisionEngine.evaluate(input, ruleSet = VidarbhaCottonRules)
        val withoutRegionRules = DecisionEngine.evaluate(input, ruleSet = DefaultRuleSet)

        assertEquals(RiskLevel.HIGH, withRegionRules.pestRisk)
        assertEquals(RiskLevel.MEDIUM, withoutRegionRules.pestRisk)
    }

    @Test
    fun `a pest outside the vulnerable stage is not escalated`() {
        val output = DecisionEngine.evaluate(
            DecisionInput(
                sensors = sensors(),
                modelOutput = null,
                diseaseResult = null,
                deviceOnline = true,
                pestResult = pestResult(detected = true, risk = RiskLevel.MEDIUM),
                cropStage = "Germination",
            ),
            ruleSet = VidarbhaCottonRules,
        )
        assertEquals(RiskLevel.MEDIUM, output.pestRisk)
    }

    // --- Disease x weather integration (Phase 2.9) ---

    @Test
    fun `disease risk is escalated when rain is expected soon under high humidity`() {
        val diseaseResult = DiseaseResult(
            label = "tomato_early_blight",
            displayName = "Tomato — Early Blight",
            confidence = 0.5f,
            riskLevel = RiskLevel.MEDIUM,
            modelVersion = "disease-v1",
            scannedAt = Instant.EPOCH,
        )
        val humidInput = DecisionInput(
            sensors = SensorReading(soilMoisturePct = 60f, temperatureC = 25f, humidityPct = 80f, timestamp = Instant.EPOCH),
            modelOutput = null,
            diseaseResult = diseaseResult,
            deviceOnline = true,
            rainOutlook = RainOutlook.RAIN_EXPECTED_SOON,
        )
        val dryInput = humidInput.copy(rainOutlook = RainOutlook.DRY)

        assertEquals(RiskLevel.HIGH, DecisionEngine.evaluate(humidInput).cropHealthRisk)
        assertEquals(RiskLevel.MEDIUM, DecisionEngine.evaluate(dryInput).cropHealthRisk)
        assertTrue(DecisionEngine.evaluate(humidInput).reasons.contains(ReasonOutcome.RainExpectedSoon))
    }

    // --- Timing / expected benefit (Phase 2.7) ---

    @Test
    fun `timing is Immediate when overall risk is HIGH and NoActionNeeded when LOW`() {
        val highRiskOutput = DecisionEngine.evaluate(input(soilMoisturePct = 5f))
        val healthyOutput = DecisionEngine.evaluate(input())

        assertEquals(TimingOutcome.Immediate, highRiskOutput.timing)
        assertEquals(TimingOutcome.NoActionNeeded, healthyOutput.timing)
    }

    // --- Fertilizer fusion (Phase 4B) ---

    // TEST 2: fertilizer recommendation reaches DecisionEngine (a real parameter it
    // receives and places into the output) — computed by FertilizerAdvisor elsewhere
    // (FieldDecisionResolver), never duplicated inside the engine itself.
    @Test
    fun `a fertilizer recommendation passed in is carried through to the output unchanged`() {
        val recommendation = FertilizerRecommendation.NoActionNeeded
        val output = DecisionEngine.evaluate(input(), fertilizerRecommendation = recommendation)
        assertEquals(recommendation, output.fertilizerRecommendation)
    }

    @Test
    fun `no fertilizer recommendation passed in leaves the field null, never invented`() {
        val output = DecisionEngine.evaluate(input())
        assertNull(output.fertilizerRecommendation)
    }

    @Test
    fun `a fertilizer recommendation never influences overallRisk or recommendation`() {
        val withoutFertilizer = DecisionEngine.evaluate(input(soilMoisturePct = 5f))
        val withFertilizer = DecisionEngine.evaluate(
            input(soilMoisturePct = 5f),
            fertilizerRecommendation = FertilizerRecommendation.NoActionNeeded,
        )

        assertEquals(withoutFertilizer.overallRisk, withFertilizer.overallRisk)
        assertEquals(withoutFertilizer.recommendation, withFertilizer.recommendation)
    }

    // --- Market fusion (Phase 4D) ---

    @Test
    fun `a market insight passed in is carried through to the output unchanged`() {
        val insight = MarketInsight.PriceAvailable(
            crop = "Cotton", market = "Akola APMC", modalPricePerQuintal = 7200f,
            trend = MarketTrend.RISING, arrivalDate = "10/09/2026",
        )
        val output = DecisionEngine.evaluate(input(), marketInsight = insight)
        assertEquals(insight, output.marketInsight)
    }

    @Test
    fun `no market insight passed in leaves the field null, never invented`() {
        val output = DecisionEngine.evaluate(input())
        assertNull(output.marketInsight)
    }

    @Test
    fun `a market insight never influences overallRisk or recommendation`() {
        val withoutMarket = DecisionEngine.evaluate(input(soilMoisturePct = 5f))
        val withMarket = DecisionEngine.evaluate(
            input(soilMoisturePct = 5f),
            marketInsight = MarketInsight.PriceAvailable(
                crop = "Cotton", market = "Akola APMC", modalPricePerQuintal = 7200f,
                trend = MarketTrend.RISING, arrivalDate = "10/09/2026",
            ),
        )

        assertEquals(withoutMarket.overallRisk, withMarket.overallRisk)
        assertEquals(withoutMarket.recommendation, withMarket.recommendation)
    }
}
