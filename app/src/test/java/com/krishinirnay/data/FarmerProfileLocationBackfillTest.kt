package com.krishinirnay.data

import com.krishinirnay.core.data.local.withBackfilledFarmLocation
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the root cause of "live weather never appears in the app".
 *
 * Weather and market requests are built only from [FarmerProfile.farmLocation],
 * and `LiveWeatherRepositoryImpl` skips the request entirely when
 * `farmLocation.isUsable()` is false. Profiles persisted before the structured
 * [FarmLocation] existed carry a populated legacy `location` display string but a
 * blank `farmLocation` — so live weather silently never even attempted a request,
 * no matter how healthy the backend was. Because a stored profile always replaces
 * the seeded default, fixing the default alone did nothing for an existing install.
 *
 * The migration is a pure function, so it is tested directly rather than through
 * DataStore and the repository's async init (which is a race, not a behaviour).
 */
class FarmerProfileLocationBackfillTest {

    private fun legacyProfile() = FarmerProfile(
        name = "Sanjay Patil",
        phone = "+91 98765 43210",
        location = "Kolhapur, Maharashtra",
        farmSizeAcres = 2f,
        crops = listOf("Soybean"),
        // Exactly the broken shape: legacy display string set, structured blank.
        farmLocation = FarmLocation(),
    )

    @Test
    fun `a legacy profile with a blank farmLocation is backfilled from the display string`() {
        val migrated = legacyProfile().withBackfilledFarmLocation()

        assertEquals("Maharashtra", migrated.farmLocation.state)
        assertEquals("Kolhapur", migrated.farmLocation.district)
        assertTrue(
            "weather only requests when the structured location is usable",
            migrated.farmLocation.isUsable(),
        )
    }

    @Test
    fun `the legacy display string itself is preserved, not moved or cleared`() {
        assertEquals("Kolhapur, Maharashtra", legacyProfile().withBackfilledFarmLocation().location)
    }

    @Test
    fun `every other profile field is preserved`() {
        val original = legacyProfile()
        val migrated = original.withBackfilledFarmLocation()

        assertEquals(original.name, migrated.name)
        assertEquals(original.phone, migrated.phone)
        assertEquals(original.crops, migrated.crops)
        assertEquals(original.farmSizeAcres, migrated.farmSizeAcres, 0f)
        assertEquals(original.soilType, migrated.soilType)
        assertEquals(original.seedlingStage, migrated.seedlingStage)
    }

    @Test
    fun `a farmer's own saved farmLocation is never overwritten by the stale display string`() {
        val edited = legacyProfile().copy(
            // Farmer moved and updated Farm Setup; the stale string must not win.
            farmLocation = FarmLocation(state = "Punjab", district = "Ludhiana"),
        )

        val migrated = edited.withBackfilledFarmLocation()

        assertSame("an already-usable profile is returned untouched", edited, migrated)
        assertEquals("Punjab", migrated.farmLocation.state)
        assertEquals("Ludhiana", migrated.farmLocation.district)
    }

    @Test
    fun `coordinates alone already count as usable and are left untouched`() {
        val withCoords = legacyProfile()
            .copy(farmLocation = FarmLocation(latitude = 16.705, longitude = 74.243))

        val migrated = withCoords.withBackfilledFarmLocation()

        assertSame(withCoords, migrated)
        assertEquals(16.705, migrated.farmLocation.latitude!!, 0.0001)
    }

    @Test
    fun `a profile with no location information at all is left alone, never invented`() {
        val blank = legacyProfile().copy(location = "")

        val migrated = blank.withBackfilledFarmLocation()

        assertSame(blank, migrated)
        assertEquals("", migrated.farmLocation.state)
        assertEquals("", migrated.farmLocation.district)
    }

    @Test
    fun `a single-word legacy location becomes the state with no district guessed`() {
        val migrated = legacyProfile().copy(location = "Maharashtra").withBackfilledFarmLocation()

        assertEquals("Maharashtra", migrated.farmLocation.state)
        assertEquals("", migrated.farmLocation.district)
    }

    @Test
    fun `a three-part legacy location uses the last two parts as district and state`() {
        val migrated = legacyProfile()
            .copy(location = "Wagholi, Pune, Maharashtra")
            .withBackfilledFarmLocation()

        assertEquals("Maharashtra", migrated.farmLocation.state)
        assertEquals("Pune", migrated.farmLocation.district)
    }

    @Test
    fun `extra whitespace and empty segments in the legacy string are tolerated`() {
        val migrated = legacyProfile()
            .copy(location = "  Kolhapur ,  Maharashtra ,")
            .withBackfilledFarmLocation()

        assertEquals("Maharashtra", migrated.farmLocation.state)
        assertEquals("Kolhapur", migrated.farmLocation.district)
    }

    @Test
    fun `a whitespace-only legacy location is not treated as a location`() {
        assertEquals("", legacyProfile().copy(location = "   ,  ").withBackfilledFarmLocation().farmLocation.state)
    }
}
