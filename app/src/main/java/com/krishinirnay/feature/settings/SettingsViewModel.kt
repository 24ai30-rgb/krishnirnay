package com.krishinirnay.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.appMode,
        settingsRepository.language,
    ) { mode, language -> SettingsUiState(appMode = mode, language = language) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setAppMode(mode: AppMode) {
        viewModelScope.launch { settingsRepository.setAppMode(mode) }
    }

    fun setLanguage(languageTag: String) {
        viewModelScope.launch { settingsRepository.setLanguage(languageTag) }
    }

    fun logout() {
        authRepository.logout()
    }
}
