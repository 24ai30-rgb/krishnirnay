package com.krishinirnay.feature.analytics

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

private const val MIN_POINTS_FOR_CHART = 5

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    fieldStateRepository: FieldStateRepository,
) : ViewModel() {

    val uiState: StateFlow<AnalyticsUiState> = fieldStateRepository.fieldState
        .map(FieldState::toAnalyticsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())
}

private fun FieldState.toAnalyticsUiState(): AnalyticsUiState {
    // history already ends with the current sensors reading — see
    // MockFieldStateRepositoryImpl.updateFieldState — so it alone covers "now".
    val readings = history
    return AnalyticsUiState(
        hasEnoughData = readings.size >= MIN_POINTS_FOR_CHART,
        isOnline = deviceStatus.isOnline,
        soilMoistureHistory = readings.map { it.soilMoisturePct },
        temperatureHistory = readings.map { it.temperatureC },
        humidityHistory = readings.map { it.humidityPct },
    )
}
