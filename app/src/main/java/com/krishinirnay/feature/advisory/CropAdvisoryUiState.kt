package com.krishinirnay.feature.advisory

import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.decision.RecommendationOutcome

enum class AdvisoryTaskType {
    IRRIGATE,
    PEST_CONTROL,
    FERTILIZER,
}

data class AdvisoryTask(
    val type: AdvisoryTaskType,
    val detail: String,
)

data class CropAdvisoryUiState(
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val confidencePct: Int = 0,
    val aiAdvice: RecommendationOutcome? = null,
    val isOnline: Boolean = true,
    val tasks: List<AdvisoryTask> = emptyList(),
)
