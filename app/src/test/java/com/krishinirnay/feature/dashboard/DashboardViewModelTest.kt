package com.krishinirnay.feature.dashboard

import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DeviceStatus
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.decision.RecommendationOutcome
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the Phase 3B fix: [FieldState.toDashboardUiState] must never let
 * the separate ML risk-fusion classifier (`apiRisk`/`mlRisk`) mask a genuine HIGH from
 * [DecisionOutput.overallRisk] — DecisionEngine's own output, which since Phase 3A already
 * fuses water/heat/pest/disease/region/rain. Before the fix, `mlRisk != null -> mlRisk`
 * fired before `decision.overallRisk` was ever consulted, so a pest/disease-driven HIGH
 * could be silently downgraded to whatever the ML classifier happened to return.
 *
 * Sensors are kept in the "healthy/normal" range (moisture 50%, temp 25°C) in every case
 * here specifically so the sensor-critical branches (`criticalWaterRisk`/`extremeHeat`/
 * `mediumWaterRisk`/`mediumHeat`) never fire — that isolates exactly the ML-vs-decision
 * fusion behavior under test, which is where the bug lived.
 */
class DashboardViewModelTest {

    private fun sensors(moisturePct: Float = 50f, temperatureC: Float = 25f) = SensorReading(
        soilMoisturePct = moisturePct,
        temperatureC = temperatureC,
        humidityPct = 55f,
        timestamp = Instant.EPOCH,
    )

    private fun decision(overallRisk: RiskLevel, pestRisk: RiskLevel = RiskLevel.UNKNOWN) = DecisionOutput(
        overallRisk = overallRisk,
        waterStressRisk = RiskLevel.LOW,
        heatRisk = RiskLevel.LOW,
        cropHealthRisk = RiskLevel.UNKNOWN,
        recommendation = RecommendationOutcome.HealthyRange,
        confidence = 1f,
        reasons = emptyList(),
        pestRisk = pestRisk,
    )

    private fun fieldState(overallRisk: RiskLevel, pestRisk: RiskLevel = RiskLevel.UNKNOWN) = FieldState(
        fieldId = "test-field",
        sensors = sensors(),
        deviceStatus = DeviceStatus(isOnline = true, lastSeenAt = Instant.EPOCH),
        decision = decision(overallRisk, pestRisk),
        dataSource = AppMode.LIVE,
    )

    private val syncStatus = SyncStatus(isOnline = true, lastSyncedAt = Instant.EPOCH, source = AppMode.LIVE)
    private val profile = FarmerProfile(name = "Test", phone = "0", location = "", farmSizeAcres = 1f, crops = listOf("Cotton"))
    private val weather = WeatherState(
        locationLabel = "", currentTempC = 28, condition = WeatherCondition.CLOUDY, windKph = 0,
        humidityPct = 50, rainChancePct = 0, rainInHoursLabel = "-", daily = emptyList(),
        status = DataSourceStatus.UNAVAILABLE,
    )
    private val market = MarketState(
        crop = "Cotton", market = null, location = null, currentPricePerQuintal = null,
        minPricePerQuintal = null, maxPricePerQuintal = null, averagePricePerQuintal = null,
        fetchedAt = null, source = null, status = DataSourceStatus.UNAVAILABLE,
    )

    private fun uiStateFor(fieldState: FieldState, apiRisk: Int?, apiConfidence: Float = 0f) =
        fieldState.toDashboardUiState(
            syncStatus = syncStatus,
            apiRisk = apiRisk,
            apiConfidence = apiConfidence,
            profile = profile,
            weather = weather,
            market = market,
        )

    // TEST 1: ML=LOW, Decision=HIGH -> final must be HIGH (the exact bug scenario).
    @Test
    fun `a HIGH decision is never masked by an ML risk of LOW`() {
        val uiState = uiStateFor(fieldState(overallRisk = RiskLevel.HIGH), apiRisk = 0) // 0 = LOW
        assertEquals(RiskLevel.HIGH, uiState.overallRisk)
    }

    // TEST 2 (also covers "ML=MEDIUM, Decision=HIGH -> HIGH"): a pest-driven HIGH must survive.
    @Test
    fun `a pest-driven HIGH decision is never masked by an ML risk of MEDIUM`() {
        val uiState = uiStateFor(
            fieldState(overallRisk = RiskLevel.HIGH, pestRisk = RiskLevel.HIGH),
            apiRisk = 1, // 1 = MEDIUM
        )
        assertEquals(RiskLevel.HIGH, uiState.overallRisk)
        assertEquals(RiskLevel.HIGH, uiState.pestRisk)
    }

    // TEST 3: LOW and MEDIUM decisions must still come through correctly (fusion untouched).
    @Test
    fun `ML=LOW and Decision=LOW yields LOW`() {
        val uiState = uiStateFor(fieldState(overallRisk = RiskLevel.LOW), apiRisk = 0)
        assertEquals(RiskLevel.LOW, uiState.overallRisk)
    }

    @Test
    fun `ML=MEDIUM and Decision=MEDIUM yields MEDIUM`() {
        val uiState = uiStateFor(fieldState(overallRisk = RiskLevel.MEDIUM), apiRisk = 1)
        assertEquals(RiskLevel.MEDIUM, uiState.overallRisk)
    }

    // TEST 4: with no ML result at all, a valid non-HIGH decision passes through untouched
    // (never replaced by an unrelated heuristic) — and a HIGH decision still isn't masked
    // by the *absence* of an ML opinion either.
    @Test
    fun `no ML result yet leaves a valid MEDIUM decision untouched`() {
        val uiState = uiStateFor(fieldState(overallRisk = RiskLevel.MEDIUM), apiRisk = null)
        assertEquals(RiskLevel.MEDIUM, uiState.overallRisk)
    }

    @Test
    fun `no ML result yet still surfaces a real HIGH decision`() {
        val uiState = uiStateFor(fieldState(overallRisk = RiskLevel.HIGH), apiRisk = null)
        assertEquals(RiskLevel.HIGH, uiState.overallRisk)
    }
}
