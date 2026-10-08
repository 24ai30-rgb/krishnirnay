package com.krishinirnay.core.designsystem.strings

import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.decision.BenefitOutcome
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.decision.TimingOutcome

/** Localized name for a risk level — reuses the same copy shown on [com.krishinirnay.core.designsystem.components.RiskBadge]. */
fun AppStrings.nameFor(risk: RiskLevel): String = when (risk) {
    RiskLevel.LOW -> riskLow
    RiskLevel.MEDIUM -> riskMedium
    RiskLevel.HIGH -> riskHigh
    RiskLevel.UNKNOWN -> riskNotAssessed
}

fun AppStrings.textFor(outcome: RecommendationOutcome): String = when (outcome) {
    RecommendationOutcome.IrrigateSevere -> recommendationIrrigateSevere
    RecommendationOutcome.IrrigateWaterHigh -> recommendationIrrigateWaterHigh
    RecommendationOutcome.ShadeOrIrrigateHeat -> recommendationShadeOrIrrigateHeat
    RecommendationOutcome.ReviewCropHealthHigh -> recommendationReviewCropHealthHigh
    RecommendationOutcome.PlanIrrigationSoon -> recommendationPlanIrrigationSoon
    RecommendationOutcome.MonitorTemperature -> recommendationMonitorTemperature
    RecommendationOutcome.ReviewCropHealthModerate -> recommendationReviewCropHealthModerate
    RecommendationOutcome.HealthyRange -> recommendationHealthyRange
    RecommendationOutcome.NotEnoughData -> recommendationNotEnoughData
    RecommendationOutcome.TreatPestDetected -> recommendationTreatPestDetected
    RecommendationOutcome.MonitorPestRisk -> recommendationMonitorPestRisk
}

fun AppStrings.textFor(reason: ReasonOutcome): String = when (reason) {
    is ReasonOutcome.SoilMoisture -> when (reason.risk) {
        RiskLevel.HIGH -> String.format(reasonSoilMoistureHighTemplate, reason.pct)
        RiskLevel.MEDIUM -> String.format(reasonSoilMoistureMediumTemplate, reason.pct)
        else -> String.format(reasonSoilMoistureHealthyTemplate, reason.pct)
    }
    is ReasonOutcome.ModelPrediction -> String.format(reasonModelPredictionTemplate, nameFor(reason.risk), reason.confidencePct)
    is ReasonOutcome.Temperature -> when (reason.risk) {
        RiskLevel.HIGH -> String.format(reasonTemperatureHighTemplate, reason.celsius)
        RiskLevel.MEDIUM -> String.format(reasonTemperatureMediumTemplate, reason.celsius)
        else -> String.format(reasonTemperatureHealthyTemplate, reason.celsius)
    }
    ReasonOutcome.CropHealthNotAssessed -> reasonCropHealthNotAssessed
    is ReasonOutcome.CropHealthAssessed -> String.format(
        reasonCropHealthAssessedTemplate,
        reason.diseaseName ?: "—",
        nameFor(reason.risk),
    )
    ReasonOutcome.DeviceOffline -> reasonDeviceOffline
    ReasonOutcome.PestNotAssessed -> reasonPestNotAssessed
    is ReasonOutcome.PestAssessed -> String.format(
        reasonPestAssessedTemplate,
        reason.pestName ?: "—",
        nameFor(reason.risk),
    )
    ReasonOutcome.RainExpectedSoon -> reasonRainExpectedSoon
}

fun AppStrings.textFor(timing: TimingOutcome): String = when (timing) {
    TimingOutcome.Immediate -> timingImmediate
    TimingOutcome.ThisEvening -> timingThisEvening
    TimingOutcome.Within24Hours -> timingWithin24Hours
    TimingOutcome.Within3Days -> timingWithin3Days
    TimingOutcome.NoActionNeeded -> timingNoActionNeeded
}

fun AppStrings.textFor(benefit: BenefitOutcome): String = when (benefit) {
    BenefitOutcome.PreventCropLoss -> benefitPreventCropLoss
    BenefitOutcome.ImprovedYield -> benefitImprovedYield
    BenefitOutcome.HealthyGrowthContinues -> benefitHealthyGrowthContinues
    BenefitOutcome.Unknown -> benefitUnknown
}
