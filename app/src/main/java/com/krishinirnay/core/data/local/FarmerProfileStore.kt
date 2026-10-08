package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.IrrigationMethod
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore-backed persistence for [FarmerProfile] — same pattern as
 * [FieldStateCache], so farmer/farm details entered in Farm Setup/Onboarding
 * survive app restarts and work with zero network, satisfying the
 * offline-first requirement for Phase 2's Farmer Profile / Farm Setup work.
 */
@Singleton
class FarmerProfileStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(profile: FarmerProfile) {
        dataStore.edit { it[Keys.FARMER_PROFILE] = json.encodeToString(CachedFarmerProfileDto.serializer(), profile.toDto()) }
    }

    suspend fun load(): FarmerProfile? {
        val raw = dataStore.data.map { it[Keys.FARMER_PROFILE] }.first() ?: return null
        return runCatching { json.decodeFromString(CachedFarmerProfileDto.serializer(), raw).toDomain() }.getOrNull()
    }

    private object Keys {
        val FARMER_PROFILE = stringPreferencesKey("farmer_profile")
    }
}

@Serializable
internal data class CachedFarmerProfileDto(
    val name: String,
    val phone: String,
    val location: String,
    val farmSizeAcres: Float,
    val crops: List<String>,
    val soilType: String,
    val seedlingStage: String,
    val state: String = "",
    val district: String = "",
    val taluka: String = "",
    val village: String = "",
    val irrigationMethod: String = IrrigationMethod.RAIN_FED.name,
    val cropVariety: String = "",
    val sowingDateMillis: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dateOfBirthMillis: Long? = null,
    val gender: String = "",
    val alternateMobile: String = "",
    val address: String = "",
    val ownershipType: String = "",
    val waterSource: String = "",
    val farmingExperienceYears: Int? = null,
    val expectedHarvestDateMillis: Long? = null,
    val voiceAssistanceEnabled: Boolean = true,
)

internal fun FarmerProfile.toDto() = CachedFarmerProfileDto(
    name = name,
    phone = phone,
    location = location,
    farmSizeAcres = farmSizeAcres,
    crops = crops,
    soilType = soilType,
    seedlingStage = seedlingStage,
    state = farmLocation.state,
    district = farmLocation.district,
    taluka = farmLocation.taluka,
    village = farmLocation.village,
    irrigationMethod = irrigationMethod.name,
    cropVariety = cropVariety,
    sowingDateMillis = sowingDate?.toEpochMilli(),
    latitude = farmLocation.latitude,
    longitude = farmLocation.longitude,
    dateOfBirthMillis = dateOfBirth?.toEpochMilli(),
    gender = gender,
    alternateMobile = alternateMobile,
    address = address,
    ownershipType = ownershipType,
    waterSource = waterSource,
    farmingExperienceYears = farmingExperienceYears,
    expectedHarvestDateMillis = expectedHarvestDate?.toEpochMilli(),
    voiceAssistanceEnabled = voiceAssistanceEnabled,
)

internal fun CachedFarmerProfileDto.toDomain() = FarmerProfile(
    name = name,
    phone = phone,
    location = location,
    farmSizeAcres = farmSizeAcres,
    crops = crops,
    soilType = soilType,
    seedlingStage = seedlingStage,
    farmLocation = FarmLocation(state = state, district = district, taluka = taluka, village = village, latitude = latitude, longitude = longitude),
    irrigationMethod = runCatching { IrrigationMethod.valueOf(irrigationMethod) }.getOrDefault(IrrigationMethod.RAIN_FED),
    cropVariety = cropVariety,
    sowingDate = sowingDateMillis?.let(Instant::ofEpochMilli),
    dateOfBirth = dateOfBirthMillis?.let(Instant::ofEpochMilli),
    gender = gender,
    alternateMobile = alternateMobile,
    address = address,
    ownershipType = ownershipType,
    waterSource = waterSource,
    farmingExperienceYears = farmingExperienceYears,
    expectedHarvestDate = expectedHarvestDateMillis?.let(Instant::ofEpochMilli),
    voiceAssistanceEnabled = voiceAssistanceEnabled,
)
