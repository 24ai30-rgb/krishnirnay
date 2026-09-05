package com.krishinirnay.core.data.network

import com.krishinirnay.core.data.repository.RiskPrediction
import com.krishinirnay.core.data.repository.RiskRepository
import com.krishinirnay.core.network.RiskApiService
import com.krishinirnay.core.network.dto.RiskPredictionRequestDto
import javax.inject.Inject
import javax.inject.Singleton

class RiskPredictionException(message: String) : Exception(message)

@Singleton
class RiskRepositoryImpl @Inject constructor(
    private val riskApiService: RiskApiService,
) : RiskRepository {

    override suspend fun predictRisk(
        cropId: String,
        soilType: String,
        seedlingStage: String,
        moi: Float,
        temperature: Float,
        humidity: Float,
    ): Result<RiskPrediction> = runCatching {

        val request = RiskPredictionRequestDto(
            crop_ID = cropId,
            soil_type = soilType,
            Seedling_Stage = seedlingStage,
            MOI = moi,
            temp = temperature,
            humidity = humidity,
        )

        val response = riskApiService.predictRisk(request)
        val body = response.body()

        if (response.isSuccessful && body != null) {
            RiskPrediction(
                riskClass = body.risk_class,
                confidence = body.confidence,
                probabilities = body.probabilities,
                modelVersion = body.model_version,
            )
        } else {
            throw RiskPredictionException(
                "Risk prediction failed. HTTP ${response.code()}",
            )
        }
    }
}