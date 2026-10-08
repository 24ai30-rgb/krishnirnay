package com.krishinirnay.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
) : ViewModel() {
    val weather: StateFlow<WeatherState> = weatherRepository.weather

    /** Pull-to-refresh on the Weather screen — same repository call the Dashboard's retry uses. */
    fun refresh() {
        viewModelScope.launch { weatherRepository.refresh() }
    }
}
