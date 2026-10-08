package com.krishinirnay.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.krishinirnay.core.data.firebase.FarmerCloudProfileRepository
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val cloudProfileRepository: FarmerCloudProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your email and password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.login(state.email.trim(), state.password)
                .onSuccess {
                    // Never let the Firestore restore hold up login itself —
                    // see RegisterViewModel's identical fix and its own
                    // comment for the measured reason (Firestore not yet
                    // enabled in the console can make a call retry for a
                    // long time rather than fail fast).
                    launch { withTimeoutOrNull(10_000) { restoreCloudProfileIfPresent() } }
                    val needsOnboarding = !settingsRepository.hasCompletedOnboarding.value
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true, needsOnboarding = needsOnboarding) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = throwable.toFriendlyMessage()) }
                }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your email first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.sendPasswordResetEmail(email)
                .onSuccess { _uiState.update { it.copy(isLoading = false, resetEmailSent = true) } }
                .onFailure { throwable -> _uiState.update { it.copy(isLoading = false, errorMessage = throwable.toFriendlyMessage()) } }
        }
    }

    /**
     * Restores the farmer's full saved profile onto whichever device they
     * log in from. Silently leaves the local profile untouched if the
     * account has no cloud document yet or Firestore is unreachable — never
     * blocks or fails the login itself over this.
     *
     * ponytail: last-write-wins — if the farmer edited Farm Setup offline on
     * a different device since this device's last sync, this overwrites
     * those edits with the older cloud copy. Add an `updatedAt` comparison
     * before applying if offline multi-device editing turns out to matter.
     */
    private suspend fun restoreCloudProfileIfPresent() {
        val uid = authRepository.currentUser.value?.uid ?: return
        val cloud = cloudProfileRepository.fetchProfile(uid) ?: return
        profileRepository.updateProfile(cloud)
    }

    private fun Throwable.toFriendlyMessage(): String = when {
        this is FirebaseNetworkException -> "Network connection required for Firebase login."
        this is FirebaseAuthException && errorCode == "ERROR_USER_NOT_FOUND" -> "No account found. Please register first."
        this is FirebaseAuthException &&
            errorCode in setOf("ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_EMAIL") -> "Invalid email or password."
        else -> "Something went wrong — please try again"
    }
}
