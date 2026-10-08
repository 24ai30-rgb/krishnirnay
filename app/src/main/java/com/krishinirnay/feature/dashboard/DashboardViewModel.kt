package com.krishinirnay.feature.dashboard

import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.voice.TextToSpeechManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.model.toDataSourceStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.RiskRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    private val profileRepository: ProfileRepository,
    private val riskRepository: RiskRepository,
    private val weatherRepository: WeatherRepository,
    private val marketRepository: MarketRepository,
    private val textToSpeechManager: TextToSpeechManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val apiRisk = MutableStateFlow<Int?>(null)
    private val apiConfidence = MutableStateFlow(0f)

    /** Farm/weather/market context, combined separately since Kotlin's typed `combine` tops out at 5 flows. */
    private val farmContext = combine(
        profileRepository.profile,
        weatherRepository.weather,
        marketRepository.market,
    ) { profile, weather, market -> Triple(profile, weather, market) }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            fieldStateRepository.fieldState,
            fieldStateRepository.syncStatus,
            apiRisk,
            apiConfidence,
            farmContext,
        ) { fieldState, syncStatus, riskClass, confidence, (profile, weather, market) ->

            fieldState.toDashboardUiState(
                syncStatus = syncStatus,
                apiRisk = riskClass,
                apiConfidence = confidence,
                profile = profile,
                weather = weather,
                market = market,
            )

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState(),
        )

    init {
        monitorRisk()
    }

    /**
     * Retry for the Weather card's error state. Goes through the repository's own
     * refresh so the result still lands in the single shared WeatherState — the
     * screen never fetches on its own.
     */
    /** Reads today's decision aloud in the farmer's chosen language (hero card's Listen button). */
    fun speakDecision(text: String) {
        val tag = when (settingsRepository.language.value) {
            "hi" -> "hi-IN"
            "mr" -> "mr-IN"
            else -> "en-IN"
        }
        textToSpeechManager.speak(text, languageTag = tag)
    }

    override fun onCleared() {
        textToSpeechManager.stop()
        super.onCleared()
    }

    fun retryWeather() {
        viewModelScope.launch { weatherRepository.refresh() }
    }

    /** Retry for the Market card's error state. */
    fun retryMarket() {
        viewModelScope.launch { marketRepository.refresh() }
    }

    private fun monitorRisk() {

        viewModelScope.launch {

            combine(
                fieldStateRepository.fieldState,
                profileRepository.profile,
            ) { fieldState, profile ->
                fieldState to profile
            }.collect { (fieldState, profile) ->

                val crop = profile.crops.firstOrNull()

                if (crop.isNullOrBlank()) {
                    apiRisk.value = null
                    apiConfidence.value = 0f
                    return@collect
                }

                val sensors = fieldState.sensors

                riskRepository.predictRisk(
                    cropId = crop,
                    soilType = profile.soilType,
                    seedlingStage = profile.seedlingStage,
                    moi = sensors.soilMoisturePct,
                    temperature = sensors.temperatureC,
                    humidity = sensors.humidityPct,
                ).onSuccess { prediction ->

                    android.util.Log.d(
                        "KRISHI_RISK",
                        "ML SUCCESS: class=${prediction.riskClass}, " +
                            "confidence=${prediction.confidence}, " +
                            "MOI=${sensors.soilMoisturePct}, " +
                            "TEMP=${sensors.temperatureC}, " +
                            "HUMIDITY=${sensors.humidityPct}",
                    )

                    apiRisk.value = prediction.riskClass
                    apiConfidence.value = prediction.confidence

                }.onFailure { error ->

                    android.util.Log.e(
                        "KRISHI_RISK",
                        "ML FAILED: ${error.message}",
                        error,
                    )
                }
            }
        }
    }
}


/**
 * SMART OVERALL FIELD RISK
 *
 * Priority:
 * 1. Critical soil moisture
 * 2. Extreme temperature
 * 3. ML prediction
 * 4. Existing decision engine
 */
/** `internal`, not `private` — so `DashboardViewModelTest` can exercise the risk-fusion logic directly without duplicating it. */
internal fun FieldState.toDashboardUiState(
    syncStatus: SyncStatus,
    apiRisk: Int?,
    apiConfidence: Float,
    profile: FarmerProfile,
    weather: WeatherState,
    market: MarketState,
): DashboardUiState {

    val moisture = sensors.soilMoisturePct
    val temperature = sensors.temperatureC
    val humidity = sensors.humidityPct

    /*
     * ============================================================
     * 1. SENSOR-BASED CRITICAL CONDITIONS
     * ============================================================
     */

    val criticalWaterRisk =
        moisture > 0f && moisture < 20f

    val mediumWaterRisk =
        moisture >= 20f && moisture < 30f

    val extremeHeat =
        temperature >= 40f

    val mediumHeat =
        temperature >= 35f


    /*
     * ============================================================
     * 2. ML RISK
     *
     * IMPORTANT:
     * 0/1/2 mapping is still provisional until training labels
     * are verified.
     * ============================================================
     */

    val mlRisk = when (apiRisk) {
        0 -> RiskLevel.LOW
        1 -> RiskLevel.MEDIUM
        2 -> RiskLevel.HIGH
        else -> null
    }


    /*
     * ============================================================
     * 3. SMART FUSION
     * ============================================================
     *
     * Critical real-time sensor conditions override weak ML
     * predictions.
     *
     * BUG FIX (Phase 3B): a genuine HIGH from `decision.overallRisk` —
     * DecisionEngine's own output, which since Phase 3A already fuses
     * water/heat/pest/disease/region/rain — must never be masked by a
     * weaker signal from the separate ML risk-fusion classifier
     * (`mlRisk`). Before this fix, `mlRisk != null -> mlRisk` fired
     * whenever the ML call had ever succeeded, discarding a real
     * pest/disease-driven HIGH from the engine in favor of a plain
     * LOW/MEDIUM ML class. The guard below runs right after the
     * sensor-critical checks and before every ML-only branch, so HIGH
     * can never be silently downgraded — LOW/MEDIUM fusion below it is
     * unchanged.
     */

    val overallRisk = when {

        /*
         * VERY LOW MOISTURE
         *
         * Example:
         * 14.2% -> HIGH
         */
        criticalWaterRisk ->
            RiskLevel.HIGH


        /*
         * EXTREME TEMPERATURE
         */
        extremeHeat ->
            RiskLevel.HIGH


        /*
         * NEVER MASK A REAL ENGINE HIGH.
         *
         * decision.overallRisk already correctly fuses pest, disease,
         * region rules, and rain outlook (Phase 3A) — if it says HIGH,
         * the dashboard must say HIGH, regardless of what the separate
         * ML classifier returned.
         */
        decision.overallRisk == RiskLevel.HIGH ->
            RiskLevel.HIGH


        /*
         * LOW-CONFIDENCE ML should not create an aggressive
         * decision by itself.
         */
        mlRisk == RiskLevel.HIGH &&
            apiConfidence >= 0.70f ->
            RiskLevel.HIGH


        /*
         * Moderate moisture + medium ML
         */
        mediumWaterRisk &&
            mlRisk != RiskLevel.LOW ->
            RiskLevel.MEDIUM


        /*
         * Moderate heat
         */
        mediumHeat &&
            mlRisk == RiskLevel.LOW ->
            RiskLevel.MEDIUM


        /*
         * Normal ML result
         */
        mlRisk != null ->
            mlRisk


        /*
         * Existing offline decision engine
         */
        else ->
            decision.overallRisk
    }


    /*
     * ============================================================
     * 4. SMART RECOMMENDATION
     * ============================================================
     */

    val smartRecommendation = when {

        criticalWaterRisk ->
            decision.recommendation

        extremeHeat ->
            decision.recommendation

        else ->
            decision.recommendation
    }


    /*
     * ============================================================
     * 5. RETURN DASHBOARD STATE
     * ============================================================
     */

    return DashboardUiState(

        overallRisk = overallRisk,

        riskConfidence = apiConfidence,

        soilMoisturePct = moisture,

        temperatureC = temperature,

        humidityPct = humidity,

        recommendation = smartRecommendation,

        waterStressRisk =
            when {
                criticalWaterRisk ->
                    RiskLevel.HIGH

                mediumWaterRisk ->
                    RiskLevel.MEDIUM

                else ->
                    decision.waterStressRisk
            },

        heatRisk =
            when {
                extremeHeat ->
                    RiskLevel.HIGH

                mediumHeat ->
                    RiskLevel.MEDIUM

                else ->
                    decision.heatRisk
            },

        cropHealthRisk =
            decision.cropHealthRisk,

        pestRisk =
            decision.pestRisk,

        isDeviceOnline =
            deviceStatus.isOnline,

        lastSyncedAt =
            deviceStatus.lastSeenAt,

        dataSourceStatus =
            syncStatus.toDataSourceStatus(),

        timing =
            decision.timing,

        expectedBenefit =
            decision.expectedBenefit,

        reasons =
            decision.reasons,

        fertilizerRecommendation =
            decision.fertilizerRecommendation,

        profile = profile,

        diseaseResult = diseaseResult,

        pestResult = pestResult,

        weather = weather,

        market = market,
    )
}
