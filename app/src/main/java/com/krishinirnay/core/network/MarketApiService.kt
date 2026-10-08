package com.krishinirnay.core.network

import com.krishinirnay.core.network.dto.MarketResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MarketApiService {

    @GET("v1/market")
    suspend fun getMarketPrice(
        @Query("crop") crop: String,
        @Query("state") state: String? = null,
        @Query("district") district: String? = null,
    ): Response<MarketResponseDto>
}
