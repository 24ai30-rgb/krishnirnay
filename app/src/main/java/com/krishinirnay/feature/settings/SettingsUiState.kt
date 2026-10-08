package com.krishinirnay.feature.settings

import com.krishinirnay.core.data.model.ThemeMode

import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmDiagnostics
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.ModelDownloadState

data class SettingsUiState(
    // Matches AppPreferences' LIVE default, so the toggle doesn't briefly render
    // the wrong selection before the real preference value arrives.
    val appMode: AppMode = AppMode.LIVE,
    val language: String = "en",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cloudFallbackEnabled: Boolean = false,
    // Never a fake "AI online" status — see LocalLlmRepository.
    val aiStatus: LocalLlmStatus = LocalLlmStatus.LOADING,
    val aiProviderKind: AiProviderKind = AiProviderKind.NONE,
    val voiceAssistanceEnabled: Boolean = true,
    // AI Diagnostics (Phase 5 Part 14) — on-demand only, see runDiagnostics().
    val isCheckingAiConnection: Boolean = false,
    val aiDiagnostics: LocalLlmDiagnostics? = null,
    // On-device AI model (Phase 5 Part 3) — see OnDeviceModelManager. Never
    // downloaded automatically; the farmer must explicitly tap Download.
    val onDeviceModelState: ModelDownloadState = ModelDownloadState.NotDownloaded,
)
