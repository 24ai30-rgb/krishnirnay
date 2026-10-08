package com.krishinirnay.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.designsystem.strings.appStringsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A plain regex rather than android.util.Patterns.EMAIL_ADDRESS: the latter
// is a stubbed-null field in local JVM unit tests (see app/build.gradle.kts
// isReturnDefaultValues), so using it here would crash every test that
// calls register() — this is simple format validation only, not a full
// RFC 5322 validator; Firebase itself is the real authority on a valid email.
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * NEW USER flow (see KrishiNavGraph): Register -> Firebase Auth account ->
 * Firestore profile document -> Onboarding (Farm Setup) -> Home. Crop/state/
 * district are collected in the existing Onboarding flow, not duplicated
 * here — this screen only owns the account fields (name/email/password/
 * mobile); Onboarding fills in the rest later, and every save it makes
 * (via ProfileRepository.updateProfile) syncs to Firestore on its own.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value, errorMessage = null) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value, errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    fun onMobileChange(value: String) = _uiState.update { it.copy(mobile = value, errorMessage = null) }

    fun register() {
        val strings = appStringsFor(settingsRepository.language.value)
        val state = _uiState.value
        val error = when {
            state.firstName.isBlank() -> strings.registerErrorNameRequired
            !EMAIL_REGEX.matches(state.email.trim()) -> strings.registerErrorInvalidEmail
            state.password.length < 6 -> strings.registerErrorPasswordTooShort
            state.password != state.confirmPassword -> strings.registerErrorPasswordMismatch
            state.mobile.filter(Char::isDigit).length != 10 -> strings.registerErrorInvalidMobile
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }

        val fullName = listOf(state.firstName.trim(), state.lastName.trim()).filter(String::isNotBlank).joinToString(" ")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.register(state.email.trim(), state.password)
                .onSuccess {
                    // A genuinely fresh profile, never the previous
                    // FarmerProfile.copy(...) — the local profile store and
                    // "onboarding completed" flag are both device-scoped,
                    // not per Firebase account (a real gap found live: a
                    // second real account registered on a device that had
                    // already onboarded one farmer silently inherited that
                    // farmer's crop/farm-size/location and skipped Farm
                    // Setup entirely). Resetting both here is the correct
                    // fix for the case this task actually asks about — a
                    // fresh registration must always reach Farmer Profile
                    // Setup with blank farm data, never a stranger's.
                    // Firestore sync happens automatically inside
                    // ProfileRepository.updateProfile (best-effort, never
                    // blocks the caller — see FarmerProfileRepositoryImpl),
                    // so registration doesn't need its own Firestore call.
                    profileRepository.updateProfile(
                        FarmerProfile(name = fullName, phone = state.mobile.trim(), location = "", farmSizeAcres = 0f, crops = emptyList()),
                    )
                    settingsRepository.setHasCompletedOnboarding(false)
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = throwable.toFriendlyMessage()) }
                }
        }
    }

    private fun Throwable.toFriendlyMessage(): String = when {
        this is FirebaseNetworkException -> "Network connection required to create your account."
        this is FirebaseAuthException && errorCode == "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Please log in instead."
        this is FirebaseAuthException && errorCode == "ERROR_WEAK_PASSWORD" -> "Please choose a stronger password."
        this is FirebaseAuthException && errorCode == "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
        else -> "Something went wrong — please try again"
    }
}
