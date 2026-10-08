package com.krishinirnay.core.llm.local

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Talks only to this app's own FastAPI server — never directly to Ollama or
 * any local-network inference endpoint. The server holds the connection
 * details ([server's Settings.local_llm_url]); Android only ever knows this
 * one contract, exactly like every other provider-backed feature
 * (Weather/Market) in this app.
 */
interface LocalLlmApiService {

    @POST("v1/local-llm/chat")
    suspend fun chat(@Body request: LocalLlmChatRequestDto): Response<LocalLlmChatResponseDto>

    /**
     * Newline-delimited JSON progress events — see the server's
     * `chat_stream()` docstring. [Streaming] tells OkHttp not to buffer the
     * whole body before returning, so [LocalLlmRepositoryImpl.askStream]
     * can read and show each line as it arrives instead of waiting for the
     * full ~10-30s generation (Part 1 — the Local LLM must feel fast).
     */
    @Streaming
    @POST("v1/local-llm/chat/stream")
    suspend fun chatStream(@Body request: LocalLlmChatRequestDto): Response<ResponseBody>

    /** [deep] = true makes the server run one real tiny generation to prove the model actually answers — see check_status(deep=...) server-side. */
    @GET("v1/local-llm/status")
    suspend fun status(@Query("deep") deep: Boolean = false): Response<LocalLlmStatusResponseDto>
}
