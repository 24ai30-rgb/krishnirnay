package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MandiRecordDto(
    val market: String,
    val district: String,
    val state: String,
    val commodity: String,
    val variety: String? = null,
    val grade: String? = null,
    val arrival_date: String? = null,
    val min_price: Float? = null,
    val max_price: Float? = null,
    val modal_price: Float? = null,
)

@Serializable
data class MarketResponseDto(
    val crop: String,
    val market: String? = null,
    val location: String? = null,
    val current_price_per_quintal: Float? = null,
    val min_price_per_quintal: Float? = null,
    val max_price_per_quintal: Float? = null,
    val average_price_per_quintal: Float? = null,
    val source: String,
    val arrival_date: String? = null,
    val variety: String? = null,
    val grade: String? = null,
    val district: String? = null,
    val state: String? = null,
    val markets: List<MandiRecordDto> = emptyList(),
    val trend: String = "UNKNOWN",
)
