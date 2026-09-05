package com.krishinirnay.core.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import com.krishinirnay.core.network.dto.PestPredictionResponseDto

interface PestApiService {

    @Multipart
    @POST("v1/predict/pest")
    suspend fun predictPest(
        @Part file: MultipartBody.Part,
    ): Response<PestPredictionResponseDto>
}