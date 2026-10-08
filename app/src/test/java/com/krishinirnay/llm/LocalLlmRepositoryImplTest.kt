package com.krishinirnay.llm

import com.krishinirnay.core.llm.local.LocalLlmApiService
import com.krishinirnay.core.llm.local.LocalLlmChatRequestDto
import com.krishinirnay.core.llm.local.LocalLlmChatResponseDto
import com.krishinirnay.core.llm.local.LocalLlmContextDto
import com.krishinirnay.core.llm.local.LocalLlmRepositoryImpl
import com.krishinirnay.core.llm.local.LocalLlmResult
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.LocalLlmStatusResponseDto
import com.krishinirnay.core.llm.local.LocalLlmStreamEvent
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private val testJson = Json { ignoreUnknownKeys = true }

private fun emptyContext() = LocalLlmContextDto(language = "en")

private fun statusDto(
    status: String,
    model: String = "deepseek-r1:7b",
    ollamaRunning: Boolean = true,
    modelAvailability: Map<String, Boolean> = mapOf("en" to true, "hi" to true, "mr" to true),
) = LocalLlmStatusResponseDto(
    status = status,
    ollama_running = ollamaRunning,
    provider = "ollama",
    model = model,
    model_availability = modelAvailability,
)

private fun successDto(answer: String, elapsedMs: Int? = null, language: String = "en", model: String = "deepseek-r1:7b") =
    LocalLlmChatResponseDto(success = true, language = language, answer = answer, model = model, error = null, elapsed_ms = elapsedMs)

private fun failureDto(error: String, language: String = "en", model: String = "deepseek-r1:7b") =
    LocalLlmChatResponseDto(success = false, language = language, answer = null, model = model, error = error)

private fun ndjsonBody(vararg lines: String): ResponseBody =
    (lines.joinToString("\n") + "\n").toResponseBody("application/x-ndjson".toMediaType())

/**
 * Proves the Local LLM repository is honest about availability and never
 * silently reaches a cloud API — it only ever calls LocalLlmApiService,
 * which itself only ever talks to this app's own server, never Ollama or
 * any cloud LLM directly (see LocalLlmRepositoryImpl's own doc comment).
 *
 * The chat endpoint always answers HTTP 200 (success/failure lives in the
 * body's `success` field), so a "the model couldn't answer" case is modeled
 * here as `Response.success(failureDto(...))`, not `Response.error(503, ...)`
 * — that HTTP-error path is reserved for the server itself being unreachable.
 */
class LocalLlmRepositoryImplTest {

    @Test
    fun `a MODEL_AVAILABLE status reports READY`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.READY, repo.status.value)
    }

    @Test
    fun `an OLLAMA_UNREACHABLE status is honestly reported UNAVAILABLE, never fake READY`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("OLLAMA_UNREACHABLE", ollamaRunning = false))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.UNAVAILABLE, repo.status.value)
    }

    @Test
    fun `a network failure while checking status is honestly reported ERROR, not READY`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } throws RuntimeException("connection refused")

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.ERROR, repo.status.value)
    }

    @Test
    fun `a real answer from the server is surfaced as Answered`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
        coEvery { api.chat(any()) } returns Response.success(successDto("Irrigate lightly this evening."))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.ask("Should I irrigate?", emptyContext())

        assertTrue(result is LocalLlmResult.Answered)
        assertEquals("Irrigate lightly this evening.", (result as LocalLlmResult.Answered).reply)
    }

    @Test
    fun `a timeout while asking never fabricates a reply`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
        coEvery { api.chat(any()) } throws RuntimeException("timeout")

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.ask("Should I irrigate?", emptyContext())

        assertEquals(LocalLlmResult.Unavailable, result)
    }

    @Test
    fun `success=false in the response body never fabricates a reply`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("OLLAMA_UNREACHABLE", ollamaRunning = false))
        coEvery { api.chat(any()) } returns Response.success(failureDto("Local LLM returned an empty response."))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.ask("Should I irrigate?", emptyContext())

        assertEquals(LocalLlmResult.Unavailable, result)
    }

    @Test
    fun `an HTTP error from the server itself never fabricates a reply`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("OLLAMA_UNREACHABLE", ollamaRunning = false))
        coEvery { api.chat(any()) } returns Response.error(500, mockk<okhttp3.ResponseBody>(relaxed = true))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.ask("Should I irrigate?", emptyContext())

        assertEquals(LocalLlmResult.Unavailable, result)
    }

    @Test
    fun `ask only ever calls this app's own LocalLlmApiService, never a second client`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
        val captured = mutableListOf<LocalLlmChatRequestDto>()
        coEvery { api.chat(any()) } coAnswers {
            captured += firstArg<LocalLlmChatRequestDto>()
            Response.success(successDto("ok"))
        }

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        repo.ask("water?", emptyContext())

        assertEquals(1, captured.size)
        assertEquals("water?", captured.first().message)
    }

    // "Ollama up but model not pulled" must never collapse into UNAVAILABLE —
    // the fix (pull the model) differs from starting Ollama, and the UI
    // shows a different message for each.
    @Test
    fun `a MODEL_MISSING status from the server is surfaced as MODEL_MISSING`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_MISSING"))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.MODEL_MISSING, repo.status.value)
    }

    @Test
    fun `a GENERATION_WORKING status also reports READY`() = runTest(UnconfinedTestDispatcher()) {
        // Only ever returned for a deep status check, but Android must still
        // treat it as "the AI genuinely works" if a caller ever surfaces it.
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("GENERATION_WORKING"))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.READY, repo.status.value)
    }

    @Test
    fun `a GENERATION_FAILED status is treated as UNAVAILABLE, never READY`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("GENERATION_FAILED"))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.UNAVAILABLE, repo.status.value)
    }

    @Test
    fun `an unrecognised server status is treated as UNAVAILABLE, never READY`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("SOMETHING_NEW"))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

        assertEquals(LocalLlmStatus.UNAVAILABLE, repo.status.value)
    }

    @Test
    fun `a successful answer records the real generation time and settles status to READY`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = mockk<LocalLlmApiService>()
            coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
            coEvery { api.chat(any()) } returns Response.success(
                successDto("Delay irrigation, rain is expected.", elapsedMs = 8123),
            )

            val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
            val result = repo.ask("why?", emptyContext())

            assertTrue(result is LocalLlmResult.Answered)
            assertEquals(8123, repo.lastResponseMillis.value)
            assertEquals(LocalLlmStatus.READY, repo.status.value)
        }

    @Test
    fun `a blank answer is never presented as a real reply`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
        coEvery { api.chat(any()) } returns Response.success(successDto("   "))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.ask("why?", emptyContext())

        assertEquals(LocalLlmResult.Unavailable, result)
    }

    // success=false means the server hit its own honest "model missing /
    // generation failed" path, so the repository re-checks which case it
    // actually is rather than leaving a stale READY on screen.
    @Test
    fun `a failed generation re-checks status so the UI shows the real reason`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = mockk<LocalLlmApiService>()
            coEvery { api.status() } returns Response.success(statusDto("MODEL_MISSING"))
            coEvery { api.chat(any()) } returns Response.success(failureDto("Model not installed."))

            val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
            val result = repo.ask("why?", emptyContext())

            assertEquals(LocalLlmResult.Unavailable, result)
            assertEquals(LocalLlmStatus.MODEL_MISSING, repo.status.value)
        }

    @Test
    fun `runDiagnostics reports every check when generation genuinely works`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
        coEvery { api.status(deep = true) } returns Response.success(statusDto("GENERATION_WORKING"))

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.runDiagnostics()

        assertEquals(true, result.serverReachable)
        assertEquals(true, result.ollamaAvailable)
        assertEquals(true, result.englishModelAvailable)
        assertEquals(true, result.hindiModelAvailable)
        assertEquals(true, result.marathiModelAvailable)
        assertEquals(true, result.chatEndpointAvailable)
    }

    @Test
    fun `runDiagnostics honestly reports an unreachable server, never fabricating any check`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
        coEvery { api.status(deep = true) } throws IOException("Failed to connect to /127.0.0.1:8000")

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.runDiagnostics()

        assertEquals(false, result.serverReachable)
        assertEquals(null, result.ollamaAvailable)
        assertEquals(null, result.englishModelAvailable)
        assertEquals(null, result.hindiModelAvailable)
        assertEquals(null, result.marathiModelAvailable)
        assertEquals(null, result.chatEndpointAvailable)
        assertTrue(result.detail!!.contains("127.0.0.1"))
    }

    @Test
    fun `runDiagnostics reports model missing without claiming the chat endpoint works`() = runTest(UnconfinedTestDispatcher()) {
        val api = mockk<LocalLlmApiService>()
        coEvery { api.status() } returns Response.success(statusDto("MODEL_MISSING"))
        coEvery { api.status(deep = true) } returns Response.success(
            statusDto("MODEL_MISSING", ollamaRunning = true, modelAvailability = mapOf("en" to false, "hi" to false, "mr" to false)),
        )

        val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
        val result = repo.runDiagnostics()

        assertEquals(true, result.serverReachable)
        assertEquals(true, result.ollamaAvailable)
        assertEquals(false, result.englishModelAvailable)
        assertEquals(false, result.chatEndpointAvailable)
    }

    // Phase 4: English/Hindi/Marathi can each be a different model, so
    // "English ready" and "Hindi missing" must be independently visible —
    // neither collapses into the other or into the overall `status` string.
    @Test
    fun `runDiagnostics reports Hindi and Marathi models missing while English is available`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = mockk<LocalLlmApiService>()
            coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
            coEvery { api.status(deep = true) } returns Response.success(
                statusDto("GENERATION_WORKING", modelAvailability = mapOf("en" to true, "hi" to false, "mr" to false)),
            )

            val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
            val result = repo.runDiagnostics()

            assertEquals(true, result.englishModelAvailable)
            assertEquals(false, result.hindiModelAvailable)
            assertEquals(false, result.marathiModelAvailable)
        }

    // Phase 5 Part 3: ChatbotViewModel's user-driven askStream() and
    // AiInsightsViewModel's auto-triggered ask() both call this same
    // @Singleton instance with no other coordination. Ollama runs one model
    // worker at a time on the reference (CPU-only) hardware, so two calls
    // firing at once queue inside Ollama itself — silently doubling the
    // effective wait time and risking the timeout budget, which a
    // one-request-at-a-time manual HTTP test never reveals. This proves the
    // fix: a second call must not even reach the network until the first
    // one finishes.
    @Test
    fun `concurrent ask calls never fire simultaneously against the shared local model`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = mockk<LocalLlmApiService>()
            coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))

            val releaseFirst = CompletableDeferred<Unit>()
            val callOrder = mutableListOf<String>()
            coEvery { api.chat(any()) } coAnswers {
                val message = firstArg<LocalLlmChatRequestDto>().message
                callOrder += "start:$message"
                if (message == "first") releaseFirst.await()
                callOrder += "respond:$message"
                Response.success(successDto("answer for $message"))
            }

            val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)

            val firstJob = backgroundScope.launch { repo.ask("first", emptyContext()) }
            // UnconfinedTestDispatcher runs firstJob eagerly until it suspends on
            // releaseFirst.await() inside api.chat() — at that point it holds the mutex.
            val secondJob = backgroundScope.launch { repo.ask("second", emptyContext()) }

            // The second call must be blocked on the mutex, not the network:
            // api.chat("second") must not have been invoked yet.
            assertEquals(listOf("start:first"), callOrder)

            releaseFirst.complete(Unit)
            firstJob.join()
            secondJob.join()

            assertEquals(
                listOf("start:first", "respond:first", "start:second", "respond:second"),
                callOrder,
            )
        }

    @Test
    fun `askStream skips one unparseable NDJSON line without discarding an otherwise good answer`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = mockk<LocalLlmApiService>()
            coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
            coEvery { api.chatStream(any()) } returns Response.success(
                ndjsonBody(
                    """{"delta":"Irrigate "}""",
                    "not json at all — a split/malformed line",
                    """{"delta":"tonight."}""",
                    """{"done":true,"success":true,"answer":"Irrigate tonight.","error":null,"elapsed_ms":500}""",
                ),
            )

            val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
            val events = repo.askStream("water?", emptyContext()).toList()

            val deltas = events.filterIsInstance<LocalLlmStreamEvent.Delta>().map { it.text }
            assertEquals(listOf("Irrigate ", "tonight."), deltas)
            val final = events.last() as LocalLlmStreamEvent.Final
            assertTrue(final.success)
            assertEquals("Irrigate tonight.", final.answer)
        }

    // Without this safety net, a stream that never sends a "done" line would
    // complete with no Final event at all — ChatbotViewModel only clears a
    // message's "generating" flag on Final, so the chat bubble would be
    // stuck showing the typing indicator forever.
    @Test
    fun `askStream always resolves to a Final event even when the stream ends without one`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = mockk<LocalLlmApiService>()
            coEvery { api.status() } returns Response.success(statusDto("MODEL_AVAILABLE"))
            coEvery { api.chatStream(any()) } returns Response.success(
                ndjsonBody("""{"delta":"partial answer, then the connection just closes"}"""),
            )

            val repo = LocalLlmRepositoryImpl(backgroundScope, api, testJson)
            val events = repo.askStream("water?", emptyContext()).toList()

            val final = events.last()
            assertTrue(final is LocalLlmStreamEvent.Final)
            assertEquals(false, (final as LocalLlmStreamEvent.Final).success)
        }
}
