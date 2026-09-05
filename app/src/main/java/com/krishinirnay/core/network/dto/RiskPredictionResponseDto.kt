package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RiskPredictionResponseDto(
    val risk_class: Int,
    val confidence: Float,
    val probabilities: Map<String, Float>,
    val model_version: String,
)