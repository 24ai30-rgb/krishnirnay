package com.krishinirnay.feature.chatbot

import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmDiagnostics
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.voice.VoiceState

data class ChatbotUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isListening: Boolean = false,
    // Never a fake "AI online" status — see LocalLlmRepository.
    val aiStatus: LocalLlmStatus = LocalLlmStatus.LOADING,
    /** Which backend [aiStatus] reflects — on-device, this app's own server, or neither (Phase 5). */
    val aiProviderKind: AiProviderKind = AiProviderKind.NONE,
    val voiceState: VoiceState = VoiceState.READY,
    val isCheckingConnection: Boolean = false,
    val diagnostics: LocalLlmDiagnostics? = null,
    /** True while a Local LLM stream is in flight — shows the Stop button and disables Send. */
    val isGenerating: Boolean = false,
)
