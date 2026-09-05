package com.krishinirnay.core.network

import com.krishinirnay.core.network.dto.LatestSensorResponseDto
import retrofit2.Response
import retrofit2.http.GET

interface SensorApiService {

    @GET("api/latest-sensor")
    suspend fun getLatestSensor(): Response<LatestSensorResponseDto>
}