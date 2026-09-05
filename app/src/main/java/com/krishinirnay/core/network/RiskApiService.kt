package com.krishinirnay.core.network

import com.krishinirnay.core.network.dto.RiskPredictionRequestDto
import com.krishinirnay.core.network.dto.RiskPredictionResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface RiskApiService {

    @POST("v1/predict/risk-fusion")
    suspend fun predictRisk(
        @Body request: RiskPredictionRequestDto,
    ): Response<RiskPredictionResponseDto>
}