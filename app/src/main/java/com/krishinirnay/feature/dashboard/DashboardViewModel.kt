package com.krishinirnay.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.RiskRepository
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
) : ViewModel() {

    private val apiRisk = MutableStateFlow<Int?>(null)
    private val apiConfidence = MutableStateFlow(0f)

    val uiState: StateFlow<DashboardUiState> =
        combine(
            fieldStateRepository.fieldState,
            apiRisk,
            apiConfidence,
        ) { fieldState, riskClass, confidence ->

            fieldState.toDashboardUiState(
                apiRisk = riskClass,
                apiConfidence = confidence,
            )

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState(),
        )

    init {
        monitorRisk()
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
private fun FieldState.toDashboardUiState(
    apiRisk: Int?,
    apiConfidence: Float,
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

        isDeviceOnline =
            deviceStatus.isOnline,

        lastSyncedAt =
            deviceStatus.lastSeenAt,
    )
}