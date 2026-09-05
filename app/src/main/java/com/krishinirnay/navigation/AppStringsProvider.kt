package com.krishinirnay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.strings.appStringsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppLanguageViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val language: StateFlow<String> = settingsRepository.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")
}

/**
 * Provides [LocalAppStrings] app-wide, driven by the persisted language preference — see
 * [com.krishinirnay.core.designsystem.strings.AppStrings]'s doc comment for why this exists
 * instead of the Android resource-locale mechanism.
 */
@Composable
fun AppStringsProvider(content: @Composable () -> Unit) {
    val viewModel: AppLanguageViewModel = hiltViewModel()
    val language by viewModel.language.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppStrings provides appStringsFor(language)) {
        content()
    }
}
