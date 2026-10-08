package com.krishinirnay.core.network

import com.krishinirnay.core.network.dto.WeatherResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    @GET("v1/weather")
    suspend fun getWeather(
        @Query("state") state: String,
        @Query("district") district: String? = null,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
    ): Response<WeatherResponseDto>
}
