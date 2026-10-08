package com.krishinirnay.data

import com.krishinirnay.core.data.local.CachedFarmerProfileDto
import com.krishinirnay.core.data.local.toDomain
import com.krishinirnay.core.data.local.toDto
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.IrrigationMethod
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [com.krishinirnay.core.data.firebase.FarmerCloudProfileRepository] stores a
 * profile as this same JSON blob in Firestore's "profileJson" field — this
 * confirms the encode/decode round-trip it depends on actually works,
 * without needing a real Firestore instance.
 */
class FarmerCloudProfileMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `profile roundtrips through the same JSON encoding used for Firestore sync`() {
        val profile = FarmerProfile(
            name = "Sanjay Patil",
            phone = "+91 98765 43210",
            location = "Kolhapur, Maharashtra",
            farmSizeAcres = 3.5f,
            crops = listOf("Cotton"),
            soilType = "Black Soil",
            seedlingStage = "Flowering",
            farmLocation = FarmLocation(
                state = "Maharashtra", district = "Nagpur", taluka = "Kamptee", village = "Sonegaon",
                latitude = 21.15, longitude = 79.09,
            ),
            irrigationMethod = IrrigationMethod.DRIP,
            cropVariety = "Bt Cotton",
        )

        val raw = json.encodeToString(CachedFarmerProfileDto.serializer(), profile.toDto())
        val restored = json.decodeFromString(CachedFarmerProfileDto.serializer(), raw).toDomain()

        assertEquals(profile, restored)
    }
}
