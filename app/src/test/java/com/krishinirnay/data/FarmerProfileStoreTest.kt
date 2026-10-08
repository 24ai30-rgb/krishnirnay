package com.krishinirnay.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.krishinirnay.core.data.local.FarmerProfileStore
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.IrrigationMethod
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real DataStore backed by a temp file (no fakes) — this is the actual
 * persistence mechanism Farm Setup relies on for offline-first behavior:
 * a farmer's entered details must survive a process restart with zero
 * network.
 */
class FarmerProfileStoreTest {

    private fun newStore(tempDir: File) = FarmerProfileStore(
        PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
            produceFile = { File(tempDir, "test_farmer_profile.preferences_pb") },
        ),
    )

    @Test
    fun `load returns null before anything has been saved`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore(createTempDir())
        assertNull(store.load())
    }

    @Test
    fun `save then load roundtrips every Farm Setup field`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore(createTempDir())

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

        store.save(profile)
        val loaded = store.load()

        assertEquals(profile, loaded)
    }

    @Test
    fun `a profile with no saved coordinates roundtrips them as null, never a fabricated default`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = newStore(createTempDir())
            val profile = FarmerProfile(
                name = "Test", phone = "0", location = "", farmSizeAcres = 1f, crops = listOf("Cotton"),
                farmLocation = FarmLocation(state = "Maharashtra"),
            )

            store.save(profile)
            val loaded = store.load()

            assertNull(loaded?.farmLocation?.latitude)
            assertNull(loaded?.farmLocation?.longitude)
        }

    private fun createTempDir(): File = File.createTempFile("farmer_profile_test", "").let {
        it.delete()
        it.mkdirs()
        it
    }
}
