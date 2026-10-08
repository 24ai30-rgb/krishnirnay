package com.krishinirnay.core.llm.local

import android.util.Log
import com.krishinirnay.BuildConfig
import com.krishinirnay.core.common.ApplicationScope
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "KRISHI_LOCAL_LLM"

// Calls this app's own server (GET/POST /v1/local-llm/*) — never Ollama or any
// inference endpoint directly, and never a cloud LLM. If the local model isn't
// installed/running, status honestly settles to MODEL_MISSING/UNAVAILABLE and
// ask() returns Unavailable — the rest of the app (Decision Engine, sensors,
// weather, market) is completely unaffected either way.
//
// ask() runs on whatever coroutine the caller provides (ChatbotViewModel uses
// viewModelScope), so a slow local model never blocks the UI thread. Requests
// go through the LocalLlmClient-qualified Retrofit, whose read timeout is sized
// for local inference rather than for a fast network API.
//
// The chat endpoint always answers HTTP 200 — success/failure lives in the
// response body's `success` field (see LocalLlmChatResponseDto), so a non-2xx
// response here means the *server itself* (or the network to it) failed, not
// that the local model failed to answer; both are still surfaced as
// [LocalLlmResult.Unavailable], never a fabricated reply either way.
@Singleton
class LocalLlmRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val api: LocalLlmApiService,
    private val json: Json,
) : LocalLlmRepository {

    private val _status = MutableStateFlow(LocalLlmStatus.LOADING)
    override val status: StateFlow<LocalLlmStatus> = _status.asStateFlow()

    // This class only ever talks to the FastAPI+Ollama server — see class
    // doc comment. AiProviderCoordinator is what actually decides between
    // on-device and server in production.
    override val activeProviderKind: StateFlow<AiProviderKind> = MutableStateFlow(AiProviderKind.SERVER).asStateFlow()

    private val _lastResponseMillis = MutableStateFlow<Int?>(null)

    /** Last real generation time — advanced/debug display only, never farmer-facing. */
    override val lastResponseMillis: StateFlow<Int?> = _lastResponseMillis.asStateFlow()

    /**
     * Serializes every real generation call. Ollama runs one model worker at
     * a time on the reference CPU-only hardware — ChatbotViewModel's
     * user-driven askStream() and AiInsightsViewModel's auto-triggered ask()
     * (fired independently on every risk-level change) both land on this
     * same @Singleton instance with no other coordination. Without this,
     * two calls firing at once queue inside Ollama itself, silently adding
     * one call's full generation time on top of the other's — easily
     * blowing the 120s/150s timeout budgets sized for one request at a
     * time, and racing writes to [_status]/[_lastResponseMillis] in the
     * meantime. A manual one-request-at-a-time HTTP test never surfaces
     * this, which is why it can "pass HTTP tests" and still fail for real
     * farmers bouncing between screens.
     */
    private val generationMutex = Mutex()

    init {
        // The host/port only (never a header or query value) — safe to log and
        // the single fastest way to spot "app is still pointed at the wrong IP".
        Log.d(TAG, "Local LLM server base URL = ${BuildConfig.SERVER_BASE_URL}")
        scope.launch { refreshStatus() }
    }

    override suspend fun refreshStatus() {
        _status.value = LocalLlmStatus.LOADING
        _status.value = try {
            val response = api.status()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Log.d(TAG, "status() -> ${body.status} (ollama_running=${body.ollama_running})")
                statusFrom(body.status)
            } else {
                Log.w(TAG, "status() -> HTTP ${response.code()}, no usable body")
                LocalLlmStatus.UNAVAILABLE
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "status() failed: ${e::class.simpleName}: ${e.message}")
            LocalLlmStatus.ERROR
        }
    }

    override suspend fun ask(message: String, context: LocalLlmContextDto): LocalLlmResult = generationMutex.withLock {
        _status.value = LocalLlmStatus.GENERATING
        try {
            val response = api.chat(LocalLlmChatRequestDto(message = message, context = context))
            val body = response.body()
            if (response.isSuccessful && body != null && body.success && !body.answer.isNullOrBlank()) {
                Log.d(TAG, "chat() succeeded in ${body.elapsed_ms}ms (model=${body.model})")
                _lastResponseMillis.value = body.elapsed_ms
                _status.value = LocalLlmStatus.READY
                LocalLlmResult.Answered(body.answer)
            } else {
                // body.success == false means the server reached its own honest
                // "no local model / generation failed" path (body.error carries
                // why, currently used only for logs/debug, never shown raw to a
                // farmer) — re-check status so the UI reflects which case it is,
                // rather than guessing.
                Log.w(TAG, "chat() did not answer: HTTP ${response.code()}, body.error=${body?.error}")
                refreshStatus()
                LocalLlmResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "chat() failed: ${e::class.simpleName}: ${e.message}")
            _status.value = LocalLlmStatus.ERROR
            LocalLlmResult.Unavailable
        }
    }

    override fun askStream(message: String, context: LocalLlmContextDto): Flow<LocalLlmStreamEvent> = flow {
        generationMutex.withLock {
            _status.value = LocalLlmStatus.GENERATING
            val response = api.chatStream(LocalLlmChatRequestDto(message = message, context = context))
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                Log.w(TAG, "chatStream() failed: HTTP ${response.code()}")
                refreshStatus()
                emit(LocalLlmStreamEvent.Final(success = false, answer = null, error = "HTTP ${response.code()}", elapsedMs = null))
                return@withLock
            }
            var finalEmitted = false
            body.use { responseBody ->
                val source = responseBody.source()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val event = try {
                        json.parseToJsonElement(line).jsonObject
                    } catch (e: Exception) {
                        // A single split/malformed NDJSON line must not discard
                        // an otherwise-good in-progress answer — skip just this
                        // line (worst case: one missing text chunk) rather than
                        // letting the outer .catch{} below throw the whole
                        // response away. The finalEmitted safety net after this
                        // loop still guarantees the message resolves even if
                        // every remaining line is unparseable.
                        Log.w(TAG, "chatStream(): skipping unparseable line: ${e.message}")
                        continue
                    }
                    if ("delta" in event) {
                        emit(LocalLlmStreamEvent.Delta(event.getValue("delta").jsonPrimitive.content))
                        continue
                    }
                    val success = event["success"]?.jsonPrimitive?.booleanOrNull ?: false
                    val answer = event["answer"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
                    val error = event["error"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
                    val elapsedMs = event["elapsed_ms"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
                    if (success && !answer.isNullOrBlank()) {
                        Log.d(TAG, "chatStream() succeeded in ${elapsedMs}ms")
                        _lastResponseMillis.value = elapsedMs
                        _status.value = LocalLlmStatus.READY
                    } else {
                        // Mirrors ask()'s own failure path: re-check status rather
                        // than guessing why (model missing vs. generation failed).
                        Log.w(TAG, "chatStream() did not answer: error=$error")
                        refreshStatus()
                    }
                    finalEmitted = true
                    emit(LocalLlmStreamEvent.Final(success, answer, error, elapsedMs))
                }
            }
            if (!finalEmitted) {
                // The stream ended (server closed the connection) without ever
                // sending a "done" line — every line we saw was either a Delta
                // or unparseable. Without this, the collecting ViewModel's
                // message bubble would be stuck showing "generating" forever
                // (see ChatbotViewModel.streamLocalLlmReply, which only clears
                // that per-message flag on a Final event).
                Log.w(TAG, "chatStream(): stream ended without a final result line")
                _status.value = LocalLlmStatus.ERROR
                emit(LocalLlmStreamEvent.Final(success = false, answer = null, error = "Stream ended unexpectedly", elapsedMs = null))
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        Log.w(TAG, "chatStream() failed: ${e::class.simpleName}: ${e.message}")
        _status.value = LocalLlmStatus.ERROR
        emit(LocalLlmStreamEvent.Final(success = false, answer = null, error = e.message, elapsedMs = null))
    }.flowOn(Dispatchers.IO)

    override suspend fun runDiagnostics(): LocalLlmDiagnostics {
        return try {
            val response = api.status(deep = true)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                Log.w(TAG, "runDiagnostics(): server reached but returned HTTP ${response.code()}")
                return LocalLlmDiagnostics(
                    serverReachable = true,
                    ollamaAvailable = null,
                    englishModelAvailable = null,
                    hindiModelAvailable = null,
                    marathiModelAvailable = null,
                    chatEndpointAvailable = null,
                    detail = "HTTP ${response.code()}",
                )
            }
            Log.d(TAG, "runDiagnostics(): ${body.status} (ollama_running=${body.ollama_running}, model_availability=${body.model_availability})")
            LocalLlmDiagnostics(
                serverReachable = true,
                ollamaAvailable = body.ollama_running,
                englishModelAvailable = body.model_availability["en"],
                hindiModelAvailable = body.model_availability["hi"],
                marathiModelAvailable = body.model_availability["mr"],
                chatEndpointAvailable = body.status == "GENERATION_WORKING",
                detail = body.status,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "runDiagnostics(): server unreachable: ${e::class.simpleName}: ${e.message}")
            LocalLlmDiagnostics(
                serverReachable = false,
                ollamaAvailable = null,
                englishModelAvailable = null,
                hindiModelAvailable = null,
                marathiModelAvailable = null,
                chatEndpointAvailable = null,
                detail = e.message ?: e::class.simpleName,
            )
        }
    }

    /** Server-side status strings — see server/app/services/local_llm_service.py. */
    private fun statusFrom(raw: String): LocalLlmStatus = when (raw) {
        "MODEL_AVAILABLE", "GENERATION_WORKING" -> LocalLlmStatus.READY
        "MODEL_MISSING" -> LocalLlmStatus.MODEL_MISSING
        // "OLLAMA_UNREACHABLE", "GENERATION_FAILED", and anything unrecognized
        // (e.g. a status value added server-side later) all mean the same
        // thing to a farmer: no working local AI right now.
        else -> LocalLlmStatus.UNAVAILABLE
    }
}
