package com.krishinirnay.core.data.model

import java.time.Instant

/**
 * State/district/taluka/village — free text, structured enough to key
 * region-specific decision rules on. [latitude]/[longitude] are optional,
 * farmer-entered coordinates (Farm Setup, manual entry — no GPS/location
 * permission is requested by this app) — when set, the weather provider
 * uses them directly instead of geocoding the state/district text on every
 * request. All fields blank/null by default so an unfilled Farm Setup
 * doesn't force fake specificity.
 */
data class FarmLocation(
    val state: String = "",
    val district: String = "",
    val taluka: String = "",
    val village: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** True once there's enough to key a region-specific rule set or a real weather/market lookup on. */
    fun isUsable(): Boolean = state.isNotBlank() || hasCoordinates()

    /** True once the farmer has entered real coordinates — see [latitude]/[longitude]. */
    fun hasCoordinates(): Boolean = latitude != null && longitude != null
}

/**
 * Persisted locally via [com.krishinirnay.core.data.local.FarmerProfileStore]
 * (DataStore, JSON) by [com.krishinirnay.core.data.network.FarmerProfileRepositoryImpl]
 * — this is real farmer-entered data, not a demo fixture, and it's the single
 * model both the Profile screen and Farm Setup read/write. [soilType] and
 * [seedlingStage] remain the exact column names the Agricultural Risk ML model
 * expects (see `RiskRepository.predictRisk`) — do not rename without updating
 * that call site too.
 */
data class FarmerProfile(
    val name: String,
    val phone: String,
    val location: String,
    val farmSizeAcres: Float,
    val crops: List<String>,

    // Inputs required by the Agricultural Risk ML model.
    val soilType: String = "Black Soil",
    val seedlingStage: String = "Germination",

    // Farm Setup (Phase 2)
    val farmLocation: FarmLocation = FarmLocation(),
    val irrigationMethod: IrrigationMethod = IrrigationMethod.RAIN_FED,
    val cropVariety: String = "",
    val sowingDate: Instant? = null,

    // Onboarding — all optional/blank by default; none of these are read by
    // DecisionEngine/FieldDecisionResolver/the Agricultural Risk model, so
    // leaving them unset never degrades a decision, only the profile's own
    // completeness.
    val dateOfBirth: Instant? = null,
    val gender: String = "",
    val alternateMobile: String = "",
    val address: String = "",
    val ownershipType: String = "",
    val waterSource: String = "",
    val farmingExperienceYears: Int? = null,
    val expectedHarvestDate: Instant? = null,
    val voiceAssistanceEnabled: Boolean = true,
) {
    /** The crop the Decision Engine / region rules should reason about — the first entry in [crops], if any. */
    val primaryCrop: String? get() = crops.firstOrNull()
}
