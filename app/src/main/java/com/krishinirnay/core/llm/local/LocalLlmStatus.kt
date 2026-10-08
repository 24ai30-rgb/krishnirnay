package com.krishinirnay.core.llm.local

/**
 * Never display a fake "AI online" status — [READY] is only reached after a
 * real, successful check against the server's `/v1/local-llm/status`, which
 * itself only reports READY after really reaching the local model server *and*
 * confirming the configured model is actually installed.
 *
 * The rest of the app (Decision Engine, sensors, weather, market) must keep
 * working exactly as before regardless of this status — see [LocalLlmRepository].
 */
enum class LocalLlmStatus {
    /** Local model server reachable and the configured model is installed. */
    READY,

    /** A reachability check is in flight. */
    LOADING,

    /** Answering right now — local inference takes tens of seconds. */
    GENERATING,

    /** Server is reachable but the configured model has not been pulled. */
    MODEL_MISSING,

    /** No local model server reachable at all. */
    UNAVAILABLE,

    /** The check or a generation attempt failed outright. */
    ERROR,
}
