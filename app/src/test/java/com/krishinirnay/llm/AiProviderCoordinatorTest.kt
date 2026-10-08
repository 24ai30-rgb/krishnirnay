package com.krishinirnay.llm

import com.krishinirnay.core.llm.local.AiProviderCoordinator
import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmContextDto
import com.krishinirnay.core.llm.local.LocalLlmRepositoryImpl
import com.krishinirnay.core.llm.local.LocalLlmResult
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.LocalLlmStreamEvent
import com.krishinirnay.core.llm.local.OnDeviceLlmProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the Phase 5 provider abstraction actually prefers on-device over
 * the server, falls back honestly when on-device isn't available, and never
 * reports a provider ready that isn't — this is the class the whole
 * "on-device primary, server fallback, no fake AI online" architecture
 * hinges on.
 */
class AiProviderCoordinatorTest {

    private fun context() = LocalLlmContextDto(language = "en")

    private fun TestScope.coordinator(
        onDeviceStatus: LocalLlmStatus,
        onDeviceStream: LocalLlmStreamEvent = LocalLlmStreamEvent.Final(false, null, "on-device unavailable", null),
        serverStatus: LocalLlmStatus = LocalLlmStatus.UNAVAILABLE,
        serverStream: LocalLlmStreamEvent = LocalLlmStreamEvent.Final(false, null, "server unavailable", null),
    ): AiProviderCoordinator {
        val onDevice = mockk<OnDeviceLlmProvider>()
        every { onDevice.status } returns MutableStateFlow(onDeviceStatus)
        coEvery { onDevice.refreshStatus() } returns Unit
        every { onDevice.askStream(any(), any()) } returns flowOf(onDeviceStream)

        val server = mockk<LocalLlmRepositoryImpl>()
        every { server.status } returns MutableStateFlow(serverStatus)
        every { server.lastResponseMillis } returns MutableStateFlow(null)
        coEvery { server.refreshStatus() } returns Unit
        every { server.askStream(any(), any()) } returns flowOf(serverStream)
        coEvery { server.runDiagnostics() } returns mockk(relaxed = true)

        // backgroundScope: the coordinator's own init{} now self-launches a
        // refreshStatus() call (see AiProviderCoordinator's doc comment for
        // why — without it, on-device status never left its initial LOADING
        // value in the real app). UnconfinedTestDispatcher runs that launch
        // eagerly, so it completes before this function returns.
        return AiProviderCoordinator(backgroundScope, onDevice, server)
    }

    @Test
    fun `on-device ready is preferred over the server, which is never even queried`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(onDeviceStatus = LocalLlmStatus.READY, serverStatus = LocalLlmStatus.READY)

        coordinator.refreshStatus()

        assertEquals(AiProviderKind.ON_DEVICE, coordinator.activeProviderKind.value)
        assertEquals(LocalLlmStatus.READY, coordinator.status.value)
    }

    @Test
    fun `falls back to the server when on-device is unavailable`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(onDeviceStatus = LocalLlmStatus.UNAVAILABLE, serverStatus = LocalLlmStatus.READY)

        coordinator.refreshStatus()

        assertEquals(AiProviderKind.SERVER, coordinator.activeProviderKind.value)
        assertEquals(LocalLlmStatus.READY, coordinator.status.value)
    }

    @Test
    fun `reports NONE honestly when neither provider is ready, never a fake ready state`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(onDeviceStatus = LocalLlmStatus.UNAVAILABLE, serverStatus = LocalLlmStatus.MODEL_MISSING)

        coordinator.refreshStatus()

        assertEquals(AiProviderKind.NONE, coordinator.activeProviderKind.value)
        assertEquals(LocalLlmStatus.MODEL_MISSING, coordinator.status.value)
    }

    @Test
    fun `askStream routes to the on-device provider when it is ready`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(
            onDeviceStatus = LocalLlmStatus.READY,
            onDeviceStream = LocalLlmStreamEvent.Final(true, "on-device answer", null, 50),
        )

        val events = coordinator.askStream("question", context()).toList()

        assertEquals(LocalLlmStreamEvent.Final(true, "on-device answer", null, 50), events.last())
        assertEquals(AiProviderKind.ON_DEVICE, coordinator.activeProviderKind.value)
    }

    @Test
    fun `askStream routes to the server when on-device is not ready`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(
            onDeviceStatus = LocalLlmStatus.UNAVAILABLE,
            serverStream = LocalLlmStreamEvent.Final(true, "server answer", null, 8000),
        )

        val events = coordinator.askStream("question", context()).toList()

        assertEquals(LocalLlmStreamEvent.Final(true, "server answer", null, 8000), events.last())
        assertEquals(AiProviderKind.SERVER, coordinator.activeProviderKind.value)
    }

    @Test
    fun `ask collapses a successful stream into Answered`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(
            onDeviceStatus = LocalLlmStatus.UNAVAILABLE,
            serverStream = LocalLlmStreamEvent.Final(true, "Irrigate lightly.", null, 100),
        )

        val result = coordinator.ask("question", context())

        assertTrue(result is LocalLlmResult.Answered)
        assertEquals("Irrigate lightly.", (result as LocalLlmResult.Answered).reply)
    }

    @Test
    fun `ask never fabricates a reply when the stream ends in failure`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator(onDeviceStatus = LocalLlmStatus.UNAVAILABLE)

        val result = coordinator.ask("question", context())

        assertEquals(LocalLlmResult.Unavailable, result)
    }
}
