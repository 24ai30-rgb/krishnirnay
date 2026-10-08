package com.krishinirnay.feature.profile

import androidx.lifecycle.ViewModel
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ProfileViewModel @Inject constructor(
    profileRepository: ProfileRepository,
) : ViewModel() {
    val profile: StateFlow<FarmerProfile> = profileRepository.profile
}
