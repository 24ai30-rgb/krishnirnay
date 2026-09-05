package com.krishinirnay.core.data.model

import java.time.Instant

/**
 * Derived, never independently computed — an `AlertGenerator` appends
 * one whenever `decision.overallRisk` steps up or a disease scan returns
 * HIGH. This is what guarantees the Alerts screen always agrees with
 * whatever produced the risk shown on Dashboard/AI Insights.
 */
data class Alert(
    val id: String,
    val timestamp: Instant,
    val riskLevel: RiskLevel,
    val title: String,
    val message: String,
)
