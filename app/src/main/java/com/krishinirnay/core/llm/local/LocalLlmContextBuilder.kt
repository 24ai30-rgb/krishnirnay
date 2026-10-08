package com.krishinirnay.core.llm.local

import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import com.krishinirnay.core.designsystem.strings.textFor
import javax.inject.Inject

/**
 * Snapshots real, already-computed app state into [LocalLlmContextDto] — the
 * only data the server's Local LLM prompt is built from (see
 * `server/app/routers/local_llm.py`). Reasons/recommendation/fertilizer text
 * are always rendered in English here regardless of the UI language — like
 * [com.krishinirnay.core.llm.ChatContextBuilder], this is internal prompt
 * plumbing sent to the model, never shown to the farmer directly (the model
 * is instructed to reply in [language] itself). A field this app has no real
 * data for is left null, never guessed.
 */
class LocalLlmContextBuilder @Inject constructor() {

    fun build(
        fieldState: FieldState,
        profile: FarmerProfile,
        weather: WeatherState,
        market: MarketState,
        language: String,
    ): LocalLlmContextDto {
        val decision = fieldState.decision
        return LocalLlmContextDto(
            language = language,
            farmer_state = profile.farmLocation.state.ifBlank { null },
            farmer_district = profile.farmLocation.district.ifBlank { null },
            crop = profile.primaryCrop,
            crop_stage = profile.seedlingStage.ifBlank { null },
            farming_method = profile.irrigationMethod.name,
            soil_type = profile.soilType,
            soil_moisture_pct = fieldState.sensors.soilMoisturePct,
            temperature_c = fieldState.sensors.temperatureC,
            humidity_pct = fieldState.sensors.humidityPct,
            weather_status = weather.status.name,
            weather_summary = weather.takeIf { it.status != DataSourceStatus.UNAVAILABLE }
                ?.let { "${it.currentTempC}C, rain chance ${it.rainChancePct}%" },
            market_status = market.status.name,
            market_summary = market.takeIf {
                it.status != DataSourceStatus.UNAVAILABLE && it.currentPricePerQuintal != null
            }?.let { "${it.crop} modal price Rs.${it.currentPricePerQuintal?.toInt()} at ${it.market ?: "local mandi"}" },
            overall_risk = decision.overallRisk.name,
            pest_summary = decision.pestRisk.takeIf { it != RiskLevel.UNKNOWN }?.name,
            disease_summary = decision.cropHealthRisk.takeIf { it != RiskLevel.UNKNOWN }?.name,
            fertilizer_summary = decision.fertilizerRecommendation?.let { EnglishStrings.textFor(it) },
            recommendation_summary = EnglishStrings.textFor(decision.recommendation),
            reasons = decision.reasons.map(EnglishStrings::textFor),
        )
    }
}
