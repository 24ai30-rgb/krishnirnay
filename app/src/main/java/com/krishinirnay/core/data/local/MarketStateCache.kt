package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.MandiPrice
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.MarketTrend
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore-backed persistence for the last known-good [MarketState] — same pattern
 * as [FieldStateCache]/[FarmerProfileStore]/[WeatherStateCache]. [status] is never
 * persisted; [load] always hands back [DataSourceStatus.CACHED] — only a genuinely
 * successful live fetch may mark it LIVE again.
 */
@Singleton
class MarketStateCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(state: MarketState) {
        dataStore.edit { it[Keys.CACHED_MARKET] = json.encodeToString(CachedMarketDto.serializer(), state.toDto()) }
    }

    suspend fun load(): MarketState? {
        val raw = dataStore.data.map { it[Keys.CACHED_MARKET] }.first() ?: return null
        return runCatching { json.decodeFromString(CachedMarketDto.serializer(), raw).toDomain() }.getOrNull()
    }

    private object Keys {
        val CACHED_MARKET = stringPreferencesKey("cached_market_state")
    }
}

@Serializable
private data class CachedMandiPriceDto(
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

@Serializable
private data class CachedMarketDto(
    val crop: String,
    val market: String?,
    val location: String?,
    val currentPricePerQuintal: Float?,
    val minPricePerQuintal: Float?,
    val maxPricePerQuintal: Float?,
    val averagePricePerQuintal: Float?,
    val source: String?,
    val fetchedAtMillis: Long?,
    val arrivalDate: String? = null,
    val variety: String? = null,
    val grade: String? = null,
    val district: String? = null,
    val state: String? = null,
    val markets: List<CachedMandiPriceDto> = emptyList(),
    val trend: String = "UNKNOWN",
)

private fun MandiPrice.toDto() = CachedMandiPriceDto(
    market = market,
    district = district,
    state = state,
    commodity = commodity,
    variety = variety,
    grade = grade,
    arrivalDate = arrivalDate,
    minPricePerQuintal = minPricePerQuintal,
    maxPricePerQuintal = maxPricePerQuintal,
    modalPricePerQuintal = modalPricePerQuintal,
)

private fun CachedMandiPriceDto.toDomain() = MandiPrice(
    market = market,
    district = district,
    state = state,
    commodity = commodity,
    variety = variety,
    grade = grade,
    arrivalDate = arrivalDate,
    minPricePerQuintal = minPricePerQuintal,
    maxPricePerQuintal = maxPricePerQuintal,
    modalPricePerQuintal = modalPricePerQuintal,
)

private fun MarketState.toDto() = CachedMarketDto(
    crop = crop,
    market = market,
    location = location,
    currentPricePerQuintal = currentPricePerQuintal,
    minPricePerQuintal = minPricePerQuintal,
    maxPricePerQuintal = maxPricePerQuintal,
    averagePricePerQuintal = averagePricePerQuintal,
    source = source,
    fetchedAtMillis = fetchedAt?.toEpochMilli(),
    arrivalDate = arrivalDate,
    variety = variety,
    grade = grade,
    district = district,
    state = state,
    markets = markets.map { it.toDto() },
    trend = trend.name,
)

private fun trendFromOrDefault(name: String): MarketTrend =
    runCatching { MarketTrend.valueOf(name) }.getOrDefault(MarketTrend.UNKNOWN)

private fun CachedMarketDto.toDomain() = MarketState(
    crop = crop,
    market = market,
    location = location,
    currentPricePerQuintal = currentPricePerQuintal,
    minPricePerQuintal = minPricePerQuintal,
    maxPricePerQuintal = maxPricePerQuintal,
    averagePricePerQuintal = averagePricePerQuintal,
    fetchedAt = fetchedAtMillis?.let(Instant::ofEpochMilli),
    source = source,
    status = DataSourceStatus.CACHED,
    arrivalDate = arrivalDate,
    variety = variety,
    grade = grade,
    district = district,
    state = state,
    markets = markets.map { it.toDomain() },
    trend = trendFromOrDefault(trend),
)
