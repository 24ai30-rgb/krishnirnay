package com.krishinirnay.feature.auth.register

import com.krishinirnay.core.data.model.AuthUser
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the validation rules from the "REGISTRATION SCREEN" spec (name
 * required, valid email, minimum password length, matching confirmation,
 * mobile format) reject bad input WITHOUT ever calling Firebase, and that a
 * genuinely valid submission creates the Auth account and a blank profile
 * (Firestore sync itself is ProfileRepository's job now, not this
 * ViewModel's — see FarmerProfileRepositoryImpl).
 */
class RegisterViewModelTest {

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun profile() = FarmerProfile(name = "", phone = "", location = "", farmSizeAcres = 0f, crops = emptyList())

    private fun viewModel(): Pair<RegisterViewModel, AuthRepository> {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.currentUser } returns MutableStateFlow(AuthUser(uid = "uid-1", email = "test@example.com"))
        coEvery { authRepository.register(any(), any()) } returns Result.success(Unit)

        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profile())
        coEvery { profileRepository.updateProfile(any()) } returns Unit

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.language } returns MutableStateFlow("en")
        coEvery { settingsRepository.setHasCompletedOnboarding(any()) } returns Unit

        val vm = RegisterViewModel(authRepository, profileRepository, settingsRepository)
        return vm to authRepository
    }

    private fun RegisterViewModel.fillValidForm() {
        onFirstNameChange("Sanjay")
        onLastNameChange("Patil")
        onEmailChange("sanjay@example.com")
        onPasswordChange("secure123")
        onConfirmPasswordChange("secure123")
        onMobileChange("9876543210")
    }

    @Test
    fun `a blank name is rejected without calling Firebase`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, authRepository) = viewModel()
        vm.fillValidForm()
        vm.onFirstNameChange("")

        vm.register()

        assertEquals("Please enter your name", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isRegistered)
        coVerify(exactly = 0) { authRepository.register(any(), any()) }
    }

    @Test
    fun `an invalid email is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _) = viewModel()
        vm.fillValidForm()
        vm.onEmailChange("not-an-email")

        vm.register()

        assertEquals("Please enter a valid email", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isRegistered)
    }

    @Test
    fun `a password shorter than 6 characters is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _) = viewModel()
        vm.fillValidForm()
        vm.onPasswordChange("123")
        vm.onConfirmPasswordChange("123")

        vm.register()

        assertEquals("Password must be at least 6 characters", vm.uiState.value.errorMessage)
    }

    @Test
    fun `mismatched password confirmation is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _) = viewModel()
        vm.fillValidForm()
        vm.onConfirmPasswordChange("different123")

        vm.register()

        assertEquals("Passwords do not match", vm.uiState.value.errorMessage)
    }

    @Test
    fun `a mobile number that is not 10 digits is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _) = viewModel()
        vm.fillValidForm()
        vm.onMobileChange("12345")

        vm.register()

        assertEquals("Please enter a valid 10-digit mobile number", vm.uiState.value.errorMessage)
    }

    @Test
    fun `a fully valid form creates the Firebase account and a blank profile`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, authRepository) = viewModel()
            vm.fillValidForm()

            vm.register()

            assertTrue(vm.uiState.value.isRegistered)
            assertNull(vm.uiState.value.errorMessage)
            coVerify { authRepository.register("sanjay@example.com", "secure123") }
        }

    // Real bug found via live on-device testing: the local profile store and
    // "onboarding completed" flag are both device-scoped, not per Firebase
    // account. A second real account registered on a device that had
    // already onboarded a different farmer silently inherited that
    // farmer's crop/farm-size/location and skipped Farm Setup entirely —
    // registration must always produce a genuinely blank profile and force
    // onboarding, regardless of what's already stored locally.
    @Test
    fun `registration never inherits a stale previous farmer's crop or farm data`() = runTest(UnconfinedTestDispatcher()) {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.currentUser } returns MutableStateFlow(AuthUser(uid = "uid-2", email = "test@example.com"))
        coEvery { authRepository.register(any(), any()) } returns Result.success(Unit)

        val staleProfile = FarmerProfile(name = "Previous Farmer", phone = "0", location = "Mumbai, Maharashtra", farmSizeAcres = 2f, crops = listOf("Wheat"))
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(staleProfile)
        val savedProfile = slot<FarmerProfile>()
        coEvery { profileRepository.updateProfile(capture(savedProfile)) } returns Unit

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.language } returns MutableStateFlow("en")
        coEvery { settingsRepository.setHasCompletedOnboarding(any()) } returns Unit

        val vm = RegisterViewModel(authRepository, profileRepository, settingsRepository)
        vm.fillValidForm()

        vm.register()

        assertTrue(vm.uiState.value.isRegistered)
        assertEquals("Sanjay Patil", savedProfile.captured.name) // this test's own name, not the stale one
        assertEquals(emptyList<String>(), savedProfile.captured.crops)
        assertEquals(0f, savedProfile.captured.farmSizeAcres)
        assertEquals("", savedProfile.captured.location)
        coVerify { settingsRepository.setHasCompletedOnboarding(false) }
    }

    @Test
    fun `an email already in use maps to a clear, human-readable message`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, authRepository) = viewModel()
        val exception = mockk<com.google.firebase.auth.FirebaseAuthException>()
        every { exception.errorCode } returns "ERROR_EMAIL_ALREADY_IN_USE"
        coEvery { authRepository.register(any(), any()) } returns Result.failure(exception)
        vm.fillValidForm()

        vm.register()

        assertEquals("An account with this email already exists. Please log in instead.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isRegistered)
    }
}
