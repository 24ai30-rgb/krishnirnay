package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.FarmerProfile
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val profile: StateFlow<FarmerProfile>
}
