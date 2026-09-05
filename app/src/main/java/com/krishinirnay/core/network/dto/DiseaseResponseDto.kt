package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DiseaseResponseDto(
    val crop: String,
    val prediction: String,
    val confidence: Float,
    val status: String,
    val top_predictions: List<TopDiseasePrediction> = emptyList(),
)

@Serializable
data class TopDiseasePrediction(
    val `class`: String,
    val confidence: Float,
)