package com.krishinirnay.core.fertilizer

import com.krishinirnay.core.decision.RainOutlook
import com.krishinirnay.core.decision.TimingOutcome

/**
 * Pure Kotlin, offline, deterministic — same discipline as
 * [com.krishinirnay.core.decision.DecisionEngine]: no network/Android imports,
 * so it works with zero connectivity and is trivially unit-testable.
 *
 * ponytail: [NITROGEN_LOW_BELOW_PPM]/[PHOSPHORUS_LOW_BELOW_PPM]/[POTASSIUM_LOW_BELOW_PPM]
 * and the quantity ranges in [recommend] are generic, widely-cited rule-of-thumb
 * figures, not a calibrated regional model — the ceiling here is real soil-test
 * data. Every [FertilizerRecommendation.Recommended] result must be shown with a
 * safety note pointing the farmer at a real soil-test card before this can be
 * tightened into a precise dose.
 */
object FertilizerAdvisor {

    // Generic "low" thresholds for available soil N/P/K in ppm — indicative only.
    const val NITROGEN_LOW_BELOW_PPM = 40f
    const val PHOSPHORUS_LOW_BELOW_PPM = 10f
    const val POTASSIUM_LOW_BELOW_PPM = 100f

    fun recommend(input: FertilizerInput): FertilizerRecommendation {
        val missing = buildList {
            if (input.nitrogenPpm == null) add("nitrogen")
            if (input.phosphorusPpm == null) add("phosphorus")
            if (input.potassiumPpm == null) add("potassium")
        }

        if (missing.size == 3) {
            return FertilizerRecommendation.InsufficientData(missing)
        }

        // Checked in N -> P -> K order — a deterministic, standard agronomy
        // convention, not a claim that nitrogen is always the priority nutrient.
        val nitrogenLow = input.nitrogenPpm != null && input.nitrogenPpm < NITROGEN_LOW_BELOW_PPM
        val phosphorusLow = input.phosphorusPpm != null && input.phosphorusPpm < PHOSPHORUS_LOW_BELOW_PPM
        val potassiumLow = input.potassiumPpm != null && input.potassiumPpm < POTASSIUM_LOW_BELOW_PPM

        return when {
            nitrogenLow -> recommended(NutrientDeficiency.NITROGEN, FertilizerType.UREA, 20..40, input.rainOutlook)
            phosphorusLow -> recommended(NutrientDeficiency.PHOSPHORUS, FertilizerType.DAP, 15..25, input.rainOutlook)
            potassiumLow -> recommended(NutrientDeficiency.POTASSIUM, FertilizerType.MOP, 10..20, input.rainOutlook)
            else -> FertilizerRecommendation.NoActionNeeded
        }
    }

    private fun recommended(
        nutrient: NutrientDeficiency,
        fertilizerType: FertilizerType,
        quantityRangeKgPerAcre: IntRange,
        rainOutlook: RainOutlook,
    ) = FertilizerRecommendation.Recommended(
        nutrient = nutrient,
        fertilizerType = fertilizerType,
        quantityRangeKgPerAcre = quantityRangeKgPerAcre,
        timing = timingFor(rainOutlook),
    )

    /**
     * Accounts for rain rather than ignoring it (Phase 4B): applying fertilizer right
     * before rain risks washing the nutrient away before the crop benefits — standard,
     * widely-taught agronomy, not a region-specific guess. [RainOutlook.UNKNOWN] (no
     * weather signal — e.g. weather is UNAVAILABLE) stays on the conservative default
     * rather than ever assuming dry conditions.
     */
    private fun timingFor(rainOutlook: RainOutlook): TimingOutcome = when (rainOutlook) {
        RainOutlook.RAIN_EXPECTED_SOON -> TimingOutcome.Within3Days
        RainOutlook.DRY -> TimingOutcome.Within24Hours
        RainOutlook.UNKNOWN -> TimingOutcome.Within3Days
    }
}
