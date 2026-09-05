package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.WeatherState
import kotlinx.coroutines.flow.StateFlow

interface WeatherRepository {
    val weather: StateFlow<WeatherState>
}
