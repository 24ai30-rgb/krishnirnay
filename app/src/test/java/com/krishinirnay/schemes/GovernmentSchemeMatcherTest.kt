package com.krishinirnay.schemes

import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.GovtScheme
import com.krishinirnay.core.schemes.GovernmentSchemeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic eligibility only — no LLM judgment call. Mirrors the exact
 * example in the Phase 4H brief: Maharashtra, Cotton, 3 acres.
 */
class GovernmentSchemeMatcherTest {

    private fun profile(state: String = "", crop: String? = null, acres: Float = 3f) = FarmerProfile(
        name = "Test", phone = "0", location = "", farmSizeAcres = acres,
        crops = if (crop == null) emptyList() else listOf(crop),
        farmLocation = FarmLocation(state = state),
    )

    private val universalScheme = GovtScheme(
        id = "universal", name = "Universal Scheme", benefit = "x", description = "x", eligibility = "x",
    )

    private val cottonMaharashtraScheme = GovtScheme(
        id = "cotton-mh", name = "Cotton Maharashtra Scheme", benefit = "x", description = "x", eligibility = "x",
        applicableStates = listOf("Maharashtra"), applicableCrops = listOf("Cotton"), minLandAcres = 1f,
    )

    // Eligible farmer: Maharashtra, Cotton, 3 acres — the brief's own example.
    @Test
    fun `an eligible farmer matches both a universal scheme and a criteria-restricted one`() {
        val result = GovernmentSchemeMatcher.match(
            profile(state = "Maharashtra", crop = "Cotton", acres = 3f),
            listOf(universalScheme, cottonMaharashtraScheme),
        )

        assertEquals(2, result.size)
        assertTrue(result.any { it.scheme.id == "cotton-mh" })
    }

    // Non-eligible farmer: wrong state.
    @Test
    fun `a farmer in a different state does not match a state-restricted scheme`() {
        val result = GovernmentSchemeMatcher.match(
            profile(state = "Punjab", crop = "Cotton", acres = 3f),
            listOf(cottonMaharashtraScheme),
        )
        assertTrue(result.isEmpty())
    }

    // Non-eligible farmer: wrong crop.
    @Test
    fun `a farmer growing a different crop does not match a crop-restricted scheme`() {
        val result = GovernmentSchemeMatcher.match(
            profile(state = "Maharashtra", crop = "Wheat", acres = 3f),
            listOf(cottonMaharashtraScheme),
        )
        assertTrue(result.isEmpty())
    }

    // Non-eligible farmer: below minimum land size.
    @Test
    fun `a farmer below the minimum land size does not match`() {
        val result = GovernmentSchemeMatcher.match(
            profile(state = "Maharashtra", crop = "Cotton", acres = 0.5f),
            listOf(cottonMaharashtraScheme),
        )
        assertTrue(result.isEmpty())
    }

    // Missing data: no state/crop set at all — a restricted scheme honestly doesn't match, never guessed.
    @Test
    fun `missing profile data never guesses a match for a restricted scheme`() {
        val result = GovernmentSchemeMatcher.match(profile(), listOf(cottonMaharashtraScheme))
        assertTrue(result.isEmpty())
    }

    // Missing data still lets a universal (no-restriction) scheme match.
    @Test
    fun `missing profile data still matches an unrestricted scheme`() {
        val result = GovernmentSchemeMatcher.match(profile(), listOf(universalScheme))
        assertEquals(1, result.size)
    }

    // Multiple matching schemes are all returned, each with its own reasons.
    @Test
    fun `multiple matching schemes are all returned with distinct reasons`() {
        val secondUniversal = universalScheme.copy(id = "universal-2", name = "Second Universal Scheme")
        val result = GovernmentSchemeMatcher.match(
            profile(state = "Maharashtra", crop = "Cotton", acres = 3f),
            listOf(universalScheme, secondUniversal, cottonMaharashtraScheme),
        )
        assertEquals(3, result.size)
        assertTrue(result.all { it.reasons.isNotEmpty() })
    }
}
