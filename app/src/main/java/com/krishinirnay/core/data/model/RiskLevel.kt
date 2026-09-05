package com.krishinirnay.core.data.model

/**
 * UNKNOWN means "not yet assessed," never "no risk." It must never be
 * silently treated as LOW — see DecisionEngine's overallRisk aggregation
 * and the Dashboard crop-health tile's neutral "Not yet assessed" state.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
}
