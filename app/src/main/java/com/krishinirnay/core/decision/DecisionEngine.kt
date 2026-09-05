package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.RiskLevel

/**
 * Pure Kotlin, no Android/Firebase/network imports — works fully offline
 * by construction. This is the one piece of "AI" in the app that must
 * never depend on connectivity; an optional LLM layer (`ExplanationService`)
 * wraps around [recommendation]/[reasons] elsewhere to polish the wording,
 * never to compute the risk itself.
 *
 * NPK/pH thresholds in [DecisionRules] are defined but not yet consumed
 * here — [DecisionOutput] has no dedicated slot for them, and Phase 1
 * hardware never populates those readings. Wiring them in is a Phase 2
 * change to this file plus [DecisionOutput]'s shape, not a change here
 * alone.
 */
object DecisionEngine {

    fun evaluate(input: DecisionInput): DecisionOutput {
        val waterStressRisk = input.modelOutput?.riskLevel
            ?: waterStressRiskFromMoisture(input.sensors.soilMoisturePct)
        val heatRisk = heatRiskFromTemperature(input.sensors.temperatureC)
        val cropHealthRisk = input.diseaseResult?.riskLevel ?: RiskLevel.UNKNOWN

        val overallRisk = maxRisk(listOf(waterStressRisk, heatRisk, cropHealthRisk))

        return DecisionOutput(
            overallRisk = overallRisk,
            waterStressRisk = waterStressRisk,
            heatRisk = heatRisk,
            cropHealthRisk = cropHealthRisk,
            recommendation = buildRecommendation(overallRisk, waterStressRisk, heatRisk),
            confidence = input.modelOutput?.probability ?: 1f,
            reasons = buildReasons(input, waterStressRisk, heatRisk, cropHealthRisk),
        )
    }

    private fun waterStressRiskFromMoisture(soilMoisturePct: Float): RiskLevel = when {
        soilMoisturePct < DecisionRules.SOIL_MOISTURE_HIGH_RISK_BELOW_PCT -> RiskLevel.HIGH
        soilMoisturePct < DecisionRules.SOIL_MOISTURE_MEDIUM_RISK_BELOW_PCT -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    private fun heatRiskFromTemperature(temperatureC: Float): RiskLevel = when {
        temperatureC >= DecisionRules.TEMPERATURE_HIGH_RISK_AT_OR_ABOVE_C -> RiskLevel.HIGH
        temperatureC >= DecisionRules.TEMPERATURE_MEDIUM_RISK_AT_OR_ABOVE_C -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    /**
     * UNKNOWN is excluded from the comparison — "not a vote," never
     * "zero risk." Water stress and heat are always LOW/MEDIUM/HIGH
     * (never UNKNOWN), so this only ever drops cropHealthRisk from the
     * comparison when no scan has been run yet.
     */
    private fun maxRisk(risks: List<RiskLevel>): RiskLevel {
        val ranked = risks.filter { it != RiskLevel.UNKNOWN }
        return ranked.maxByOrNull(::severityRank) ?: RiskLevel.UNKNOWN
    }

    private fun severityRank(risk: RiskLevel): Int = when (risk) {
        RiskLevel.LOW -> 0
        RiskLevel.MEDIUM -> 1
        RiskLevel.HIGH -> 2
        RiskLevel.UNKNOWN -> -1
    }

    private fun buildRecommendation(
        overallRisk: RiskLevel,
        waterStressRisk: RiskLevel,
        heatRisk: RiskLevel,
    ): RecommendationOutcome = when (overallRisk) {
        RiskLevel.HIGH -> when {
            waterStressRisk == RiskLevel.HIGH && heatRisk == RiskLevel.HIGH -> RecommendationOutcome.IrrigateSevere
            waterStressRisk == RiskLevel.HIGH -> RecommendationOutcome.IrrigateWaterHigh
            heatRisk == RiskLevel.HIGH -> RecommendationOutcome.ShadeOrIrrigateHeat
            else -> RecommendationOutcome.ReviewCropHealthHigh
        }
        RiskLevel.MEDIUM -> when {
            waterStressRisk == RiskLevel.MEDIUM -> RecommendationOutcome.PlanIrrigationSoon
            heatRisk == RiskLevel.MEDIUM -> RecommendationOutcome.MonitorTemperature
            else -> RecommendationOutcome.ReviewCropHealthModerate
        }
        RiskLevel.LOW -> RecommendationOutcome.HealthyRange
        RiskLevel.UNKNOWN -> RecommendationOutcome.NotEnoughData
    }

    private fun buildReasons(
        input: DecisionInput,
        waterStressRisk: RiskLevel,
        heatRisk: RiskLevel,
        cropHealthRisk: RiskLevel,
    ): List<ReasonOutcome> {
        val reasons = mutableListOf<ReasonOutcome>()
        val sensors = input.sensors

        reasons += ReasonOutcome.SoilMoisture(sensors.soilMoisturePct, waterStressRisk)

        input.modelOutput?.let { modelOutput ->
            reasons += ReasonOutcome.ModelPrediction(modelOutput.riskLevel, (modelOutput.probability * 100).toInt())
        }

        reasons += ReasonOutcome.Temperature(sensors.temperatureC, heatRisk)

        reasons += if (cropHealthRisk == RiskLevel.UNKNOWN) {
            ReasonOutcome.CropHealthNotAssessed
        } else {
            ReasonOutcome.CropHealthAssessed(input.diseaseResult?.displayName, cropHealthRisk)
        }

        if (!input.deviceOnline) {
            reasons += ReasonOutcome.DeviceOffline
        }

        return reasons
    }
}
