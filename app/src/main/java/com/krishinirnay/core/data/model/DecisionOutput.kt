package com.krishinirnay.core.data.model

import com.krishinirnay.core.decision.BenefitOutcome
import com.krishinirnay.core.decision.MarketInsight
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.decision.TimingOutcome
import com.krishinirnay.core.fertilizer.FertilizerRecommendation

/**
 * Produced by [com.krishinirnay.core.decision.DecisionEngine.evaluate] —
 * pure, offline, rule-based. [recommendation]/[reasons]/[timing]/[expectedBenefit]
 * are structured, language-agnostic data (never pre-formatted text — see
 * core/designsystem/strings/DecisionText.kt for rendering), shown
 * immediately and are what an optional LLM "explanation polish" layer
 * wraps around, never replaces.
 *
 * [fertilizerRecommendation] (Phase 4B) is computed by
 * [com.krishinirnay.core.fertilizer.FertilizerAdvisor] and passed through by
 * [com.krishinirnay.core.data.composite.FieldDecisionResolver] — it never
 * participates in [overallRisk]/[recommendation]'s computation, so a fertilizer
 * suggestion can never mask a genuine pest/disease/water/heat HIGH.
 *
 * [marketInsight] (Phase 4D) is likewise a structurally inert pass-through of the
 * farmer's real mandi snapshot — never part of [overallRisk]/[recommendation].
 */
data class DecisionOutput(
    val overallRisk: RiskLevel,
    val waterStressRisk: RiskLevel,
    val heatRisk: RiskLevel,
    val cropHealthRisk: RiskLevel,
    val recommendation: RecommendationOutcome,
    val confidence: Float,
    val reasons: List<ReasonOutcome>,
    val pestRisk: RiskLevel = RiskLevel.UNKNOWN,
    val timing: TimingOutcome = TimingOutcome.NoActionNeeded,
    val expectedBenefit: BenefitOutcome = BenefitOutcome.Unknown,
    val fertilizerRecommendation: FertilizerRecommendation? = null,
    val marketInsight: MarketInsight? = null,
)
