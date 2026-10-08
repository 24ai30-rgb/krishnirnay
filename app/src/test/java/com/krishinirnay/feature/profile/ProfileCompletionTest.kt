package com.krishinirnay.feature.profile

import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCompletionTest {

    private val blankProfile = FarmerProfile(
        name = "",
        phone = "",
        location = "",
        farmSizeAcres = 0f,
        crops = emptyList(),
    )

    @Test
    fun `a completely blank profile is 0 percent complete`() {
        val result = blankProfile.completion(EnglishStrings)
        assertEquals(0, result.percent)
        assertEquals(13, result.missingLabels.size)
    }

    @Test
    fun `filling every checked field reaches 100 percent with nothing missing`() {
        val full = blankProfile.copy(
            name = "Ramesh",
            phone = "9999999999",
            farmLocation = FarmLocation(state = "MH", district = "Pune", village = "Wagholi"),
            crops = listOf("Wheat"),
            farmSizeAcres = 2f,
            gender = "Male",
            address = "Near temple road",
            ownershipType = "Owned",
            waterSource = "Well",
            cropVariety = "Lokwan",
            farmingExperienceYears = 5,
        )
        val result = full.completion(EnglishStrings)
        assertEquals(100, result.percent)
        assertTrue(result.missingLabels.isEmpty())
    }

    @Test
    fun `soil type and irrigation method never count toward completion since their defaults are valid choices`() {
        // blankProfile never sets soilType/irrigationMethod, yet they carry real,
        // decision-relevant defaults (soilType = "Black Soil") — completion must not
        // treat an untouched default as "missing."
        val result = blankProfile.completion(EnglishStrings)
        assertTrue(result.missingLabels.none { it == EnglishStrings.farmSetupSoilType })
    }

    @Test
    fun `a partially filled profile is proportionally between 0 and 100`() {
        val partial = blankProfile.copy(name = "Ramesh", phone = "9999999999")
        val result = partial.completion(EnglishStrings)
        assertTrue(result.percent in 1..99)
        assertEquals(11, result.missingLabels.size)
    }
}
