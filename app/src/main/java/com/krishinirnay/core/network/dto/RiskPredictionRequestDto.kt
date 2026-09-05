package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RiskPredictionRequestDto(
    val crop_ID: String,
    val soil_type: String,
    val Seedling_Stage: String,
    val MOI: Float,
    val temp: Float,
    val humidity: Float,
)