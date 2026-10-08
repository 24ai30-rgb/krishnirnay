package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.decision.region.DefaultRuleSet
import com.krishinirnay.core.decision.region.RegionCropRuleSet
import com.krishinirnay.core.fertilizer.FertilizerRecommendation

/**
 * Pure Kotlin, no Android/Firebase/network imports — works fully offline
 * by construction. This is the one piece of "AI" in the app that must
 * never depend on connectivity; an optional LLM layer (`ExplanationService`)
 * wraps around [recommendation]/[reasons] elsewhere to polish the wording,
 * never to compute the risk itself.
 *
 * [ruleSet] is the Phase 2 region/crop rule layer (see `core/decision/region`) —
 * it only ever adjusts thresholds/escalation knobs the interface exposes; the
 * engine's own logic never branches on a region or crop name directly, which is
 * what keeps adding a new region+crop a new [RegionCropRuleSet], not a new `when`
 * branch here.
 *
 * [fertilizerRecommendation] (Phase 4B) is computed elsewhere (by
 * [com.krishinirnay.core.fertilizer.FertilizerAdvisor], called from
 * [com.krishinirnay.core.data.composite.FieldDecisionResolver], which has the
 * farmer-profile context this engine deliberately doesn't take on) and simply
 * passed through into the returned [DecisionOutput] — it plays no part in
 * [overallRisk]/[recommendation], so it can never mask a real risk.
 */
object DecisionEngine {

    fun evaluate(
        input: DecisionInput,
        ruleSet: RegionCropRuleSet = DefaultRuleSet,
        fertilizerRecommendation: FertilizerRecommendation? = null,
        marketInsight: MarketInsight? = null,
    ): DecisionOutput {
        val waterStressRisk = input.modelOutput?.riskLevel
            ?: waterStressRiskFromMoisture(input.sensors.soilMoisturePct, ruleSet)
        val heatRisk = heatRiskFromTemperature(input.sensors.temperatureC)
        val cropHealthRisk = cropHealthRiskFrom(input)
        val pestRisk = pestRiskFrom(input, ruleSet)

        val overallRisk = maxRisk(listOf(waterStressRisk, heatRisk, cropHealthRisk, pestRisk))
        val recommendation = buildRecommendation(waterStressRisk, heatRisk, cropHealthRisk, pestRisk)

        return DecisionOutput(
            overallRisk = overallRisk,
            waterStressRisk = waterStressRisk,
            heatRisk = heatRisk,
            cropHealthRisk = cropHealthRisk,
            recommendation = recommendation,
            confidence = input.modelOutput?.probability ?: 1f,
            reasons = buildReasons(input, waterStressRisk, heatRisk, cropHealthRisk, pestRisk),
            pestRisk = pestRisk,
            timing = buildTiming(overallRisk, heatRisk),
            expectedBenefit = buildExpectedBenefit(overallRisk),
            fertilizerRecommendation = fertilizerRecommendation,
            marketInsight = marketInsight,
        )
    }

    private fun waterStressRiskFromMoisture(soilMoisturePct: Float, ruleSet: RegionCropRuleSet): RiskLevel = when {
        soilMoisturePct < ruleSet.soilMoistureHighRiskBelowPct -> RiskLevel.HIGH
        soilMoisturePct < ruleSet.soilMoistureMediumRiskBelowPct -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    private fun heatRiskFromTemperature(temperatureC: Float): RiskLevel = when {
        temperatureC >= DecisionRules.TEMPERATURE_HIGH_RISK_AT_OR_ABOVE_C -> RiskLevel.HIGH
        temperatureC >= DecisionRules.TEMPERATURE_MEDIUM_RISK_AT_OR_ABOVE_C -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    /**
     * A real disease scan's risk, escalated one level when rain is expected soon
     * under high humidity — the exact "disease + high humidity + rain expected"
     * scenario this phase's brief asks for, since damp conditions genuinely favor
     * fungal/bacterial spread. Never escalates an UNKNOWN (no scan yet) into a
     * guessed risk.
     */
    private fun cropHealthRiskFrom(input: DecisionInput): RiskLevel {
        val base = input.diseaseResult?.riskLevel ?: RiskLevel.UNKNOWN
        if (base == RiskLevel.UNKNOWN) return base
        val humidConditionsFavorDisease = input.rainOutlook == RainOutlook.RAIN_EXPECTED_SOON &&
            input.sensors.humidityPct >= DecisionRules.HUMIDITY_DISEASE_ESCALATION_AT_OR_ABOVE_PCT
        return if (humidConditionsFavorDisease) escalate(base) else base
    }

    /**
     * A real pest scan's risk, escalated one level when the crop is currently in
     * one of [RegionCropRuleSet.pestVulnerableCropStages] — e.g. cotton at
     * flowering/boll stage, per [com.krishinirnay.core.decision.region.VidarbhaCottonRules].
     * Never escalates an UNKNOWN (no scan yet) or a "nothing detected" LOW.
     */
    private fun pestRiskFrom(input: DecisionInput, ruleSet: RegionCropRuleSet): RiskLevel {
        val result = input.pestResult ?: return RiskLevel.UNKNOWN
        if (!result.detected) return RiskLevel.LOW
        val atVulnerableStage = input.cropStage != null &&
            ruleSet.pestVulnerableCropStages.any { it.equals(input.cropStage, ignoreCase = true) }
        return if (atVulnerableStage) escalate(result.riskLevel) else result.riskLevel
    }

    private fun escalate(risk: RiskLevel): RiskLevel = when (risk) {
        RiskLevel.LOW -> RiskLevel.MEDIUM
        RiskLevel.MEDIUM -> RiskLevel.HIGH
        RiskLevel.HIGH -> RiskLevel.HIGH
        RiskLevel.UNKNOWN -> RiskLevel.UNKNOWN
    }

    /**
     * UNKNOWN is excluded from the comparison — "not a vote," never
     * "zero risk." Water stress and heat are always LOW/MEDIUM/HIGH
     * (never UNKNOWN), so this only ever drops cropHealthRisk/pestRisk
     * from the comparison when no scan has been run yet.
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
        waterStressRisk: RiskLevel,
        heatRisk: RiskLevel,
        cropHealthRisk: RiskLevel,
        pestRisk: RiskLevel,
    ): RecommendationOutcome = when {
        waterStressRisk == RiskLevel.HIGH && heatRisk == RiskLevel.HIGH -> RecommendationOutcome.IrrigateSevere
        waterStressRisk == RiskLevel.HIGH -> RecommendationOutcome.IrrigateWaterHigh
        heatRisk == RiskLevel.HIGH -> RecommendationOutcome.ShadeOrIrrigateHeat
        pestRisk == RiskLevel.HIGH -> RecommendationOutcome.TreatPestDetected
        cropHealthRisk == RiskLevel.HIGH -> RecommendationOutcome.ReviewCropHealthHigh
        waterStressRisk == RiskLevel.MEDIUM -> RecommendationOutcome.PlanIrrigationSoon
        heatRisk == RiskLevel.MEDIUM -> RecommendationOutcome.MonitorTemperature
        pestRisk == RiskLevel.MEDIUM -> RecommendationOutcome.MonitorPestRisk
        cropHealthRisk == RiskLevel.MEDIUM -> RecommendationOutcome.ReviewCropHealthModerate
        waterStressRisk == RiskLevel.LOW && heatRisk == RiskLevel.LOW -> RecommendationOutcome.HealthyRange
        else -> RecommendationOutcome.NotEnoughData
    }

    private fun buildTiming(overallRisk: RiskLevel, heatRisk: RiskLevel): TimingOutcome = when {
        overallRisk == RiskLevel.HIGH -> TimingOutcome.Immediate
        overallRisk == RiskLevel.MEDIUM && heatRisk == RiskLevel.MEDIUM -> TimingOutcome.ThisEvening
        overallRisk == RiskLevel.MEDIUM -> TimingOutcome.Within24Hours
        overallRisk == RiskLevel.LOW -> TimingOutcome.NoActionNeeded
        else -> TimingOutcome.NoActionNeeded
    }

    private fun buildExpectedBenefit(overallRisk: RiskLevel): BenefitOutcome = when (overallRisk) {
        RiskLevel.HIGH -> BenefitOutcome.PreventCropLoss
        RiskLevel.MEDIUM -> BenefitOutcome.ImprovedYield
        RiskLevel.LOW -> BenefitOutcome.HealthyGrowthContinues
        RiskLevel.UNKNOWN -> BenefitOutcome.Unknown
    }

    private fun buildReasons(
        input: DecisionInput,
        waterStressRisk: RiskLevel,
        heatRisk: RiskLevel,
        cropHealthRisk: RiskLevel,
        pestRisk: RiskLevel,
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

        reasons += if (pestRisk == RiskLevel.UNKNOWN) {
            ReasonOutcome.PestNotAssessed
        } else {
            ReasonOutcome.PestAssessed(input.pestResult?.label, pestRisk)
        }

        if (input.rainOutlook == RainOutlook.RAIN_EXPECTED_SOON) {
            reasons += ReasonOutcome.RainExpectedSoon
        }

        if (!input.deviceOnline) {
            reasons += ReasonOutcome.DeviceOffline
        }

        return reasons
    }
}
