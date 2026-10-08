package com.krishinirnay.core.data.model

import java.time.Instant

/**
 * Domain-level result of a real Pest Detection scan (YOLOv8, `POST /v1/predict/pest`)
 * — mirrors [DiseaseResult]'s role so [com.krishinirnay.core.decision.DecisionEngine] can
 * treat pest and disease risk symmetrically. Built from the actual model response
 * (`PestPredictionResponseDto`) in `PestDetectionViewModel`; never fabricated.
 */
data class PestResult(
    val detected: Boolean,
    val label: String?,
    val confidence: Float,
    val riskLevel: RiskLevel,
    val modelVersion: String,
    val scannedAt: Instant,
)
