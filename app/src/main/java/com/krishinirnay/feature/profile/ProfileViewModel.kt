package com.krishinirnay.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * [ProfileRepository] only supplies demo farm details (location, crops, farm size — no
 * backend collects these yet). Name and contact must instead reflect whoever actually
 * logged in, via [AuthRepository.currentUser] — Firebase Auth here only carries
 * uid/email, so the display name is derived from the email rather than faked.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    val profile: StateFlow<FarmerProfile> = combine(
        profileRepository.profile,
        authRepository.currentUser,
    ) { demoProfile, user ->
        val email = user?.email
        demoProfile.copy(
            name = email?.let(::displayNameFromEmail) ?: demoProfile.name,
            phone = email ?: demoProfile.phone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profileRepository.profile.value)
}

private fun displayNameFromEmail(email: String): String = email
    .substringBefore("@")
    .split(".", "_", "-")
    .filter { it.isNotBlank() }
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
