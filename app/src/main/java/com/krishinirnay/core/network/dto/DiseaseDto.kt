package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Shared error shape for non-2xx responses.
 */
@Serializable
data class ApiErrorDto(
    val error: String,
    val message: String,
)