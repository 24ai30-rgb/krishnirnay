package com.krishinirnay.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PestPredictionResponseDto(
    val success: Boolean,
    val message: String,
    val model: String,
    val model_version: String,
    val detected: Boolean,
    val count: Int,
    val top_detection: PestDetectionDto? = null,
    val detections: List<PestDetectionDto> = emptyList(),
)

@Serializable
data class PestDetectionDto(
    val class_id: Int,
    val class_name: String,
    val confidence: Float,
    val bounding_box: BoundingBoxDto,
)

@Serializable
data class BoundingBoxDto(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)