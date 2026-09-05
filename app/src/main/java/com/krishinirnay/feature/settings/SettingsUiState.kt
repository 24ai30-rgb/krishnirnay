package com.krishinirnay.feature.settings

import com.krishinirnay.core.data.model.AppMode

data class SettingsUiState(
    val appMode: AppMode = AppMode.MOCK,
    val language: String = "en",
)
