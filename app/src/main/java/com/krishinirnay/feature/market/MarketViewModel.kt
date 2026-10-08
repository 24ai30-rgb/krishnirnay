package com.krishinirnay.feature.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MarketUiState(
    val market: MarketState? = null,
    /** The farmer's own saved district — for [com.krishinirnay.core.data.model.matchLevel], not re-derived from the response. */
    val farmerDistrict: String = "",
)

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<MarketUiState> = combine(
        marketRepository.market,
        profileRepository.profile,
    ) { market, profile -> MarketUiState(market = market, farmerDistrict = profile.farmLocation.district) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketUiState())

    fun refresh() {
        viewModelScope.launch { marketRepository.refresh() }
    }
}
