package com.krishinirnay.core.llm.local

import kotlinx.serialization.Serializable

/**
 * Structured, factual context only — mirrors `server/app/schemas/local_llm.py`'s
 * `LocalLlmContext` field-for-field. Every field must come from a real upstream
 * source ([com.krishinirnay.core.data.model.FieldState]/[com.krishinirnay.core.data.model.FarmerProfile]/
 * [com.krishinirnay.core.data.model.WeatherState]/[com.krishinirnay.core.data.model.MarketState]) —
 * see [LocalLlmContextBuilder]. Never a value the app itself invents.
 */
@Serializable
data class LocalLlmContextDto(
    val language: String = "en",
    val farmer_state: String? = null,
    val farmer_district: String? = null,
    val crop: String? = null,
    val crop_stage: String? = null,
    val farming_method: String? = null,
    val soil_type: String? = null,
    val soil_moisture_pct: Float? = null,
    val temperature_c: Float? = null,
    val humidity_pct: Float? = null,
    val weather_status: String? = null,
    val weather_summary: String? = null,
    val market_status: String? = null,
    val market_summary: String? = null,
    val overall_risk: String? = null,
    val pest_summary: String? = null,
    val disease_summary: String? = null,
    val fertilizer_summary: String? = null,
    val recommendation_summary: String? = null,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class LocalLlmChatRequestDto(
    val message: String,
    val context: LocalLlmContextDto,
)

/**
 * One stable shape whether the server answered or failed — mirrors
 * `server/app/schemas/local_llm.py`'s `LocalLlmChatResponse`. The endpoint
 * always returns HTTP 200; [success] is the field to check, not the HTTP
 * status code, so there is exactly one response class to parse instead of a
 * success shape plus a separately-shaped error body.
 */
@Serializable
data class LocalLlmChatResponseDto(
    val success: Boolean,
    val language: String,
    val answer: String? = null,
    val source: String = "local_llm",
    val model: String,
    val error: String? = null,
    /** Real generation time from the server — advanced/debug display only. */
    val elapsed_ms: Int? = null,
)

@Serializable
data class LocalLlmStatusResponseDto(
    val status: String,
    val ollama_running: Boolean = false,
    val provider: String,
    val model: String,
    /** Per-language ("en"/"hi"/"mr") pulled-model check — see server's LocalLlmStatusResponse docstring. */
    val model_availability: Map<String, Boolean> = emptyMap(),
)
