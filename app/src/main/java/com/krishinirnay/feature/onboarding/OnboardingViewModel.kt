package com.krishinirnay.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the 6-step Farmer Registration / Onboarding flow. Writes through
 * [ProfileRepository] — the same repository Farm Setup and every Decision
 * Engine call site read — so there is exactly one farmer data store, never a
 * second one for "onboarding data." Seeded from the current profile so
 * resuming onboarding (e.g. after backgrounding the app mid-flow) never
 * shows blanker fields than what's already saved.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(seedFrom(profileRepository.profile.value, settingsRepository.language.value))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun next() {
        if (!_uiState.value.isCurrentStepValid()) return
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(ONBOARDING_STEP_COUNT - 1)) }
    }

    fun back() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun update(transform: (OnboardingUiState) -> OnboardingUiState) {
        _uiState.update(transform)
    }

    /** Only meaningful on the final (Confirmation) step. */
    fun save(onDone: () -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val current = profileRepository.profile.value
            profileRepository.updateProfile(
                current.copy(
                    name = state.name,
                    phone = state.phone,
                    alternateMobile = state.alternateMobile,
                    gender = state.gender,
                    address = state.address,
                    farmLocation = FarmLocation(
                        state = state.state,
                        district = state.district,
                        taluka = state.taluka,
                        village = state.village,
                        latitude = state.latitudeText.toDoubleOrNull(),
                        longitude = state.longitudeText.toDoubleOrNull(),
                    ),
                    farmSizeAcres = state.farmSizeAcresText.toFloatOrNull() ?: current.farmSizeAcres,
                    ownershipType = state.ownershipType,
                    waterSource = state.waterSource,
                    crops = listOfNotNull(
                        state.primaryCrop.ifBlank { null },
                        state.secondaryCrop.ifBlank { null },
                    ).ifEmpty { current.crops },
                    cropVariety = state.cropVariety,
                    seedlingStage = state.cropStage.ifBlank { current.seedlingStage },
                    irrigationMethod = state.irrigationMethod,
                    soilType = state.soilType.ifBlank { current.soilType },
                    farmingExperienceYears = state.farmingExperienceYearsText.toIntOrNull(),
                    voiceAssistanceEnabled = state.voiceAssistanceEnabled,
                ),
            )
            settingsRepository.setLanguage(state.language)
            settingsRepository.setHasCompletedOnboarding(true)
            _uiState.update { it.copy(isSaving = false, saved = true) }
            onDone()
        }
    }

    private companion object {
        fun seedFrom(profile: com.krishinirnay.core.data.model.FarmerProfile, language: String) = OnboardingUiState(
            name = profile.name,
            phone = profile.phone,
            alternateMobile = profile.alternateMobile,
            gender = profile.gender,
            address = profile.address,
            state = profile.farmLocation.state,
            district = profile.farmLocation.district,
            taluka = profile.farmLocation.taluka,
            village = profile.farmLocation.village,
            latitudeText = profile.farmLocation.latitude?.toString().orEmpty(),
            longitudeText = profile.farmLocation.longitude?.toString().orEmpty(),
            farmSizeAcresText = if (profile.farmSizeAcres > 0f) profile.farmSizeAcres.toString() else "",
            ownershipType = profile.ownershipType,
            waterSource = profile.waterSource,
            primaryCrop = profile.crops.getOrNull(0).orEmpty(),
            secondaryCrop = profile.crops.getOrNull(1).orEmpty(),
            cropVariety = profile.cropVariety,
            cropStage = profile.seedlingStage,
            language = language,
            farmingExperienceYearsText = profile.farmingExperienceYears?.toString().orEmpty(),
            irrigationMethod = profile.irrigationMethod,
            soilType = profile.soilType,
            voiceAssistanceEnabled = profile.voiceAssistanceEnabled,
        )
    }
}
