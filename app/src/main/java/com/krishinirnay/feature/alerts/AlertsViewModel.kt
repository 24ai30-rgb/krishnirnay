package com.krishinirnay.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.Alert
import com.krishinirnay.core.data.repository.AlertsRepository
import com.krishinirnay.core.data.repository.FieldStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AlertsViewModel @Inject constructor(
    alertsRepository: AlertsRepository,
    fieldStateRepository: FieldStateRepository,
) : ViewModel() {

    val alerts: StateFlow<List<Alert>> = alertsRepository.alerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isOnline: StateFlow<Boolean> = fieldStateRepository.fieldState
        .map { it.deviceStatus.isOnline }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
