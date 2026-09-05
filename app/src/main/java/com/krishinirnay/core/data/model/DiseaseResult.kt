package com.krishinirnay.core.data.model

import java.time.Instant

/** Mirrors the `POST /v1/predict/disease` response shape — see docs/api-contract.md. */
data class DiseaseResult(
    val label: String,
    val displayName: String,
    val confidence: Float,
    val riskLevel: RiskLevel,
    val modelVersion: String,
    val scannedAt: Instant,
)
