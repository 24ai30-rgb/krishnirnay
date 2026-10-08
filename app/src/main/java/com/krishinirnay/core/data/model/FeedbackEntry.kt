package com.krishinirnay.core.data.model

import java.time.Instant

enum class FeedbackAction { YES, NO, PARTIALLY }

enum class FeedbackResult {
    CROP_IMPROVED,
    NO_CHANGE,
    CROP_WORSE,
    YIELD_IMPROVED,
    YIELD_REDUCED,
    PEST_REDUCED,
    DISEASE_REDUCED,
    OTHER,
}

/**
 * One farmer's response to "Did you follow this recommendation?" / "What
 * happened?" — stored locally (see FeedbackRepository) for later analytics
 * and model improvement. Never fed back into DecisionEngine's deterministic
 * rules automatically; a real ML retraining pipeline would consume this
 * later as training/evaluation data, not a live rule.
 */
data class FeedbackEntry(
    val id: String,
    val timestamp: Instant,
    val crop: String?,
    val cropStage: String?,
    /** The structured RecommendationOutcome name (e.g. "IrrigateWaterHigh") the farmer was reacting to. */
    val recommendation: String,
    val actionTaken: FeedbackAction,
    val result: FeedbackResult? = null,
    val notes: String? = null,
)
