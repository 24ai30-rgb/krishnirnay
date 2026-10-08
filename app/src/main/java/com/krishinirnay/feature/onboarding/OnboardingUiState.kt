package com.krishinirnay.feature.onboarding

import com.krishinirnay.core.data.model.IrrigationMethod

/** 6 steps: Welcome, Personal, Farm, Crop, Preferences, Confirmation — see OnboardingScreen. */
const val ONBOARDING_STEP_COUNT = 6

/**
 * Local draft state for the multi-step registration flow — seeded from
 * [com.krishinirnay.core.data.repository.ProfileRepository]'s current profile
 * (so re-entering onboarding, e.g. after a process death mid-flow, never
 * starts from a blanker state than what's already saved) and written back to
 * the same [com.krishinirnay.core.data.model.FarmerProfile] on the final
 * step — never a second, competing farmer data store.
 */
data class OnboardingUiState(
    val step: Int = 0,

    // Step 2: Personal
    val name: String = "",
    val phone: String = "",
    val alternateMobile: String = "",
    val gender: String = "",
    val address: String = "",
    val state: String = "",
    val district: String = "",
    val taluka: String = "",
    val village: String = "",

    // Step 3: Farm
    val latitudeText: String = "",
    val longitudeText: String = "",
    val farmSizeAcresText: String = "",
    val ownershipType: String = "",
    val irrigationAvailable: Boolean = true,
    val waterSource: String = "",

    // Step 4: Crop
    val primaryCrop: String = "",
    val secondaryCrop: String = "",
    val cropVariety: String = "",
    val cropStage: String = "",

    // Step 5: Preferences
    val language: String = "en",
    val farmingExperienceYearsText: String = "",
    val irrigationMethod: IrrigationMethod = IrrigationMethod.RAIN_FED,
    val soilType: String = "",
    val voiceAssistanceEnabled: Boolean = true,

    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    /** Required-field check for the current step — Continue is disabled until this is true. */
    fun isCurrentStepValid(): Boolean = when (step) {
        1 -> name.isNotBlank() && phone.isNotBlank() && state.isNotBlank() && district.isNotBlank()
        2 -> farmSizeAcresText.toFloatOrNull() != null && farmSizeAcresText.toFloatOrNull()!! > 0f
        3 -> primaryCrop.isNotBlank()
        else -> true
    }
}
