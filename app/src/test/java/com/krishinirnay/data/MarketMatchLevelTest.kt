package com.krishinirnay.data

import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.MarketMatchLevel
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.matchLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The server's hard rule already guarantees a successful [MarketState] never
 * belongs to another state — this only distinguishes "the farmer's own
 * district reported" from "a different district in their own state did",
 * so the farmer always sees which real mandi/district actually priced them.
 */
class MarketMatchLevelTest {

    private fun state(district: String?) = MarketState(
        crop = "Soybean",
        market = "APMC Udgir",
        location = "$district, Maharashtra",
        currentPricePerQuintal = 5850f,
        minPricePerQuintal = 5800f,
        maxPricePerQuintal = 5900f,
        averagePricePerQuintal = null,
        fetchedAt = null,
        source = "data.gov.in (AGMARKNET)",
        status = DataSourceStatus.LIVE,
        district = district,
        state = "Maharashtra",
    )

    @Test
    fun `the farmer's own district reporting is SAME_DISTRICT`() {
        assertEquals(MarketMatchLevel.SAME_DISTRICT, state("Raigad").matchLevel(farmerDistrict = "Raigad"))
    }

    @Test
    fun `the comparison is case-insensitive`() {
        assertEquals(MarketMatchLevel.SAME_DISTRICT, state("RAIGAD").matchLevel(farmerDistrict = "raigad"))
    }

    @Test
    fun `a different district in the same state is SAME_STATE, not a mismatch`() {
        assertEquals(MarketMatchLevel.SAME_STATE, state("Latur").matchLevel(farmerDistrict = "Raigad"))
    }

    @Test
    fun `a missing real district is UNKNOWN, never guessed as a match`() {
        assertEquals(MarketMatchLevel.UNKNOWN, state(null).matchLevel(farmerDistrict = "Raigad"))
    }

    @Test
    fun `a missing saved farmer district is UNKNOWN`() {
        assertEquals(MarketMatchLevel.UNKNOWN, state("Latur").matchLevel(farmerDistrict = ""))
    }

    @Test
    fun `surrounding whitespace never causes a false mismatch`() {
        assertEquals(MarketMatchLevel.SAME_DISTRICT, state(" Raigad ").matchLevel(farmerDistrict = "Raigad "))
    }
}
