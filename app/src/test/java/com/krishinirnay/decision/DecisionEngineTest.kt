package com.krishinirnay.decision

import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.decision.DecisionEngine
import com.krishinirnay.core.decision.DecisionInput
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.core.ml.IrrigationModelOutput
import java.time.Instant
import org.junit.Assert.assertEquals
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
}
