package com.krishinirnay.core.data.composite

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.di.LiveSource
import com.krishinirnay.core.data.di.MockSource
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Same Mock/Live switching pattern as [DefaultFieldStateRepository], reusing the same qualifiers. */
@Singleton
class DefaultMarketRepository @Inject constructor(
    @MockSource private val mock: MarketRepository,
    @LiveSource private val live: MarketRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : MarketRepository {

    override val market: StateFlow<MarketState> = settingsRepository.appMode
        .flatMapLatest { mode -> activeRepository(mode).market }
        .stateIn(scope, SharingStarted.Eagerly, mock.market.value)

    override suspend fun refresh() {
        activeRepository(settingsRepository.appMode.value).refresh()
    }

    private fun activeRepository(mode: AppMode): MarketRepository = if (mode == AppMode.MOCK) mock else live
}
