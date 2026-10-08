package com.krishinirnay.core.data.composite

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.di.LiveSource
import com.krishinirnay.core.data.di.MockSource
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Same Mock/Live switching pattern as [DefaultFieldStateRepository], reusing the same qualifiers. */
@Singleton
class DefaultWeatherRepository @Inject constructor(
    @MockSource private val mock: WeatherRepository,
    @LiveSource private val live: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : WeatherRepository {

    override val weather: StateFlow<WeatherState> = settingsRepository.appMode
        .flatMapLatest { mode -> activeRepository(mode).weather }
        .stateIn(scope, SharingStarted.Eagerly, mock.weather.value)

    override suspend fun refresh() {
        activeRepository(settingsRepository.appMode.value).refresh()
    }

    private fun activeRepository(mode: AppMode): WeatherRepository = if (mode == AppMode.MOCK) mock else live
}
