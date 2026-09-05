package com.krishinirnay.feature.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OfflineModeViewModel @Inject constructor(
    fieldStateRepository: FieldStateRepository,
) : ViewModel() {

    val uiState: StateFlow<OfflineModeUiState> = fieldStateRepository.syncStatus
        .map(SyncStatus::toOfflineModeUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineModeUiState())
}

private fun SyncStatus.toOfflineModeUiState() = OfflineModeUiState(
    isOnline = isOnline,
    lastSyncedAt = lastSyncedAt,
)
