package com.krishinirnay.feature.insights

import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.decision.RecommendationOutcome

data class InsightsUiState(
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val confidencePct: Int = 0,
    val recommendation: RecommendationOutcome? = null,
    val polishedText: String? = null,
    val isPolished: Boolean = false,
    val reasons: List<ReasonOutcome> = emptyList(),
)
