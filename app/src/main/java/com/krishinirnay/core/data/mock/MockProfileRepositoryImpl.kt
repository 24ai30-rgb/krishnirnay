package com.krishinirnay.core.data.mock

import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Static demo profile — AuthUser only carries uid/email; no farmer-profile backend exists in Phase 1. */
@Singleton
class MockProfileRepositoryImpl @Inject constructor() : ProfileRepository {

    private val _profile = MutableStateFlow(
        FarmerProfile(
            name = "Sanjay Patil",
            phone = "+91 98765 43210",
            location = "Kolhapur, Maharashtra",
            farmSizeAcres = 2f,
            crops = listOf("Soybean", "Wheat", "Maize", "Cotton"),
            soilType = "Black Soil",
            seedlingStage = "Germination",
        ),
    )

    override val profile: StateFlow<FarmerProfile> = _profile.asStateFlow()
}