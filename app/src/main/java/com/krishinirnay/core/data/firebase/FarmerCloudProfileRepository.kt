package com.krishinirnay.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.krishinirnay.core.data.local.CachedFarmerProfileDto
import com.krishinirnay.core.data.local.toDomain
import com.krishinirnay.core.data.local.toDto
import com.krishinirnay.core.data.model.FarmerProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

private const val COLLECTION = "farmers"

/**
 * The Firestore side of a farmer's account — separate from
 * [com.krishinirnay.core.data.repository.ProfileRepository] (the local,
 * always-available profile every screen actually reads/writes) so a
 * Firestore outage never breaks the app; a failed [saveProfile] just means
 * this device's edit hasn't reached the cloud yet, and a failed
 * [fetchProfile] is treated the same as "nothing saved" rather than
 * crashing login. Every field of [FarmerProfile] round-trips through the
 * same [CachedFarmerProfileDto] JSON mapping already used for local
 * DataStore persistence, so this stays in sync with the model automatically
 * instead of needing a second hand-written field list.
 */
@Singleton
class FarmerCloudProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fire-and-forget from [com.krishinirnay.core.data.local.FarmerProfileRepositoryImpl.updateProfile] on every save. */
    suspend fun saveProfile(uid: String, profile: FarmerProfile): Result<Unit> = runCatching {
        val data = mapOf(
            "uid" to uid,
            "updatedAt" to Timestamp.now(),
            "profileJson" to json.encodeToString(CachedFarmerProfileDto.serializer(), profile.toDto()),
        )
        firestore.collection(COLLECTION).document(uid).set(data).await()
        Unit
    }

    /** Returns the farmer's saved profile, or null if this account has no cloud document yet (never fabricated). */
    suspend fun fetchProfile(uid: String): FarmerProfile? = runCatching {
        val snapshot = firestore.collection(COLLECTION).document(uid).get().await()
        val raw = snapshot.getString("profileJson") ?: return@runCatching null
        json.decodeFromString(CachedFarmerProfileDto.serializer(), raw).toDomain()
    }.getOrNull()
}
