package com.krishinirnay.feature.dashboard

import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.decision.BenefitOutcome
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.decision.TimingOutcome
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import java.time.Instant

data class DashboardUiState(
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,

    val recommendation: RecommendationOutcome? = null,

    val waterStressRisk: RiskLevel = RiskLevel.UNKNOWN,

    val heatRisk: RiskLevel = RiskLevel.UNKNOWN,

    val cropHealthRisk: RiskLevel = RiskLevel.UNKNOWN,

    val pestRisk: RiskLevel = RiskLevel.UNKNOWN,

    // Live sensor values
    val soilMoisturePct: Float = 0f,

    val temperatureC: Float = 0f,

    val humidityPct: Float = 0f,

    // ML confidence
    val riskConfidence: Float = 0f,

    val isDeviceOnline: Boolean = true,

    val lastSyncedAt: Instant = Instant.EPOCH,

    // LIVE / CACHED / MOCK — never inferred separately in the UI, always
    // carried straight from FieldStateRepository.syncStatus.
    val dataSourceStatus: DataSourceStatus = DataSourceStatus.MOCK,

    // Today's Decision — WHAT/WHEN/WHY/BENEFIT (Phase 2.11)
    val timing: TimingOutcome = TimingOutcome.NoActionNeeded,
    val expectedBenefit: BenefitOutcome = BenefitOutcome.Unknown,
    val reasons: List<ReasonOutcome> = emptyList(),
    val fertilizerRecommendation: FertilizerRecommendation? = null,

    // Farm Status (Phase 2.11)
    val profile: FarmerProfile? = null,

    // Crop Health (Phase 2.9/2.11)
    val diseaseResult: DiseaseResult? = null,
    val pestResult: PestResult? = null,

    // Weather / Market (Phase 2.4/2.5/2.11)
    val weather: WeatherState? = null,
    val market: MarketState? = null,
)
