package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Always backed by real Firebase Auth — no mock variant. Mock Mode still
 * requires a real login (with a seeded demo account) rather than a
 * guest bypass, so Login stays a genuine, demoable Phase-1 screen.
 */
interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    suspend fun login(email: String, password: String): Result<Unit>

    /** Creates a brand-new Firebase Auth account — the farmer profile document is created separately, see FarmerCloudProfileRepository. */
    suspend fun register(email: String, password: String): Result<Unit>

    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    fun logout()
}
