package com.krishinirnay.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.BuildConfig
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.llm.local.LocalLlmDiagnostics
import com.krishinirnay.core.llm.local.LocalLlmRepository
import com.krishinirnay.core.llm.local.OnDeviceModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val localLlmRepository: LocalLlmRepository,
    private val profileRepository: ProfileRepository,
    private val onDeviceModelManager: OnDeviceModelManager,
) : ViewModel() {

    // Packed into one tuple flow so the outer combine() stays at the 5-flow
    // overload rather than needing the untyped vararg form.
    private val diagnosticsState = MutableStateFlow(false to null as LocalLlmDiagnostics?)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settingsRepository.appMode, settingsRepository.language, settingsRepository.cloudFallbackEnabled, ::Triple),
        combine(localLlmRepository.status, localLlmRepository.activeProviderKind, ::Pair),
        profileRepository.profile,
        diagnosticsState,
        onDeviceModelManager.downloadState,
    ) { (mode, language, cloudFallbackEnabled), (aiStatus, providerKind), profile, (isChecking, diagnostics), onDeviceModelState ->
        SettingsUiState(
            appMode = mode,
            language = language,
            cloudFallbackEnabled = cloudFallbackEnabled,
            aiStatus = aiStatus,
            aiProviderKind = providerKind,
            voiceAssistanceEnabled = profile.voiceAssistanceEnabled,
            isCheckingAiConnection = isChecking,
            aiDiagnostics = diagnostics,
            onDeviceModelState = onDeviceModelState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /**
     * Explicit, farmer-triggered download of the on-device AI model — never
     * automatic. Downloads from this app's own server (never a third party
     * directly, matching every other provider-backed feature here); the
     * developer must have placed the real model file at
     * `server/models/on_device/` first (see that directory's README) — the
     * license-gated model itself is never fetched or bundled by this app.
     */
    fun downloadOnDeviceModel() {
        viewModelScope.launch {
            onDeviceModelManager.download("${BuildConfig.SERVER_BASE_URL}v1/local-llm/on-device-model")
            // Without this, a farmer who just downloaded the model would
            // still see the server (or "unavailable") status until the app
            // restarts — AiProviderCoordinator only re-checks on its own
            // init and doesn't watch OnDeviceModelManager's download state
            // itself, so the one place that knows "a new model just landed"
            // has to say so explicitly.
            localLlmRepository.refreshStatus()
        }
    }

    fun deleteOnDeviceModel() {
        onDeviceModelManager.deleteDownloadedModel()
    }

    /** Real (~15-30s) deep check — only ever triggered by the farmer/developer tapping the button, see ChatbotViewModel.checkConnection for the same pattern. */
    fun checkAiConnection() {
        diagnosticsState.update { true to null }
        viewModelScope.launch {
            val result = localLlmRepository.runDiagnostics()
            diagnosticsState.update { false to result }
        }
    }

    fun setAppMode(mode: AppMode) {
        viewModelScope.launch { settingsRepository.setAppMode(mode) }
    }

    fun setLanguage(languageTag: String) {
        viewModelScope.launch { settingsRepository.setLanguage(languageTag) }
    }

    fun setCloudFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloudFallbackEnabled(enabled) }
    }

    fun setVoiceAssistanceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            profileRepository.updateProfile(profileRepository.profile.value.copy(voiceAssistanceEnabled = enabled))
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
