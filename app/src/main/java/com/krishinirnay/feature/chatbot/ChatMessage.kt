package com.krishinirnay.feature.chatbot

/** Screen-local — no persistence in Phase 1, resets when the screen is left. */
data class ChatMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
)
