package com.krishinirnay.feature.dashboard

import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.decision.RecommendationOutcome
import java.time.Instant

data class DashboardUiState(
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,

    val recommendation: RecommendationOutcome? = null,

    val waterStressRisk: RiskLevel = RiskLevel.UNKNOWN,

    val heatRisk: RiskLevel = RiskLevel.UNKNOWN,

    val cropHealthRisk: RiskLevel = RiskLevel.UNKNOWN,

    // Live sensor values
    val soilMoisturePct: Float = 0f,

    val temperatureC: Float = 0f,

    val humidityPct: Float = 0f,

    // ML confidence
    val riskConfidence: Float = 0f,

    val isDeviceOnline: Boolean = true,

    val lastSyncedAt: Instant = Instant.EPOCH,
)