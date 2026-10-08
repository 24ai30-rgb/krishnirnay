package com.krishinirnay.core.data.model

import java.time.Instant

/** Deterministic only (see `server/app/services/market_provider.py`'s `_compute_trend` — never
 * an LLM guess): compares the two most recently dated records for the same market. UNKNOWN
 * whenever there isn't enough real historical data to compare. */
enum class MarketTrend {
    RISING,
    STABLE,
    FALLING,
    UNKNOWN,
}

/** One market's raw price row — lets the UI compare mandis instead of only ever showing one. */
data class MandiPrice(
    val market: String,
    val district: String,
    val state: String,
    val commodity: String,
    val variety: String?,
    val grade: String?,
    val arrivalDate: String?,
    val minPricePerQuintal: Float?,
    val maxPricePerQuintal: Float?,
    val modalPricePerQuintal: Float?,
)

/**
 * One crop's market snapshot. [status] follows the same LIVE/CACHED/MOCK/UNAVAILABLE
 * contract as [DataSourceStatus] everywhere else — see [com.krishinirnay.core.data.repository.MarketRepository].
 * Never populated with invented prices: absent a real data source this stays
 * [DataSourceStatus.UNAVAILABLE] with null price fields, not a guessed number.
 *
 * [currentPricePerQuintal] is the modal price of the strongest available market (highest
 * modal price among [markets], per Phase 4D) — never a guaranteed farm-gate selling price,
 * only the government mandi's reported daily modal price. [markets] carries every mandi the
 * provider returned so the UI can show a comparison, not just the single best one.
 */
data class MarketState(
    val crop: String,
    val market: String?,
    val location: String?,
    val currentPricePerQuintal: Float?,
    val minPricePerQuintal: Float?,
    val maxPricePerQuintal: Float?,
    val averagePricePerQuintal: Float?,
    val fetchedAt: Instant?,
    val source: String?,
    val status: DataSourceStatus,
    val arrivalDate: String? = null,
    val variety: String? = null,
    val grade: String? = null,
    val district: String? = null,
    val state: String? = null,
    val markets: List<MandiPrice> = emptyList(),
    val trend: MarketTrend = MarketTrend.UNKNOWN,
)
