package com.krishinirnay.feature.profile

import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.designsystem.strings.AppStrings

/**
 * Percent complete out of the fields checked below, plus which are still
 * blank. Fields like [FarmerProfile.soilType]/[FarmerProfile.irrigationMethod]
 * are deliberately excluded — they default to a real, valid value (e.g.
 * "Black Soil"), so an unedited default can't be told apart from a farmer's
 * deliberate choice. Only fields that are genuinely blank/unset when
 * unfilled are counted.
 */
data class ProfileCompletion(val percent: Int, val missingLabels: List<String>)

fun FarmerProfile.completion(strings: AppStrings): ProfileCompletion {
    val checks = listOf(
        (name.isNotBlank()) to strings.onboardingFullName,
        (phone.isNotBlank()) to strings.onboardingMobileNumber,
        (farmLocation.state.isNotBlank()) to strings.farmSetupState,
        (farmLocation.district.isNotBlank()) to strings.farmSetupDistrict,
        (farmLocation.village.isNotBlank()) to strings.farmSetupVillage,
        (primaryCrop?.isNotBlank() == true) to strings.onboardingPrimaryCrop,
        (farmSizeAcres > 0f) to strings.farmSetupAcres,
        (gender.isNotBlank()) to strings.onboardingGender,
        (address.isNotBlank()) to strings.onboardingAddress,
        (ownershipType.isNotBlank()) to strings.onboardingOwnershipType,
        (waterSource.isNotBlank()) to strings.onboardingWaterSource,
        (cropVariety.isNotBlank()) to strings.farmSetupCropVariety,
        (farmingExperienceYears != null) to strings.onboardingFarmingExperience,
    )
    val percent = (checks.count { it.first } * 100) / checks.size
    val missing = checks.filterNot { it.first }.map { it.second }
    return ProfileCompletion(percent, missing)
}
