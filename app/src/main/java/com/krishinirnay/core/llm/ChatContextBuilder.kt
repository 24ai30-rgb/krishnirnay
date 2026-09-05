package com.krishinirnay.core.llm

import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.core.llm.dto.ChatContextDto
import javax.inject.Inject

/**
 * Snapshots the current [FieldState] into the context object sent with
 * every `/v1/chat` request — this is the "grounded in live app data"
 * mechanism, reusing the same `reasons`/risk fields the Decision Engine
 * already produces for AI Insights. The server prepends this to the
 * prompt before calling Gemini; Gemini itself never touches Firebase or
 * any app-internal system.
 */
class ChatContextBuilder @Inject constructor() {
    fun build(fieldState: FieldState): ChatContextDto = ChatContextDto(
        overallRisk = fieldState.decision.overallRisk.name,
        waterStressRisk = fieldState.decision.waterStressRisk.name,
        heatRisk = fieldState.decision.heatRisk.name,
        cropHealthRisk = fieldState.decision.cropHealthRisk.name,
        // Always rendered in English regardless of the UI language — this text is
        // internal prompt plumbing sent to Gemini, never shown to the user directly.
        reasons = fieldState.decision.reasons.map(EnglishStrings::textFor),
        soilMoisturePct = fieldState.sensors.soilMoisturePct,
        temperatureC = fieldState.sensors.temperatureC,
        humidityPct = fieldState.sensors.humidityPct,
    )
}
