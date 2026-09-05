package com.krishinirnay.core.llm

import com.krishinirnay.core.llm.dto.ChatRequestDto
import com.krishinirnay.core.llm.dto.ChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApiService {

    @POST("v1/chat")
    suspend fun chat(
        @Body request: ChatRequestDto,
    ): Response<ChatResponseDto>
}