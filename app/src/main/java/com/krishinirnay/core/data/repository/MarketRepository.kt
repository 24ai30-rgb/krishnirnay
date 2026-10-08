package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.MarketState
import kotlinx.coroutines.flow.StateFlow

interface MarketRepository {
    val market: StateFlow<MarketState>

    /** Re-fetch now (e.g. a pull-to-refresh gesture). No-op in Mock Mode. */
    suspend fun refresh()
}
