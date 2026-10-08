package com.krishinirnay.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.krishinirnay.core.data.model.AuthUser
import com.krishinirnay.core.data.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser?.toAuthUser())
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser?.toAuthUser()
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Unit
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email)
}
