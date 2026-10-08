package com.krishinirnay.core.data.composite

import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.decision.DecisionEngine
import com.krishinirnay.core.decision.DecisionInput
import com.krishinirnay.core.decision.MarketInsight
import com.krishinirnay.core.decision.region.RegionCropRuleRegistry
import com.krishinirnay.core.decision.toRainOutlook
import com.krishinirnay.core.fertilizer.FertilizerAdvisor
import com.krishinirnay.core.fertilizer.FertilizerInput
import com.krishinirnay.core.ml.IrrigationModelOutput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place [com.krishinirnay.core.data.mock.MockFieldStateRepositoryImpl] and
 * [com.krishinirnay.core.data.network.LiveFieldStateRepositoryImpl] go through to reach
 * [DecisionEngine.evaluate] — this is what makes Mock and Live produce a decision through
 * the exact same pathway, with the exact same region/crop-stage/rain context, instead of
 * each repository independently (and possibly inconsistently) deciding how to build a
 * [DecisionInput]. Fixes the Phase 3 audit's Bug 1: previously every call site defaulted
 * `cropStage`/`rainOutlook`/`ruleSet`, so the region rule layer and rain-aware escalation
 * only ever ran in unit tests.
 *
 * Resolution rules — never invents a value, only uses the domain's existing
 * unavailable/default representation when data isn't there:
 * - Region + crop -> [com.krishinirnay.core.decision.region.RegionCropRuleSet] via
 *   [RegionCropRuleRegistry], keyed on [ProfileRepository]'s current
 *   `farmLocation.state` + `primaryCrop`. Falls back to
 *   [com.krishinirnay.core.decision.region.DefaultRuleSet] when the farmer hasn't set a
 *   usable location/crop, or when the pair isn't in the registry yet — never a guessed
 *   region.
 * - `cropStage` <- the same profile's `seedlingStage`, blank -> `null`.
 * - `rainOutlook` <- [WeatherRepository]'s current [com.krishinirnay.core.data.model.WeatherState]
 *   via [toRainOutlook] — `UNKNOWN` whenever weather is
 *   [com.krishinirnay.core.data.model.DataSourceStatus.UNAVAILABLE].
 *
 * Phase 4B: also builds a [FertilizerInput] from the same profile + sensors + rain
 * outlook + crop stage already resolved above, runs it through
 * [FertilizerAdvisor] (unchanged, still the only place fertilizer logic lives),
 * and passes the result straight into [DecisionEngine.evaluate] so it lands in the
 * one final [DecisionOutput] — never a second, UI-layer computation.
 *
 * Phase 4D: also reads [MarketRepository]'s current [MarketState] and, only when it
 * carries a real price ([DataSourceStatus.UNAVAILABLE] excluded), attaches a
 * [MarketInsight.PriceAvailable] snapshot — never a fabricated one.
 */
@Singleton
class FieldDecisionResolver @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val weatherRepository: WeatherRepository,
    private val marketRepository: MarketRepository,
) {
    fun evaluate(
        sensors: SensorReading,
        modelOutput: IrrigationModelOutput?,
        diseaseResult: DiseaseResult?,
        pestResult: PestResult?,
        deviceOnline: Boolean,
    ): DecisionOutput {
        val profile = profileRepository.profile.value

        val ruleSet = RegionCropRuleRegistry.forRegionAndCrop(
            region = profile.farmLocation.state,
            crop = profile.primaryCrop,
        )

        val cropStage = profile.seedlingStage.ifBlank { null }
        val rainOutlook = weatherRepository.weather.value.toRainOutlook()

        val input = DecisionInput(
            sensors = sensors,
            modelOutput = modelOutput,
            diseaseResult = diseaseResult,
            deviceOnline = deviceOnline,
            pestResult = pestResult,
            rainOutlook = rainOutlook,
            cropStage = cropStage,
        )

        val fertilizerRecommendation = FertilizerAdvisor.recommend(
            FertilizerInput(
                crop = profile.primaryCrop,
                cropVariety = profile.cropVariety.ifBlank { null },
                soilType = profile.soilType,
                cropStage = cropStage,
                farmAreaAcres = profile.farmSizeAcres,
                soilMoisturePct = sensors.soilMoisturePct,
                nitrogenPpm = sensors.nitrogenPpm,
                phosphorusPpm = sensors.phosphorusPpm,
                potassiumPpm = sensors.potassiumPpm,
                irrigationMethod = profile.irrigationMethod,
                rainOutlook = rainOutlook,
            ),
        )

        val marketInsight = marketRepository.market.value.toMarketInsight()

        return DecisionEngine.evaluate(input, ruleSet, fertilizerRecommendation, marketInsight)
    }
}

private fun MarketState.toMarketInsight(): MarketInsight {
    val modalPrice = currentPricePerQuintal
    return if (status != DataSourceStatus.UNAVAILABLE && modalPrice != null) {
        MarketInsight.PriceAvailable(
            crop = crop,
            market = market,
            modalPricePerQuintal = modalPrice,
            trend = trend,
            arrivalDate = arrivalDate,
        )
    } else {
        MarketInsight.Unavailable
    }
}
