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
    fun logout()
}
