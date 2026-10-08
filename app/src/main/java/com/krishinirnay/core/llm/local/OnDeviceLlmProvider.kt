package com.krishinirnay.core.llm.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The on-device half of the AI provider abstraction (Phase 5) — a model
 * running directly on this phone, no PC/FastAPI/Ollama required. Kept as an
 * interface, separate from [LocalLlmRepositoryImpl]-shaped code, so the real
 * engine ([MediaPipeOnDeviceLlmProvider], LiteRT-LM) and the server path
 * stay swappable behind [AiProviderCoordinator] without any UI code
 * changing.
 *
 * [MediaPipeOnDeviceLlmProvider] is the only implementation bound in
 * production — it honestly reports [LocalLlmStatus.MODEL_MISSING] whenever
 * [OnDeviceModelManager] hasn't downloaded the model yet, so
 * [AiProviderCoordinator] falls through to the server path exactly as
 * before until a farmer explicitly downloads the on-device model.
 */
interface OnDeviceLlmProvider {
    val status: StateFlow<LocalLlmStatus>
    fun askStream(message: String, context: LocalLlmContextDto): Flow<LocalLlmStreamEvent>
    suspend fun refreshStatus()
}
