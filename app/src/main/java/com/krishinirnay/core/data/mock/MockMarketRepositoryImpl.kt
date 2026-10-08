package com.krishinirnay.core.data.mock

import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.MandiPrice
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.repository.MarketRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Static demo market snapshot, explicitly [DataSourceStatus.MOCK] — no real market-price API is wired up. */
@Singleton
class MockMarketRepositoryImpl @Inject constructor() : MarketRepository {

    private val demoMarkets = listOf(
        MandiPrice(
            market = "Nagpur APMC", district = "Nagpur", state = "Maharashtra", commodity = "Cotton",
            variety = "H-4", grade = "FAQ", arrivalDate = "Demo data",
            minPricePerQuintal = 6800f, maxPricePerQuintal = 7600f, modalPricePerQuintal = 7200f,
        ),
        MandiPrice(
            market = "Akola APMC", district = "Akola", state = "Maharashtra", commodity = "Cotton",
            variety = "H-4", grade = "FAQ", arrivalDate = "Demo data",
            minPricePerQuintal = 6900f, maxPricePerQuintal = 7700f, modalPricePerQuintal = 7300f,
        ),
    )

    private val _market = MutableStateFlow(
        MarketState(
            crop = "Cotton",
            market = "Akola APMC",
            location = "Akola, Maharashtra",
            currentPricePerQuintal = 7300f,
            minPricePerQuintal = 6900f,
            maxPricePerQuintal = 7700f,
            averagePricePerQuintal = 7150f,
            fetchedAt = Instant.now(),
            source = "Demo data",
            status = DataSourceStatus.MOCK,
            arrivalDate = "Demo data",
            variety = "H-4",
            grade = "FAQ",
            district = "Akola",
            state = "Maharashtra",
            markets = demoMarkets,
            trend = MarketTrend.STABLE,
        ),
    )
    override val market: StateFlow<MarketState> = _market.asStateFlow()

    override suspend fun refresh() = Unit
}
