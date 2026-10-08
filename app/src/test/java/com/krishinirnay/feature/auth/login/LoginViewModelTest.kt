package com.krishinirnay.feature.auth.login

import com.krishinirnay.core.data.firebase.FarmerCloudProfileRepository
import com.krishinirnay.core.data.model.AuthUser
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the exact bug found via live on-device testing: Firestore isn't
 * enabled for this project yet (PERMISSION_DENIED from the console), and a
 * call awaiting that response doesn't fail fast — it can hang far longer
 * than a farmer would ever wait. Login must never be gated on the Firestore
 * profile restore succeeding or even finishing at all.
 */
class LoginViewModelTest {

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun profile() = FarmerProfile(name = "", phone = "", location = "", farmSizeAcres = 0f, crops = emptyList())

    private fun viewModel(cloudFetchNeverCompletes: Boolean = false): LoginViewModel {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.currentUser } returns MutableStateFlow(AuthUser(uid = "uid-1", email = "test@example.com"))
        coEvery { authRepository.login(any(), any()) } returns Result.success(Unit)

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.hasCompletedOnboarding } returns MutableStateFlow(true)

        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profile())
        coEvery { profileRepository.updateProfile(any()) } returns Unit

        val cloudProfileRepository = mockk<FarmerCloudProfileRepository>()
        if (cloudFetchNeverCompletes) {
            coEvery { cloudProfileRepository.fetchProfile(any()) } coAnswers { awaitCancellation() }
        } else {
            coEvery { cloudProfileRepository.fetchProfile(any()) } returns null
        }

        return LoginViewModel(authRepository, settingsRepository, profileRepository, cloudProfileRepository)
    }

    @Test
    fun `blank email or password is rejected without calling Firebase`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()

        vm.login()

        assertFalse(vm.uiState.value.isLoggedIn)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `a successful login completes even when the Firestore profile restore never finishes`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = viewModel(cloudFetchNeverCompletes = true)
            vm.onEmailChange("test@example.com")
            vm.onPasswordChange("password123")

            vm.login()

            // Must not be stuck loading forever just because the Firestore
            // call underneath never resolves — see class doc comment.
            assertTrue(vm.uiState.value.isLoggedIn)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `a successful login with a working Firestore restore also completes normally`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(cloudFetchNeverCompletes = false)
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")

        vm.login()

        assertTrue(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.isLoading)
    }
}
