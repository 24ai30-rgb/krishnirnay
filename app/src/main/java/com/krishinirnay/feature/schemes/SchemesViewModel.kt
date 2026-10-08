package com.krishinirnay.feature.schemes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SchemesRepository
import com.krishinirnay.core.schemes.GovernmentSchemeMatcher
import com.krishinirnay.core.schemes.MatchedScheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Matched deterministically by GovernmentSchemeMatcher against the farmer's
 * real profile — the LLM never decides eligibility. An empty result means
 * either no schemes are configured or none match this farmer's profile; the
 * screen shows an honest "no schemes available" message rather than the raw
 * static list, so a farmer never sees a scheme they don't actually qualify for.
 */
@HiltViewModel
class SchemesViewModel @Inject constructor(
    schemesRepository: SchemesRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val matchedSchemes: StateFlow<List<MatchedScheme>> = combine(
        schemesRepository.schemes,
        profileRepository.profile,
    ) { schemes, profile -> GovernmentSchemeMatcher.match(profile, schemes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
