package com.krishinirnay.llm

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.MediaPipeOnDeviceLlmProvider
import com.krishinirnay.core.llm.local.ModelDownloadState
import com.krishinirnay.core.llm.local.OnDeviceModelManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MediaPipeOnDeviceLlmProvider.refreshStatus] is the only thing standing
 * between a real farmer's phone and a fake "AI ready" claim — these tests
 * prove its three honest outcomes (Phase 5 Part 4 item 24): no model
 * downloaded yet, a downloaded model whose engine loads fine, and a
 * downloaded model whose engine fails to load (corrupt file, unsupported
 * device, out of memory) — the last of which must settle to ERROR, never a
 * fabricated READY.
 */
class MediaPipeOnDeviceLlmProviderTest {

    private fun manager(state: ModelDownloadState): OnDeviceModelManager {
        val manager = mockk<OnDeviceModelManager>()
        every { manager.downloadState } returns MutableStateFlow(state)
        every { manager.modelFile } returns File("model.task")
        return manager
    }

    @After
    fun tearDown() {
        unmockkConstructor(Engine::class)
    }

    @Test
    fun `reports MODEL_MISSING honestly when no model has been downloaded yet`() = runTest {
        val provider = MediaPipeOnDeviceLlmProvider(mockk<Context>(), manager(ModelDownloadState.NotDownloaded))

        provider.refreshStatus()

        assertEquals(LocalLlmStatus.MODEL_MISSING, provider.status.value)
    }

    @Test
    fun `reports READY once a downloaded model's engine initializes successfully`() = runTest {
        mockkConstructor(Engine::class)
        every { anyConstructed<Engine>().initialize() } returns Unit
        val provider = MediaPipeOnDeviceLlmProvider(mockk<Context>(), manager(ModelDownloadState.Downloaded))

        provider.refreshStatus()

        assertEquals(LocalLlmStatus.READY, provider.status.value)
    }

    @Test
    fun `settles to ERROR, never a fabricated READY, when a downloaded model's engine fails to load`() = runTest {
        mockkConstructor(Engine::class)
        every { anyConstructed<Engine>().initialize() } throws RuntimeException("native library missing")
        val provider = MediaPipeOnDeviceLlmProvider(mockk<Context>(), manager(ModelDownloadState.Downloaded))

        provider.refreshStatus()

        assertEquals(LocalLlmStatus.ERROR, provider.status.value)
    }
}
