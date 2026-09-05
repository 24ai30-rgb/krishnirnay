package com.krishinirnay.core.data.model

import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.decision.ReasonOutcome

/**
 * Produced by [com.krishinirnay.core.decision.DecisionEngine.evaluate] —
 * pure, offline, rule-based. [recommendation]/[reasons] are structured,
 * language-agnostic data (never pre-formatted text — see
 * core/designsystem/strings/DecisionText.kt for rendering), shown
 * immediately and are what an optional LLM "explanation polish" layer
 * wraps around, never replaces.
 */
data class DecisionOutput(
    val overallRisk: RiskLevel,
    val waterStressRisk: RiskLevel,
    val heatRisk: RiskLevel,
    val cropHealthRisk: RiskLevel,
    val recommendation: RecommendationOutcome,
    val confidence: Float,
    val reasons: List<ReasonOutcome>,
)
