package com.krishinirnay.navigation

import androidx.lifecycle.ViewModel
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Decides where the outer NavHost starts, computed once at app launch —
 * Firebase Auth restores any persisted session synchronously (see
 * FirebaseAuthRepositoryImpl's init block), so an already-logged-in farmer
 * is never shown Welcome/Login again (Part 7: "Do not show login repeatedly
 * to already authenticated users"). Mirrors the exact decision
 * LoginViewModel/RegisterViewModel make right after a fresh
 * login/registration, so a returning farmer and a just-authenticated farmer
 * land in the same place.
 *
 * No session at all starts at Welcome, not Login directly (Phase 5 Part 1):
 * a farmer explicitly picks "Create Account" or "Log In" rather than always
 * being shown a login form as if they already had an account.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val startDestination: String = when {
        authRepository.currentUser.value == null -> Destination.Welcome.route
        !settingsRepository.hasCompletedOnboarding.value -> Destination.Onboarding.route
        else -> Destination.Main.route
    }
}
