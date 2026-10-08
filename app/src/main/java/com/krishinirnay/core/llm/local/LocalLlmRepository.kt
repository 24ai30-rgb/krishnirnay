package com.krishinirnay.core.llm.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A real answer, or [Unavailable] when the local model can't be reached — never a guessed reply. */
sealed interface LocalLlmResult {
    data class Answered(val reply: String) : LocalLlmResult
    data object Unavailable : LocalLlmResult
}

/**
 * Progress events from [LocalLlmRepository.askStream] — mirrors the
 * server's `/v1/local-llm/chat/stream` NDJSON events (see
 * `server/app/routers/local_llm.py`'s chat_stream() docstring).
 *
 * [Delta] chunks are shown immediately for perceived speed but are NOT
 * validated individually — only [Final] carries the honest, fully-checked
 * outcome (language honesty, meta-commentary rejection, same as the
 * non-streaming path). A caller MUST replace whatever partial text it
 * displayed from [Delta]s with [Final]'s own text once it arrives — on
 * failure that means discarding the streamed-so-far text entirely, never
 * leaving an unvalidated partial answer on screen as if it were real.
 */
sealed interface LocalLlmStreamEvent {
    data class Delta(val text: String) : LocalLlmStreamEvent
    data class Final(val success: Boolean, val answer: String?, val error: String?, val elapsedMs: Int?) : LocalLlmStreamEvent
}

/**
 * Independent, honestly-checked facts about the connection to the local
 * model, in the order a developer would actually debug them: can Android
 * reach the FastAPI server at all, is Ollama up behind it, is each
 * language's configured model pulled (Phase 4 — English/Hindi/Marathi can
 * each use a different model, see server's resolve_model_for_language), and
 * does generation actually work. Each is `null` only when it could not be
 * determined (e.g. the server itself is unreachable, so nothing past that
 * point was tested) — never guessed true.
 */
data class LocalLlmDiagnostics(
    val serverReachable: Boolean,
    val ollamaAvailable: Boolean?,
    val englishModelAvailable: Boolean?,
    val hindiModelAvailable: Boolean?,
    val marathiModelAvailable: Boolean?,
    val chatEndpointAvailable: Boolean?,
    /** The raw failure detail (exception message or HTTP code) — for logs/debug display, never shown as a farmer-facing answer. */
    val detail: String?,
)

/**
 * The one seam the rest of the app depends on for local-LLM conversation —
 * [com.krishinirnay.feature.chatbot.ChatbotViewModel] and AI Insights both go
 * through this, never a concrete HTTP client directly, so the model/provider
 * stays swappable via server-side configuration alone (`LOCAL_LLM_PROVIDER`/
 * `LOCAL_LLM_URL`/`LOCAL_LLM_MODEL`).
 */
interface LocalLlmRepository {
    val status: StateFlow<LocalLlmStatus>

    /**
     * Which backend [status]/[ask]/[askStream] actually reflect right now —
     * see [AiProviderCoordinator], the real implementation bound in
     * production. A plain [LocalLlmRepositoryImpl] (used directly only in
     * tests/older call sites) always reports [AiProviderKind.SERVER].
     */
    val activeProviderKind: StateFlow<AiProviderKind>

    /** Last real generation time in ms — advanced/debug display only, null until one succeeds. */
    val lastResponseMillis: StateFlow<Int?>

    /** Re-checks reachability now (e.g. after entering the Chatbot screen). */
    suspend fun refreshStatus()

    suspend fun ask(message: String, context: LocalLlmContextDto): LocalLlmResult

    /**
     * Same generation as [ask], but as a live stream of [LocalLlmStreamEvent]s
     * so the UI can show the answer as it's typed instead of waiting for the
     * whole thing (Part 1). Cancelling collection (e.g. the caller's
     * coroutine scope) stops the underlying network read — see
     * [LocalLlmRepositoryImpl.askStream].
     */
    fun askStream(message: String, context: LocalLlmContextDto): Flow<LocalLlmStreamEvent>

    /** Runs a real (~15-30s) deep check against the server — only call on explicit user action (e.g. a "Check connection" button), never automatically on screen entry. */
    suspend fun runDiagnostics(): LocalLlmDiagnostics
}
