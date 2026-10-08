package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.WeatherState
import kotlinx.coroutines.flow.StateFlow

interface WeatherRepository {
    val weather: StateFlow<WeatherState>

    /** Re-fetch now (e.g. a pull-to-refresh gesture). No-op in Mock Mode. */
    suspend fun refresh()
}
