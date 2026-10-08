package com.krishinirnay.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.krishinirnay.core.data.local.MarketStateCache
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.network.LiveMarketRepositoryImpl
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.network.MarketApiService
import com.krishinirnay.core.network.dto.MandiRecordDto
import com.krishinirnay.core.network.dto.MarketResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

private fun profileWithCrop(crop: String?, state: String = "", district: String = "") = FarmerProfile(
    name = "Test",
    phone = "0",
    location = "",
    farmSizeAcres = 1f,
    crops = if (crop == null) emptyList() else listOf(crop),
    farmLocation = FarmLocation(state = state, district = district),
)

private fun noopCache(): MarketStateCache {
    val cache = mockk<MarketStateCache>()
    coEvery { cache.load() } returns null
    coEvery { cache.save(any()) } returns Unit
    return cache
}

private fun realCache(tempDir: File) = MarketStateCache(
    PreferenceDataStoreFactory.create(
        scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
        produceFile = { File(tempDir, "market_test.preferences_pb") },
    ),
)

private fun createTempDir(): File = File.createTempFile("market_cache_test", "").let {
    it.delete()
    it.mkdirs()
    it
}

class LiveMarketRepositoryImplTest {

    @Test
    fun `no primary crop set is honestly UNAVAILABLE, never a fabricated price`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop(null))

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.UNAVAILABLE, repo.market.value.status)
        assertNull(repo.market.value.currentPricePerQuintal)
    }

    @Test
    fun `a failed poll after a real success keeps the last known price but marks it CACHED`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton"))

        coEvery { marketApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } returns Response.success(
            MarketResponseDto(crop = "Cotton", current_price_per_quintal = 7200f, source = "test-provider"),
        )

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.LIVE, repo.market.value.status)
        assertEquals(7200f, repo.market.value.currentPricePerQuintal)

        coEvery { marketApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } throws RuntimeException("unreachable")
        repo.refresh()

        assertEquals(7200f, repo.market.value.currentPricePerQuintal)
        assertEquals(DataSourceStatus.CACHED, repo.market.value.status)
    }

    // TEST 6: Successful Market data is persisted.
    @Test
    fun `a successful fetch persists the price to MarketStateCache`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton"))
        coEvery { marketApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } returns Response.success(
            MarketResponseDto(crop = "Cotton", current_price_per_quintal = 7500f, source = "test-provider"),
        )

        val cache = noopCache()
        LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, cache)

        coVerify { cache.save(match { it.currentPricePerQuintal == 7500f && it.status == DataSourceStatus.LIVE }) }
    }

    // TEST 7 + TEST 8: Persisted Market data survives repository recreation, and is reloaded as CACHED.
    @Test
    fun `persisted market data survives repository recreation and reloads as CACHED`() = runTest(UnconfinedTestDispatcher()) {
        val tempDir = createTempDir()
        val cache = realCache(tempDir)

        val firstProcessApiService = mockk<MarketApiService>()
        val firstProcessProfile = mockk<ProfileRepository>()
        every { firstProcessProfile.profile } returns MutableStateFlow(profileWithCrop("Cotton"))
        coEvery { firstProcessApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } returns Response.success(
            MarketResponseDto(crop = "Cotton", current_price_per_quintal = 6800f, source = "test-provider"),
        )

        val firstRunRepo = LiveMarketRepositoryImpl(backgroundScope, firstProcessApiService, firstProcessProfile, cache)
        assertEquals(DataSourceStatus.LIVE, firstRunRepo.market.value.status)
        assertEquals(6800f, firstRunRepo.market.value.currentPricePerQuintal)

        // "App restart": a brand new repository instance, sharing only the on-disk cache.
        val secondProcessApiService = mockk<MarketApiService>()
        val secondProcessProfile = mockk<ProfileRepository>()
        every { secondProcessProfile.profile } returns MutableStateFlow(profileWithCrop("Cotton"))
        coEvery { secondProcessApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } throws RuntimeException("unreachable after restart")

        val afterRestartRepo = LiveMarketRepositoryImpl(backgroundScope, secondProcessApiService, secondProcessProfile, cache)

        assertEquals(6800f, afterRestartRepo.market.value.currentPricePerQuintal)
        assertEquals(DataSourceStatus.CACHED, afterRestartRepo.market.value.status)
    }

    // TEST 9: Market provider failure does not erase data restored from cache (the exact bug fixed).
    @Test
    fun `a fetch failure never erases a price that was restored from cache`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton"))
        coEvery { marketApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } throws RuntimeException("provider not configured")

        val restoredFromDisk = MarketState(
            crop = "Cotton",
            market = "Nagpur APMC",
            location = "Vidarbha",
            currentPricePerQuintal = 7100f,
            minPricePerQuintal = null,
            maxPricePerQuintal = null,
            averagePricePerQuintal = null,
            fetchedAt = null,
            source = "test-provider",
            status = DataSourceStatus.CACHED,
        )
        val cache = mockk<MarketStateCache>()
        coEvery { cache.load() } returns restoredFromDisk
        coEvery { cache.save(any()) } returns Unit

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, cache)

        assertEquals(7100f, repo.market.value.currentPricePerQuintal)
        assertEquals(DataSourceStatus.CACHED, repo.market.value.status)
    }

    // TEST 10: No cached Market data + provider unavailable = UNAVAILABLE.
    @Test
    fun `no cache and an unreachable provider settles to UNAVAILABLE, not a fabricated price`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton"))
        coEvery { marketApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } throws RuntimeException("provider not configured")

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.UNAVAILABLE, repo.market.value.status)
        assertNull(repo.market.value.currentPricePerQuintal)
    }

    // Phase 4D TEST 18/19: the farmer's actual saved location and crop are sent
    // to the server — never a hardcoded region.
    @Test
    fun `the farmer's actual state and district are sent to the server`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(
            profileWithCrop("Cotton", state = "Maharashtra", district = "Amravati"),
        )
        coEvery {
            marketApiService.getMarketPrice(crop = "Cotton", state = "Maharashtra", district = "Amravati")
        } returns Response.success(MarketResponseDto(crop = "Cotton", source = "test-provider"))

        LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        coVerify {
            marketApiService.getMarketPrice(crop = "Cotton", state = "Maharashtra", district = "Amravati")
        }
    }

    // Phase 4D TEST 6: multiple mandis + a real trend map through into MarketState honestly.
    @Test
    fun `multiple mandis and a real trend are mapped into MarketState`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton"))

        val records = listOf(
            MandiRecordDto(
                market = "Amravati", district = "Amravati", state = "Maharashtra", commodity = "Cotton",
                arrival_date = "10/09/2026", min_price = 6800f, max_price = 7600f, modal_price = 7200f,
            ),
            MandiRecordDto(
                market = "Akola", district = "Akola", state = "Maharashtra", commodity = "Cotton",
                arrival_date = "10/09/2026", min_price = 6900f, max_price = 7700f, modal_price = 7500f,
            ),
        )
        coEvery { marketApiService.getMarketPrice(crop = "Cotton", state = null, district = null) } returns Response.success(
            MarketResponseDto(
                crop = "Cotton", market = "Akola", current_price_per_quintal = 7500f,
                source = "data.gov.in (AGMARKNET)", markets = records, trend = "RISING",
            ),
        )

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(2, repo.market.value.markets.size)
        assertEquals(MarketTrend.RISING, repo.market.value.trend)
        assertEquals("Akola", repo.market.value.market)
    }

    // A 503 from our own server means it reached data.gov.in and answered
    // honestly that today's snapshot has no row for this crop/state. Nothing is
    // broken and there is nothing to retry, so the farmer must see "no data
    // today" rather than the old "no live data source is configured".
    @Test
    fun `a 503 from our server is NO_DATA, not a failure`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Soybean", state = "Maharashtra"))
        coEvery {
            marketApiService.getMarketPrice(crop = "Soybean", state = "Maharashtra", district = null)
        } returns Response.error(503, mockk<okhttp3.ResponseBody>(relaxed = true))

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.NO_DATA, repo.market.value.status)
        assertNull("no price may be invented for a no-data day", repo.market.value.currentPricePerQuintal)
    }

    // Being unable to reach our own server is a real failure the farmer can
    // retry — it must not be reported as "no data today".
    @Test
    fun `an unreachable server is UNAVAILABLE, not NO_DATA`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton", state = "Maharashtra"))
        coEvery {
            marketApiService.getMarketPrice(crop = "Cotton", state = "Maharashtra", district = null)
        } throws RuntimeException("connection refused")

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.UNAVAILABLE, repo.market.value.status)
    }

    // A real price already on screen is more useful than "no data today", so a
    // later empty day keeps it and marks it CACHED.
    @Test
    fun `a no-data day keeps a real price already on screen and marks it CACHED`() =
        runTest(UnconfinedTestDispatcher()) {
            val marketApiService = mockk<MarketApiService>()
            val profileRepository = mockk<ProfileRepository>()
            every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton", state = "Maharashtra"))
            coEvery {
                marketApiService.getMarketPrice(crop = "Cotton", state = "Maharashtra", district = null)
            } returns Response.success(
                MarketResponseDto(crop = "Cotton", current_price_per_quintal = 7200f, source = "test-provider"),
            )

            val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())
            assertEquals(DataSourceStatus.LIVE, repo.market.value.status)

            coEvery {
                marketApiService.getMarketPrice(crop = "Cotton", state = "Maharashtra", district = null)
            } returns Response.error(503, mockk<okhttp3.ResponseBody>(relaxed = true))
            repo.refresh()

            assertEquals(DataSourceStatus.CACHED, repo.market.value.status)
            assertEquals(7200f, repo.market.value.currentPricePerQuintal)
        }

    // Placeholder nulls must never be dressed up as a "last known reading".
    @Test
    fun `a failure with nothing ever loaded never claims CACHED`() = runTest(UnconfinedTestDispatcher()) {
        val marketApiService = mockk<MarketApiService>()
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profileWithCrop("Cotton", state = "Maharashtra"))
        coEvery {
            marketApiService.getMarketPrice(crop = "Cotton", state = "Maharashtra", district = null)
        } throws RuntimeException("boom")

        val repo = LiveMarketRepositoryImpl(backgroundScope, marketApiService, profileRepository, noopCache())

        assertEquals(DataSourceStatus.UNAVAILABLE, repo.market.value.status)
        assertNull(repo.market.value.currentPricePerQuintal)
    }
}
