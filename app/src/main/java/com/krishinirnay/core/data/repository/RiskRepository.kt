package com.krishinirnay.core.data.repository

data class RiskPrediction(
    val riskClass: Int,
    val confidence: Float,
    val probabilities: Map<String, Float>,
    val modelVersion: String,
)

interface RiskRepository {

    suspend fun predictRisk(
        cropId: String,
        soilType: String,
        seedlingStage: String,
        moi: Float,
        temperature: Float,
        humidity: Float,
    ): Result<RiskPrediction>
}