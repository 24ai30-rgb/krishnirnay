package com.krishinirnay.feature.weather

import androidx.lifecycle.ViewModel
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class WeatherViewModel @Inject constructor(
    weatherRepository: WeatherRepository,
) : ViewModel() {
    val weather: StateFlow<WeatherState> = weatherRepository.weather
}
