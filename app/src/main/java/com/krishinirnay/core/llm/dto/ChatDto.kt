package com.krishinirnay.core.llm.dto

import kotlinx.serialization.Serializable

/** `mode` is `"chat"` (Chatbot screen) or `"explain"` (AI Insights polish) — see docs/api-contract.md. */
@Serializable
data class ChatRequestDto(
    val mode: String,
    val message: String,
    val context: ChatContextDto,
)

@Serializable
data class ChatContextDto(
    val overallRisk: String,
    val waterStressRisk: String,
    val heatRisk: String,
    val cropHealthRisk: String,
    val reasons: List<String>,
    val soilMoisturePct: Float,
    val temperatureC: Float,
    val humidityPct: Float,
)

@Serializable
data class ChatResponseDto(
    val reply: String,
)
