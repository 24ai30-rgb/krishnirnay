package com.krishinirnay.core.voice

/**
 * Farmer speaks -> LISTENING; local speech-to-text finished, resolving an
 * answer (keyword match or Local LLM) -> PROCESSING; speaking the answer
 * back -> SPEAKING; idle -> READY; recognition/synthesis failed -> ERROR.
 * Never a network/cloud state — see ChatbotViewModel, the only current
 * consumer of this enum.
 */
enum class VoiceState {
    READY,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
}
