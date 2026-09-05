package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.RiskLevel

/**
 * Structured, language-agnostic recommendation produced by [DecisionEngine] —
 * kept as data, not pre-formatted text, so it stays translatable. Rendered to
 * a display string via `AppStrings.textFor` (core/designsystem/strings/DecisionText.kt).
 */
sealed interface RecommendationOutcome {
    data object IrrigateSevere : RecommendationOutcome
    data object IrrigateWaterHigh : RecommendationOutcome
    data object ShadeOrIrrigateHeat : RecommendationOutcome
    data object ReviewCropHealthHigh : RecommendationOutcome
    data object PlanIrrigationSoon : RecommendationOutcome
    data object MonitorTemperature : RecommendationOutcome
    data object ReviewCropHealthModerate : RecommendationOutcome
    data object HealthyRange : RecommendationOutcome
    data object NotEnoughData : RecommendationOutcome
}

/**
 * Structured, language-agnostic reason produced by [DecisionEngine] — same
 * rationale as [RecommendationOutcome]. Rendered via `AppStrings.textFor`.
 */
sealed interface ReasonOutcome {
    data class SoilMoisture(val pct: Float, val risk: RiskLevel) : ReasonOutcome
    data class ModelPrediction(val risk: RiskLevel, val confidencePct: Int) : ReasonOutcome
    data class Temperature(val celsius: Float, val risk: RiskLevel) : ReasonOutcome
    data object CropHealthNotAssessed : ReasonOutcome
    data class CropHealthAssessed(val diseaseName: String?, val risk: RiskLevel) : ReasonOutcome
    data object DeviceOffline : ReasonOutcome
}
