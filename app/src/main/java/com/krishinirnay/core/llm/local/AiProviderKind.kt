package com.krishinirnay.core.llm.local

/**
 * Which AI backend actually answered (or would answer) the last/next
 * question — see [AiProviderCoordinator]. The UI never needs to know more
 * than this plus [LocalLlmStatus] to render one of the four states Phase 5
 * requires: "On-device AI Ready" / "Local Server AI Ready" / "Offline AI" /
 * "AI Unavailable".
 */
enum class AiProviderKind {
    /** A model running directly on this device answered/would answer — no PC, no network required. */
    ON_DEVICE,

    /** This app's own FastAPI + Ollama server answered/would answer — requires that server to be reachable. */
    SERVER,

    /** Neither is currently usable. */
    NONE,
}
