package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.MarketTrend

/**
 * Structured, language-agnostic market snapshot (Phase 4D) — rendered later via
 * `core/designsystem/strings` exactly like [ReasonOutcome]/[TimingOutcome], never
 * pre-formatted text here. Computed by [com.krishinirnay.core.data.composite.FieldDecisionResolver]
 * from [com.krishinirnay.core.data.repository.MarketRepository] and passed through
 * [DecisionEngine.evaluate] — like [com.krishinirnay.core.fertilizer.FertilizerRecommendation],
 * it plays no part in [com.krishinirnay.core.data.model.DecisionOutput.overallRisk]/`recommendation`,
 * so it can never mask a real risk.
 *
 * Deliberately carries only real, already-fetched numbers ([modalPricePerQuintal], [trend]) —
 * no "favorable"/"unfavorable" judgement is computed here, since that would require a baseline
 * this system has no real data for (transport cost, farmer's own cost basis, historical price
 * distribution). Inventing that comparison would violate the same "never fabricate" rule that
 * governs weather/market prices themselves.
 */
sealed class MarketInsight {
    data class PriceAvailable(
        val crop: String,
        val market: String?,
        val modalPricePerQuintal: Float,
        val trend: MarketTrend,
        val arrivalDate: String?,
    ) : MarketInsight()

    object Unavailable : MarketInsight()
}
