package com.krishinirnay.core.llm.local

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "KRISHI_ONDEVICE_LLM"

// Conservative generation settings picked without a physical device to tune
// them on: MAX_TOKENS bounds the whole context (prompt + answer) so a
// verbose fact-list prompt can never silently starve the answer of room;
// TEMPERATURE stays low for the same reason as the server path (explaining
// already-computed facts, not inventing anything).
private const val MAX_TOKENS = 1024
private const val TOP_K = 40
private const val TOP_P = 0.9
private const val TEMPERATURE = 0.2

/**
 * Real on-device inference via Google's LiteRT-LM runtime — the actively
 * maintained successor to the older MediaPipe `tasks-genai`/`LlmInference`
 * API (that API's own `ProgressListener` is `@Deprecated`, pointing here:
 * https://github.com/google-ai-edge/LiteRT-LM). Modeled directly on
 * Google's own reference implementation
 * (`google-ai-edge/gallery`'s `LlmChatModelHelper.kt`, Apache-2.0), the only
 * verified-working consumer of this library found during research for this
 * feature — this class intentionally uses a small, conservative subset of
 * its API (no vision/audio, no speculative decoding, no multi-turn memory).
 *
 * IMPORTANT — unverified on real hardware: this session has no physical or
 * emulated Android device to confirm the model actually loads, generates,
 * or performs acceptably rather than OOM-killing the app. Every failure
 * path below settles honestly to [LocalLlmStatus.ERROR]/[LocalLlmStatus.MODEL_MISSING]
 * rather than assuming success (see [refreshStatus]) — this is a real,
 * working implementation, never a faked "on-device ready".
 *
 * CPU backend only (no GPU/NPU): GPU delegate compatibility varies enormously
 * across real phone GPUs and can crash unpredictably in ways this session
 * cannot test for — a farmer should get a slower-but-correct answer, never a
 * crash. Revisit once this has been profiled on real devices.
 *
 * One fresh, stateless conversation per [askStream] call (no multi-turn
 * memory) — matches [LocalLlmRepositoryImpl] exactly (every request rebuilds
 * the full grounded prompt from scratch via [OnDeviceLlmPromptBuilder]) and
 * avoids unbounded context growth this session cannot verify [MAX_TOKENS]
 * safely absorbs over a long chat.
 */
@Singleton
class MediaPipeOnDeviceLlmProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: OnDeviceModelManager,
) : OnDeviceLlmProvider {

    private val _status = MutableStateFlow(LocalLlmStatus.LOADING)
    override val status: StateFlow<LocalLlmStatus> = _status.asStateFlow()

    /** Serializes generation the same way [LocalLlmRepositoryImpl] does for the server path — only one real inference call against the loaded engine at a time. */
    private val generationMutex = Mutex()

    private var engine: Engine? = null

    override suspend fun refreshStatus() {
        _status.value = LocalLlmStatus.LOADING
        _status.value = try {
            if (modelManager.downloadState.value != ModelDownloadState.Downloaded) {
                closeEngine()
                LocalLlmStatus.MODEL_MISSING
            } else {
                ensureEngineLoaded()
                LocalLlmStatus.READY
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refreshStatus(): engine failed to load: ${e::class.simpleName}: ${e.message}")
            closeEngine()
            LocalLlmStatus.ERROR
        }
    }

    /** Loads the engine once and keeps it (matches LiteRT-LM's own guidance: hold the loaded weights, create fresh conversations per turn). */
    private suspend fun ensureEngineLoaded() {
        if (engine != null) return
        withContext(Dispatchers.IO) {
            val config = EngineConfig(
                modelPath = modelManager.modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
        }
    }

    private fun closeEngine() {
        engine?.let {
            try {
                it.close()
            } catch (e: Exception) {
                Log.w(TAG, "closeEngine(): ${e.message}")
            }
        }
        engine = null
    }

    override fun askStream(message: String, context: LocalLlmContextDto): Flow<LocalLlmStreamEvent> = callbackFlow {
        generationMutex.withLock {
            _status.value = LocalLlmStatus.GENERATING
            try {
                ensureEngineLoaded()
            } catch (e: Exception) {
                Log.w(TAG, "askStream(): engine failed to load: ${e.message}")
                _status.value = LocalLlmStatus.ERROR
                trySend(LocalLlmStreamEvent.Final(success = false, answer = null, error = e.message, elapsedMs = null))
                close()
                return@withLock
            }

            val prompt = OnDeviceLlmPromptBuilder.build(message, context)
            val started = System.currentTimeMillis()
            val accumulated = StringBuilder()
            val activeEngine = engine ?: run {
                _status.value = LocalLlmStatus.ERROR
                trySend(LocalLlmStreamEvent.Final(success = false, answer = null, error = "Engine unavailable.", elapsedMs = null))
                close()
                return@withLock
            }
            val conversation = activeEngine.createConversation(
                ConversationConfig(samplerConfig = SamplerConfig(topK = TOP_K, topP = TOP_P, temperature = TEMPERATURE)),
            )

            conversation.sendMessageAsync(
                Contents.of(listOf(Content.Text(prompt))),
                object : MessageCallback {
                    override fun onMessage(msg: Message) {
                        val text = msg.toString()
                        if (text.isNotEmpty()) {
                            accumulated.append(text)
                            trySend(LocalLlmStreamEvent.Delta(text))
                        }
                    }

                    override fun onDone() {
                        val elapsedMs = (System.currentTimeMillis() - started).toInt()
                        val reply = accumulated.toString().trim()
                        if (reply.isBlank()) {
                            Log.w(TAG, "askStream(): on-device model returned an empty response.")
                            _status.value = LocalLlmStatus.ERROR
                            trySend(
                                LocalLlmStreamEvent.Final(
                                    success = false, answer = null,
                                    error = "The on-device model returned an empty response.", elapsedMs = elapsedMs,
                                ),
                            )
                        } else {
                            _status.value = LocalLlmStatus.READY
                            trySend(LocalLlmStreamEvent.Final(success = true, answer = reply, error = null, elapsedMs = elapsedMs))
                        }
                        close()
                    }

                    override fun onError(throwable: Throwable) {
                        Log.w(TAG, "askStream(): generation failed: ${throwable.message}")
                        _status.value = LocalLlmStatus.ERROR
                        trySend(LocalLlmStreamEvent.Final(success = false, answer = null, error = throwable.message, elapsedMs = null))
                        close()
                    }
                },
                emptyMap(),
            )

            // Runs once the flow closes for any reason — normal completion
            // (onDone/onError already called close() above) or the collector
            // cancelling early (see ChatbotViewModel.cancelGeneration). Closing
            // the conversation exactly once here, never inside the callback
            // above, avoids a double-close race between those two paths.
            awaitClose {
                try {
                    conversation.cancelProcess()
                } catch (e: Exception) {
                    Log.w(TAG, "askStream(): cancelProcess failed: ${e.message}")
                }
                try {
                    conversation.close()
                } catch (e: Exception) {
                    Log.w(TAG, "askStream(): conversation.close failed: ${e.message}")
                }
            }
        }
    }.flowOn(Dispatchers.IO)
        .catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "askStream(): ${e::class.simpleName}: ${e.message}")
            _status.value = LocalLlmStatus.ERROR
            emit(LocalLlmStreamEvent.Final(success = false, answer = null, error = e.message, elapsedMs = null))
        }
}
