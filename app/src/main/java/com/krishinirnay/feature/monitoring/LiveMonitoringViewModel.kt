package com.krishinirnay.feature.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.repository.FieldStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LiveMonitoringViewModel @Inject constructor(
    fieldStateRepository: FieldStateRepository,
) : ViewModel() {

    val uiState: StateFlow<LiveMonitoringUiState> = fieldStateRepository.fieldState
        .map(FieldState::toLiveMonitoringUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveMonitoringUiState())
}

private fun FieldState.toLiveMonitoringUiState() = LiveMonitoringUiState(
    isOnline = deviceStatus.isOnline,
    batteryPct = deviceStatus.batteryPct,
    soilMoisturePct = sensors.soilMoisturePct,
    temperatureC = sensors.temperatureC,
    humidityPct = sensors.humidityPct,
    ph = sensors.ph,
    lastUpdatedAt = sensors.timestamp,
)
