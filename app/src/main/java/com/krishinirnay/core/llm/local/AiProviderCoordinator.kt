package com.krishinirnay.core.llm.local

import android.util.Log
import com.krishinirnay.core.common.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "KRISHI_AI_PROVIDER"

/**
 * The "clean AI provider abstraction" Phase 5 asks for:
 *
 *     Android Local AI
 *           |
 *     Primary: on-device model      (OnDeviceLlmProvider)
 *           v (only if unavailable)
 *     Fallback: FastAPI + Ollama    (LocalLlmRepositoryImpl, dev/optional)
 *
 * This is the ONLY class Hilt binds to [LocalLlmRepository] in production —
 * [ChatbotViewModel]/Settings/AiInsights depend on the interface exactly as
 * before and never know which backend actually answered (Phase 5 Part 15:
 * "The UI should not know implementation details"). [activeProviderKind]
 * is the one extra signal they read to render "On-device AI Ready" / "Local
 * Server AI Ready" / "Offline AI" / "AI Unavailable" (Part 1).
 *
 * No cloud fallback exists here on purpose — Part 1 only allows one "if
 * explicitly configured", and nothing in this codebase configures one, so
 * adding a stub for it would be speculative, unused code.
 */
@Singleton
class AiProviderCoordinator @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val onDeviceProvider: OnDeviceLlmProvider,
    private val serverProvider: LocalLlmRepositoryImpl,
) : LocalLlmRepository {

    private val _activeProviderKind = MutableStateFlow(AiProviderKind.NONE)
    override val activeProviderKind: StateFlow<AiProviderKind> = _activeProviderKind.asStateFlow()

    private val _status = MutableStateFlow(LocalLlmStatus.LOADING)
    override val status: StateFlow<LocalLlmStatus> = _status.asStateFlow()

    override val lastResponseMillis: StateFlow<Int?> = serverProvider.lastResponseMillis

    init {
        // Without this, [_status]/[_activeProviderKind] (and therefore
        // [onDeviceProvider]'s own status, which askStream() reads directly)
        // stay at their initial LOADING/NONE values forever — nothing else
        // in the app ever called refreshStatus() at this coordinator level,
        // which silently meant on-device was NEVER actually preferred even
        // once a real model was downloaded (found via Phase 5 Part 4's
        // requirement that on-device MUST be preferred — it structurally
        // couldn't be, since the check it depends on never resolved past
        // LOADING). [LocalLlmRepositoryImpl] already self-initializes this
        // way for the exact same reason; the coordinator needs its own.
        scope.launch { refreshStatus() }
    }

    override suspend fun refreshStatus() {
        _status.value = LocalLlmStatus.LOADING
        onDeviceProvider.refreshStatus()
        if (onDeviceProvider.status.value == LocalLlmStatus.READY) {
            Log.d(TAG, "on-device provider ready — using it, server not queried")
            _activeProviderKind.value = AiProviderKind.ON_DEVICE
            _status.value = LocalLlmStatus.READY
            return
        }

        serverProvider.refreshStatus()
        val serverStatus = serverProvider.status.value
        Log.d(TAG, "on-device unavailable, server status = $serverStatus")
        _activeProviderKind.value = if (serverStatus == LocalLlmStatus.READY) AiProviderKind.SERVER else AiProviderKind.NONE
        _status.value = serverStatus
    }

    override suspend fun ask(message: String, context: LocalLlmContextDto): LocalLlmResult {
        var result: LocalLlmResult = LocalLlmResult.Unavailable
        try {
            askStream(message, context).collect { event ->
                if (event is LocalLlmStreamEvent.Final) {
                    result = if (event.success && !event.answer.isNullOrBlank()) {
                        LocalLlmResult.Answered(event.answer)
                    } else {
                        LocalLlmResult.Unavailable
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
        return result
    }

    override fun askStream(message: String, context: LocalLlmContextDto): Flow<LocalLlmStreamEvent> {
        // Reflects the provider actually used for THIS call, not a stale
        // value from the last refreshStatus() — a farmer who waited out a
        // brief server hiccup should not be stuck labelled NONE forever.
        return if (onDeviceProvider.status.value == LocalLlmStatus.READY) {
            _activeProviderKind.value = AiProviderKind.ON_DEVICE
            onDeviceProvider.askStream(message, context)
        } else {
            _activeProviderKind.value = AiProviderKind.SERVER
            serverProvider.askStream(message, context)
        }
    }

    /**
     * The on-device provider has no per-language model concept (the
     * downloaded model answers every language equally, or not at all) and no
     * deep "prove generation really works" check of its own — diagnostics
     * always reflect the server, which is the only backend with a
     * language-aware deep status check today. See [MediaPipeOnDeviceLlmProvider].
     */
    override suspend fun runDiagnostics(): LocalLlmDiagnostics = serverProvider.runDiagnostics()
}
