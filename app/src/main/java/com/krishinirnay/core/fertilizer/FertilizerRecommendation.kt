package com.krishinirnay.core.fertilizer

import com.krishinirnay.core.decision.TimingOutcome

enum class NutrientDeficiency { NITROGEN, PHOSPHORUS, POTASSIUM }

enum class FertilizerType { UREA, DAP, MOP }

/**
 * Structured, language-agnostic output — same "never pre-formatted text"
 * discipline as [com.krishinirnay.core.decision.DecisionEngine]'s
 * `RecommendationOutcome`, rendered via `AppStrings.textFor` (see
 * `core/designsystem/strings/FertilizerText.kt`).
 *
 * [Recommended.quantityRangeKgPerAcre] is a wide, explicitly-indicative range
 * from generic agronomy rules of thumb, not a calibrated dose — the safety
 * note shown alongside it always tells the farmer to confirm with a real
 * soil-test card or local agriculture extension office before applying.
 * There is deliberately no cost estimate: no real fertilizer/market price
 * source is wired up yet (see `MarketRepository`), and a plausible-looking
 * ₹ figure with no real pricing behind it would be exactly the kind of
 * fabricated data this system must avoid.
 */
sealed interface FertilizerRecommendation {
    data class Recommended(
        val nutrient: NutrientDeficiency,
        val fertilizerType: FertilizerType,
        val quantityRangeKgPerAcre: IntRange,
        val timing: TimingOutcome,
    ) : FertilizerRecommendation

    data object NoActionNeeded : FertilizerRecommendation

    data class InsufficientData(val missingFields: List<String>) : FertilizerRecommendation
}
