package com.krishinirnay.core.data.network

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.local.MarketStateCache
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.MandiPrice
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.network.MarketApiService
import com.krishinirnay.core.network.dto.MandiRecordDto
import com.krishinirnay.core.network.dto.MarketResponseDto
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Calls the server's `GET /v1/market` whenever the farmer has a primary crop set, passing
 * along their `state`/`district` so a real provider can filter by farm location. As of
 * Phase 4D the server calls data.gov.in's real AGMARKNET mandi-price dataset — see
 * `server/app/services/market_provider.py` — and this stays honestly
 * [DataSourceStatus.UNAVAILABLE]/[DataSourceStatus.CACHED] whenever no key is configured,
 * no mandi matches, or the request fails — never a fabricated price.
 *
 * Phase 4A: the last successful reading is persisted via [MarketStateCache] (same
 * DataStore pattern as [com.krishinirnay.core.data.local.FieldStateCache]) and
 * restored on init, marked [DataSourceStatus.CACHED] — a restart no longer resets
 * straight to UNAVAILABLE.
 */
@Singleton
class LiveMarketRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val marketApiService: MarketApiService,
    private val profileRepository: ProfileRepository,
    private val marketStateCache: MarketStateCache,
) : MarketRepository {

    // LOADING, not UNAVAILABLE: a cold start is not a failure.
    private val _market = MutableStateFlow(unavailableState("", DataSourceStatus.LOADING))
    override val market: StateFlow<MarketState> = _market.asStateFlow()

    init {
        scope.launch {
            // Restore the last known-good reading first, so cold start never shows
            // UNAVAILABLE when a valid (if stale) reading actually exists.
            marketStateCache.load()?.let { cached ->
                _market.value = cached
            }

            profileRepository.profile
                .map { Triple(it.primaryCrop, it.farmLocation.state, it.farmLocation.district) }
                .distinctUntilChanged()
                .collect { (crop, state, district) -> fetchMarket(crop, state, district) }
        }
    }

    override suspend fun refresh() {
        val profile = profileRepository.profile.value
        fetchMarket(profile.primaryCrop, profile.farmLocation.state, profile.farmLocation.district)
    }

    private suspend fun fetchMarket(crop: String?, state: String, district: String) {
        if (crop.isNullOrBlank()) {
            android.util.Log.i("MARKET", "Skipping fetch: no primary crop set on the farmer profile")
            preserveAsCachedOrUnavailable(crop = _market.value.crop)
            return
        }

        if (_market.value.status == DataSourceStatus.UNAVAILABLE ||
            _market.value.status == DataSourceStatus.NO_DATA ||
            _market.value.status == DataSourceStatus.LOADING
        ) {
            _market.value = _market.value.copy(status = DataSourceStatus.LOADING)
        }

        // Safe diagnostic: the exact query sent to our own server. The provider
        // key lives server-side only, so there is nothing secret to redact here.
        android.util.Log.i(
            "MARKET",
            "Requesting /v1/market crop=$crop state=${state.ifBlank { "-" }} district=${district.ifBlank { "-" }}",
        )

        try {
            val response = marketApiService.getMarketPrice(
                crop = crop,
                state = state.ifBlank { null },
                district = district.ifBlank { null },
            )

            if (response.code() == 503) {
                // Our own server reached the provider and answered honestly that
                // it has no record for this crop/state in today's dataset (the
                // AGMARKNET snapshot only covers some crop/state pairs each day).
                // Nothing is broken and there is nothing to retry, so this is
                // NO_DATA rather than a failure. A developer-facing cause such as
                // an unset server key also lands here; the real reason is in the
                // server's own 503 message and log, never surfaced to the farmer.
                android.util.Log.i("MARKET", "No mandi records today for crop=$crop state=$state")
                settleAsNoDataOrCached(crop)
                return
            }

            if (!response.isSuccessful) {
                throw RuntimeException("Market API HTTP ${response.code()}")
            }

            val body = response.body() ?: throw RuntimeException("Empty market response")
            val next = body.toDomain()
            android.util.Log.i(
                "MARKET",
                "Live market OK: source=${body.source} crop=${body.crop} mandis=${body.markets.size}",
            )
            _market.value = next
            marketStateCache.save(next)
        } catch (error: Exception) {
            android.util.Log.e("MARKET", "Market fetch failed: ${error.message}", error)
            preserveAsCachedOrUnavailable(crop = crop)
        }
    }

    /**
     * A failed/skippable fetch must never erase a value the farmer could still see
     * before this call — whether that value just arrived LIVE or was already
     * CACHED (e.g. restored at cold start, or from an earlier failed attempt).
     * Only when there was genuinely nothing to preserve does this settle to
     * UNAVAILABLE.
     */
    private fun preserveAsCachedOrUnavailable(crop: String) {
        val current = _market.value
        // Only LIVE/CACHED hold real prices. LOADING/NO_DATA/UNAVAILABLE hold
        // nulls, so calling those CACHED would present "no price" as a last
        // known reading.
        val hasRealPrice = (current.status == DataSourceStatus.LIVE || current.status == DataSourceStatus.CACHED) &&
            current.currentPricePerQuintal != null
        _market.value = if (hasRealPrice) current.copy(status = DataSourceStatus.CACHED) else unavailableState(crop)
    }

    /**
     * The provider answered "no records today". A real price already on screen
     * stays visible as CACHED; otherwise the farmer is told plainly that today's
     * dataset has nothing for their crop and state.
     */
    private fun settleAsNoDataOrCached(crop: String) {
        val current = _market.value
        val hasRealPrice = (current.status == DataSourceStatus.LIVE || current.status == DataSourceStatus.CACHED) &&
            current.currentPricePerQuintal != null
        _market.value = if (hasRealPrice) {
            current.copy(status = DataSourceStatus.CACHED)
        } else {
            unavailableState(crop, DataSourceStatus.NO_DATA)
        }
    }

    private fun unavailableState(
        crop: String,
        status: DataSourceStatus = DataSourceStatus.UNAVAILABLE,
    ) = MarketState(
        crop = crop,
        market = null,
        location = null,
        currentPricePerQuintal = null,
        minPricePerQuintal = null,
        maxPricePerQuintal = null,
        averagePricePerQuintal = null,
        fetchedAt = null,
        source = null,
        status = status,
    )
}

private fun MarketResponseDto.toDomain(): MarketState = MarketState(
    crop = crop,
    market = market,
    location = location,
    currentPricePerQuintal = current_price_per_quintal,
    minPricePerQuintal = min_price_per_quintal,
    maxPricePerQuintal = max_price_per_quintal,
    averagePricePerQuintal = average_price_per_quintal,
    fetchedAt = Instant.now(),
    source = source,
    status = DataSourceStatus.LIVE,
    arrivalDate = arrival_date,
    variety = variety,
    grade = grade,
    district = district,
    state = state,
    markets = markets.map { it.toDomain() },
    trend = trendFromOrDefault(trend),
)

private fun MandiRecordDto.toDomain(): MandiPrice = MandiPrice(
    market = market,
    district = district,
    state = state,
    commodity = commodity,
    variety = variety,
    grade = grade,
    arrivalDate = arrival_date,
    minPricePerQuintal = min_price,
    maxPricePerQuintal = max_price,
    modalPricePerQuintal = modal_price,
)

private fun trendFromOrDefault(name: String): MarketTrend =
    runCatching { MarketTrend.valueOf(name) }.getOrDefault(MarketTrend.UNKNOWN)
