package com.krishinirnay.data

import com.krishinirnay.core.data.composite.FieldDecisionResolver
import com.krishinirnay.core.data.local.FieldStateCache
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.network.LiveFieldStateRepositoryImpl
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import com.krishinirnay.core.network.SensorApiService
import com.krishinirnay.core.network.dto.LatestSensorResponseDto
import com.krishinirnay.core.network.dto.SensorDataDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

private fun stubDecision() = DecisionOutput(
    overallRisk = RiskLevel.UNKNOWN,
    waterStressRisk = RiskLevel.UNKNOWN,
    heatRisk = RiskLevel.UNKNOWN,
    cropHealthRisk = RiskLevel.UNKNOWN,
    recommendation = RecommendationOutcome.NotEnoughData,
    confidence = 0f,
    reasons = emptyList(),
)

/** A distinctive value nothing in LiveFieldStateRepositoryImpl could produce by coincidence. */
private fun distinctiveDecision() = DecisionOutput(
    overallRisk = RiskLevel.HIGH,
    waterStressRisk = RiskLevel.LOW,
    heatRisk = RiskLevel.LOW,
    cropHealthRisk = RiskLevel.UNKNOWN,
    recommendation = RecommendationOutcome.TreatPestDetected,
    confidence = 0.42f,
    reasons = emptyList(),
    pestRisk = RiskLevel.HIGH,
    // Phase 4B: proves fertilizer flows through this same delegation unchanged too.
    fertilizerRecommendation = FertilizerRecommendation.NoActionNeeded,
)

/**
 * Regression test for the "stale cache mistaken for live" bug fixed in
 * [LiveFieldStateRepositoryImpl]: a failed poll must keep showing the
 * last known sensor values (never blank them), but must honestly flag
 * the connection as offline/stale rather than silently continuing to
 * report `isOnline = true` forever, as it did before this fix.
 */
class LiveFieldStateRepositoryImplTest {

    @Test
    fun `a failed poll keeps the last known sensor values but marks the device offline`() =
        runTest(UnconfinedTestDispatcher()) {
            val sensorApiService = mockk<SensorApiService>()
            val fieldStateCache = mockk<FieldStateCache>(relaxed = true)
            val settingsRepository = mockk<SettingsRepository>(relaxed = true)
            val fieldDecisionResolver = mockk<FieldDecisionResolver>()
            every { fieldDecisionResolver.evaluate(any(), any(), any(), any(), any()) } returns stubDecision()

            coEvery { fieldStateCache.load() } returns null
            coEvery { sensorApiService.getLatestSensor() } returns Response.success(
                LatestSensorResponseDto(
                    data = SensorDataDto(
                        temperature = 28f,
                        humidity = 60f,
                        soil_moisture = 27f,
                        timestamp = null,
                    ),
                ),
            )

            val repo = LiveFieldStateRepositoryImpl(
                scope = backgroundScope,
                sensorApiService = sensorApiService,
                fieldStateCache = fieldStateCache,
                settingsRepository = settingsRepository,
                fieldDecisionResolver = fieldDecisionResolver,
            )

            // The init{} block's first tick already ran (UnconfinedTestDispatcher
            // executes eagerly up to the first real suspension, delay(5000)).
            assertEquals(27f, repo.fieldState.value.sensors.soilMoisturePct)
            assertEquals(true, repo.syncStatus.value.isOnline)
            assertEquals(true, repo.fieldState.value.deviceStatus.isOnline)

            coEvery { sensorApiService.getLatestSensor() } throws RuntimeException("server unreachable")

            repo.refresh()

            // Last known values must still be on screen...
            assertEquals(27f, repo.fieldState.value.sensors.soilMoisturePct)
            assertEquals(28f, repo.fieldState.value.sensors.temperatureC)

            // ...but the connection must now be honestly reported as offline/stale.
            assertEquals(false, repo.syncStatus.value.isOnline)
            assertEquals(false, repo.fieldState.value.deviceStatus.isOnline)
        }

    // TEST 5 (Phase 4B): Live repository uses the same FieldDecisionResolver pathway —
    // recordPestResult must plumb its output (fertilizer included) through verbatim,
    // exactly like MockFieldStateRepositoryImplTest proves for the Mock side.
    @Test
    fun `recordPestResult delegates verbatim to FieldDecisionResolver, fertilizer included`() = runTest(UnconfinedTestDispatcher()) {
        val sensorApiService = mockk<SensorApiService>()
        val fieldStateCache = mockk<FieldStateCache>()
        val settingsRepository = mockk<SettingsRepository>()
        val fieldDecisionResolver = mockk<FieldDecisionResolver>()

        // Explicit stubs for everything the init{} block's background fetch loop
        // touches — no relaxed/unstubbed calls, so there is nothing for MockK to
        // auto-generate a fallback answer for.
        coEvery { fieldStateCache.load() } returns null
        coEvery { fieldStateCache.save(any()) } returns Unit
        coEvery { sensorApiService.getLatestSensor() } throws RuntimeException("not exercised by this test")
        every { fieldDecisionResolver.evaluate(any(), any(), any(), any(), any()) } returns distinctiveDecision()

        val repo = LiveFieldStateRepositoryImpl(
            scope = backgroundScope,
            sensorApiService = sensorApiService,
            fieldStateCache = fieldStateCache,
            settingsRepository = settingsRepository,
            fieldDecisionResolver = fieldDecisionResolver,
        )

        val pest = PestResult(
            detected = true, label = "aphid", confidence = 0.8f, riskLevel = RiskLevel.MEDIUM,
            modelVersion = "pest-v1", scannedAt = Instant.EPOCH,
        )
        repo.recordPestResult(pest)

        assertEquals(distinctiveDecision(), repo.fieldState.value.decision)
    }
}
