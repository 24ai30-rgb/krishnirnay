package com.krishinirnay.data

import com.krishinirnay.core.common.DispatcherProvider
import com.krishinirnay.core.data.composite.FieldDecisionResolver
import com.krishinirnay.core.data.local.FieldStateCache
import com.krishinirnay.core.data.mock.MockFieldStateRepositoryImpl
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the Phase 3A requirement "Mock mode must use the same decision-engine
 * pathway as live mode": [MockFieldStateRepositoryImpl.recordPestResult] must plumb
 * [FieldDecisionResolver]'s output through into `fieldState.decision` verbatim,
 * exactly like [LiveFieldStateRepositoryImplTest] already proves for the Live side —
 * together, both repositories are shown to depend on and defer to the same single
 * resolver class rather than each computing a decision independently.
 */
class MockFieldStateRepositoryImplTest {

    // Sharing one dispatcher (and therefore one TestCoroutineScheduler) across
    // runTest and the fake DispatcherProvider — kotlinx-coroutines-test throws
    // if NarrativeEngine's coroutines and the test's end up on different
    // schedulers, since they'd advance virtual time independently.
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeDispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val default = testDispatcher
        override val io = testDispatcher
    }

    private fun pest() = PestResult(
        detected = true, label = "aphid", confidence = 0.8f, riskLevel = RiskLevel.MEDIUM,
        modelVersion = "pest-v1", scannedAt = Instant.EPOCH,
    )

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

    @Test
    fun `recordPestResult delegates verbatim to FieldDecisionResolver`() = runTest(testDispatcher) {
        val fieldStateCache = mockk<FieldStateCache>()
        val settingsRepository = mockk<SettingsRepository>()
        val fieldDecisionResolver = mockk<FieldDecisionResolver>()

        coEvery { fieldStateCache.load() } returns null
        coEvery { fieldStateCache.save(any()) } returns Unit
        every { fieldDecisionResolver.evaluate(any(), any(), any(), any(), any()) } returns distinctiveDecision()

        val repo = MockFieldStateRepositoryImpl(
            scope = backgroundScope,
            dispatcherProvider = fakeDispatcherProvider,
            fieldStateCache = fieldStateCache,
            settingsRepository = settingsRepository,
            fieldDecisionResolver = fieldDecisionResolver,
        )

        repo.recordPestResult(pest())

        assertEquals(distinctiveDecision(), repo.fieldState.value.decision)
    }
}
