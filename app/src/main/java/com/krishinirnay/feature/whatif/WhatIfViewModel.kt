package com.krishinirnay.feature.whatif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.RiskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WhatIfViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    private val profileRepository: ProfileRepository,
    private val riskRepository: RiskRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhatIfUiState())
    val uiState: StateFlow<WhatIfUiState> = _uiState.asStateFlow()

    fun onDelayChange(delayHours: Float) {
        _uiState.update {
            it.copy(
                delayHours = delayHours,
                isCalculating = true,
            )
        }

        predictRisk()
    }

    fun predictRisk() {
        viewModelScope.launch {
            val currentState = fieldStateRepository.fieldState.value
            val profile = profileRepository.profile.value

            val crop = profile.crops.firstOrNull()

            if (crop.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        predictedRisk = com.krishinirnay.core.data.model.RiskLevel.UNKNOWN,
                        isCalculating = false,
                    )
                }
                return@launch
            }

            val result = riskRepository.predictRisk(
                cropId = crop,
                soilType = profile.soilType,
                seedlingStage = profile.seedlingStage,
                moi = currentState.sensors.soilMoisturePct,
                temperature = currentState.sensors.temperatureC,
                humidity = currentState.sensors.humidityPct,
            )

            result
                .onSuccess { prediction ->
                    _uiState.update {
                        it.copy(
                            isCalculating = false,
                            predictedRisk = mapRiskClass(prediction.riskClass),
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            predictedRisk = com.krishinirnay.core.data.model.RiskLevel.UNKNOWN,
                            isCalculating = false,
                        )
                    }
                }
        }
    }

    fun simulateIrrigationNow() {
        _uiState.update {
            it.copy(
                delayHours = 0f,
                isCalculating = true,
            )
        }

        predictRisk()
    }

    /**
     * Temporary mapping.
     *
     * IMPORTANT:
     * The trained dataset's class meanings (0/1/2) have not yet
     * been verified as LOW/MEDIUM/HIGH.
     *
     * Therefore this mapping is only a UI placeholder until the
     * class-label meaning is confirmed.
     */
    private fun mapRiskClass(riskClass: Int):
        com.krishinirnay.core.data.model.RiskLevel {

        return when (riskClass) {
            0 -> com.krishinirnay.core.data.model.RiskLevel.LOW
            1 -> com.krishinirnay.core.data.model.RiskLevel.MEDIUM
            2 -> com.krishinirnay.core.data.model.RiskLevel.HIGH
            else -> com.krishinirnay.core.data.model.RiskLevel.UNKNOWN
        }
    }
}