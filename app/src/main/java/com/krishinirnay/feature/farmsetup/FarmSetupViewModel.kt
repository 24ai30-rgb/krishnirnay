package com.krishinirnay.feature.farmsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FarmLocation
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.IrrigationMethod
import com.krishinirnay.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FarmSetupUiState(
    val profile: FarmerProfile = FarmerProfile(name = "", phone = "", location = "", farmSizeAcres = 0f, crops = emptyList()),
    val justSaved: Boolean = false,
)

/**
 * Reads/writes the same [ProfileRepository] the Profile screen and every
 * Decision Engine call site read — a farmer editing Farm Setup immediately
 * changes what Dashboard/Advisory/the risk model see, no separate draft state.
 */
@HiltViewModel
class FarmSetupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val justSaved = MutableStateFlow(false)

    val uiState: StateFlow<FarmSetupUiState> = profileRepository.profile
        .let { profileFlow ->
            kotlinx.coroutines.flow.combine(profileFlow, justSaved) { profile, saved ->
                FarmSetupUiState(profile = profile, justSaved = saved)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FarmSetupUiState())

    fun save(
        state: String,
        district: String,
        taluka: String,
        village: String,
        farmSizeAcres: Float,
        soilType: String,
        crop: String,
        cropVariety: String,
        irrigationMethod: IrrigationMethod,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        viewModelScope.launch {
            val current = profileRepository.profile.value
            profileRepository.updateProfile(
                current.copy(
                    farmLocation = FarmLocation(
                        state = state,
                        district = district,
                        taluka = taluka,
                        village = village,
                        latitude = latitude,
                        longitude = longitude,
                    ),
                    farmSizeAcres = farmSizeAcres,
                    soilType = soilType,
                    crops = if (crop.isBlank()) current.crops else listOf(crop),
                    cropVariety = cropVariety,
                    irrigationMethod = irrigationMethod,
                ),
            )
            justSaved.value = true
        }
    }
}
