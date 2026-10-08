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

    // Pest Detection integration (Phase 2.9)
    data object TreatPestDetected : RecommendationOutcome
    data object MonitorPestRisk : RecommendationOutcome
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

    // Pest Detection integration (Phase 2.9)
    data object PestNotAssessed : ReasonOutcome
    data class PestAssessed(val pestName: String?, val risk: RiskLevel) : ReasonOutcome
    data object RainExpectedSoon : ReasonOutcome
}

/**
 * WHEN the farmer should act — structured like [RecommendationOutcome] so it
 * stays translatable rather than baking a language into [DecisionEngine].
 */
sealed interface TimingOutcome {
    data object Immediate : TimingOutcome
    data object ThisEvening : TimingOutcome
    data object Within24Hours : TimingOutcome
    data object Within3Days : TimingOutcome
    data object NoActionNeeded : TimingOutcome
}

/**
 * WHAT the farmer can expect if they follow [RecommendationOutcome] — a coarse,
 * honest signal, never a precise yield/₹ number the engine has no basis for.
 */
sealed interface BenefitOutcome {
    data object PreventCropLoss : BenefitOutcome
    data object ImprovedYield : BenefitOutcome
    data object HealthyGrowthContinues : BenefitOutcome
    data object Unknown : BenefitOutcome
}
