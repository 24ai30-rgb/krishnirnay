package com.krishinirnay.core.data.repository

import com.krishinirnay.core.network.RiskApiService
import com.krishinirnay.core.network.dto.RiskPredictionRequestDto
import javax.inject.Inject
import javax.inject.Singleton

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
    ): Result<RiskPrediction> {

        return try {
            val response = riskApiService.predictRisk(
                RiskPredictionRequestDto(
                    crop_ID = cropId,
                    soil_type = soilType,
                    Seedling_Stage = seedlingStage,
                    MOI = moi,
                    temp = temperature,
                    humidity = humidity,
                ),
            )

            if (response.isSuccessful) {
                val body = response.body()

                if (body != null) {
                    Result.success(
                        RiskPrediction(
                            riskClass = body.risk_class,
                            confidence = body.confidence,
                            probabilities = body.probabilities,
                            modelVersion = body.model_version,
                        ),
                    )
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Empty risk prediction response",
                        ),
                    )
                }
            } else {
                Result.failure(
                    IllegalStateException(
                        "Risk API failed: HTTP ${response.code()}",
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}