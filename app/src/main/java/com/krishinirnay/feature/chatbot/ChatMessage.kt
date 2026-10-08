package com.krishinirnay.feature.chatbot

/** Screen-local — no persistence in Phase 1, resets when the screen is left. */
data class ChatMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    /** True while a Local LLM stream is still writing into [text] — drives the typing/streaming indicator. */
    val isGenerating: Boolean = false,
    /** True when [text] is an honest "couldn't answer" message rather than a real answer — offers Retry instead of Copy. */
    val isError: Boolean = false,
)
