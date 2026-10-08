package com.krishinirnay.core.data.local

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.firebase.FarmerCloudProfileRepository
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real, persisted [ProfileRepository] — replaces the old
 * `MockProfileRepositoryImpl` fixture. Seeds with the exact same demo values
 * that fixture used (so nothing regresses for a farmer who hasn't touched
 * Farm Setup yet), then persists every [updateProfile] call to
 * [FarmerProfileStore] (DataStore/JSON) so it survives restarts with zero
 * network — this is real farmer-entered data, not a "Mock Mode" concept, so
 * it applies regardless of the app's Mock/Live sensor toggle. Also pushes
 * every update to [FarmerCloudProfileRepository] (best-effort, never blocks
 * the caller) so Farm Setup/Onboarding edits reach Firestore too, not just
 * the registration-time snapshot.
 */
@Singleton
class FarmerProfileRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val store: FarmerProfileStore,
    private val authRepository: AuthRepository,
    private val cloudProfileRepository: FarmerCloudProfileRepository,
) : ProfileRepository {

    private val _profile = MutableStateFlow(defaultProfile())
    override val profile: StateFlow<FarmerProfile> = _profile.asStateFlow()

    init {
        scope.launch {
            store.load()?.let { saved ->
                val migrated = saved.withBackfilledFarmLocation()
                _profile.value = migrated
                // Persist the backfill once so later reads (and the server
                // requests built from them) stay consistent.
                if (migrated != saved) store.save(migrated)
            }
        }
    }

    override suspend fun updateProfile(profile: FarmerProfile) {
        _profile.value = profile
        store.save(profile)
        authRepository.currentUser.value?.uid?.let { uid ->
            scope.launch { cloudProfileRepository.saveProfile(uid, profile) }
        }
    }

    private companion object {

        fun defaultProfile() = FarmerProfile(
            name = "Sanjay Patil",
            phone = "+91 98765 43210",
            location = "Kolhapur, Maharashtra",
            farmSizeAcres = 2f,
            crops = listOf("Soybean", "Wheat", "Maize", "Cotton"),
            soilType = "Black Soil",
            seedlingStage = "Germination",
            // Must agree with `location` above: live weather/market read the
            // structured farmLocation, not the legacy display string, and
            // LiveWeatherRepositoryImpl skips fetching entirely when
            // FarmLocation.isUsable() is false. Leaving this blank while
            // `location` claimed a real place meant live weather never even
            // attempted a request until the farmer opened Farm Setup.
            farmLocation = FarmLocation(state = "Maharashtra", district = "Kolhapur"),
        )
    }
}

/**
 * One-time migration for profiles saved before the structured [FarmLocation]
 * existed.
 *
 * Weather and market build their requests from [FarmerProfile.farmLocation], and
 * `LiveWeatherRepositoryImpl` skips the request entirely when
 * `farmLocation.isUsable()` is false. A profile persisted earlier has a populated
 * legacy [FarmerProfile.location] display string ("Kolhapur, Maharashtra") but a
 * blank `farmLocation`, so live weather never even attempted a request — no
 * matter how healthy the backend was. Fixing only `defaultProfile()` helped a
 * clean install and nothing else, because a stored profile always replaces the
 * default.
 *
 * Parses the legacy "District, State" display string rather than inventing
 * anything, and never overwrites a `farmLocation` the farmer has already filled
 * in. Top-level and `internal` so it can be unit-tested as the pure function it
 * is, instead of through DataStore and a coroutine race.
 */
internal fun FarmerProfile.withBackfilledFarmLocation(): FarmerProfile {
    if (farmLocation.isUsable() || location.isBlank()) return this
    val parts = location.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val state = parts.lastOrNull().orEmpty()
    if (state.isBlank()) return this
    val district = if (parts.size >= 2) parts[parts.size - 2] else ""
    return copy(farmLocation = farmLocation.copy(state = state, district = district))
}
