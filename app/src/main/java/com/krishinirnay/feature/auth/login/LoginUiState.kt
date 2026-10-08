package com.krishinirnay.feature.auth.login

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    /** True when the farmer hasn't completed the onboarding flow yet — read from SettingsRepository, never guessed. */
    val needsOnboarding: Boolean = false,
    val resetEmailSent: Boolean = false,
)
