package com.krishinirnay.feature.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MockControls
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.mock.SensorScenario
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SimulationUiState(
    val isMockModeActive: Boolean = false,
)

/**
 * Lets the app be demonstrated and tested end to end without physical ESP32
 * hardware — see [SensorScenario]. Every control here is a genuine no-op
 * while Live Mode is active (never fakes a LIVE reading); [uiState] reflects
 * that so the screen can explain why the buttons currently do nothing.
 */
@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val mockControls: MockControls? = fieldStateRepository as? MockControls

    val uiState: StateFlow<SimulationUiState> = settingsRepository.appMode
        .map { SimulationUiState(isMockModeActive = it == AppMode.MOCK) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimulationUiState())

    fun applyScenario(scenario: SensorScenario) {
        mockControls?.applySensorScenario(scenario)
    }

    fun triggerIrrigation() {
        mockControls?.triggerIrrigation()
    }

    fun triggerDeviceDisconnect() {
        mockControls?.triggerDeviceDisconnect()
    }

    fun triggerDeviceReconnect() {
        mockControls?.triggerDeviceReconnect()
    }
}
