package com.krishinirnay.core.network

import com.krishinirnay.core.network.dto.DiseaseResponseDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface DiseaseApiService {
    @Multipart
    @POST("v1/predict/disease")
    suspend fun predictDisease(@Part image: MultipartBody.Part): Response<DiseaseResponseDto>
}
