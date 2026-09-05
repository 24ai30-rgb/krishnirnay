package com.krishinirnay.data

import com.krishinirnay.core.data.composite.DefaultFieldStateRepository
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DeviceStatus
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.data.repository.SettingsRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun fieldState(fieldId: String, source: AppMode, history: List<SensorReading> = emptyList()) = FieldState(
    fieldId = fieldId,
    sensors = SensorReading(soilMoisturePct = 60f, temperatureC = 25f, humidityPct = 55f, timestamp = Instant.EPOCH),
    deviceStatus = DeviceStatus(isOnline = true, lastSeenAt = Instant.EPOCH),
    decision = DecisionOutput(
        overallRisk = RiskLevel.LOW,
        waterStressRisk = RiskLevel.LOW,
        heatRisk = RiskLevel.LOW,
        cropHealthRisk = RiskLevel.UNKNOWN,
        recommendation = RecommendationOutcome.NotEnoughData,
        confidence = 1f,
        reasons = emptyList(),
    ),
    diseaseResult = null,
    history = history,
    dataSource = source,
)

private class FakeFieldStateRepository(initial: FieldState) : FieldStateRepository {
    private val state = MutableStateFlow(initial)
    override val fieldState: StateFlow<FieldState> = state.asStateFlow()
    override val syncStatus: StateFlow<SyncStatus> =
        MutableStateFlow(SyncStatus(isOnline = true, lastSyncedAt = null, source = initial.dataSource)).asStateFlow()

    var refreshCallCount = 0
        private set

    override suspend fun refresh() {
        refreshCallCount++
    }

    override suspend fun recordDiseaseResult(result: DiseaseResult) = Unit
}

private class FakeSettingsRepository(initialMode: AppMode) : SettingsRepository {
    private val mode = MutableStateFlow(initialMode)
    override val appMode: StateFlow<AppMode> = mode.asStateFlow()
    override val language: StateFlow<String> = MutableStateFlow("en").asStateFlow()
    override val hasSeenHowItWorks: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val lastSyncedAt: StateFlow<Instant?> = MutableStateFlow<Instant?>(null).asStateFlow()

    override suspend fun setAppMode(mode: AppMode) {
        this.mode.value = mode
    }

    override suspend fun setLanguage(languageTag: String) = Unit
    override suspend fun setHasSeenHowItWorks(seen: Boolean) = Unit
    override suspend fun setLastSyncedAt(instant: Instant) = Unit
}

class DefaultFieldStateRepositoryTest {

    // UnconfinedTestDispatcher rather than the plain default: the eager
    // stateIn collector needs to react to appMode changes immediately,
    // not only when explicitly pumped via advanceUntilIdle() — see the
    // fix history for why the default StandardTestDispatcher wasn't
    // reliably unsticking it.

    @Test
    fun `starts on the mock source by default`() = runTest(UnconfinedTestDispatcher()) {
        val mock = FakeFieldStateRepository(fieldState("mock-1", AppMode.MOCK))
        val live = FakeFieldStateRepository(fieldState("live-1", AppMode.LIVE))
        val settings = FakeSettingsRepository(initialMode = AppMode.MOCK)
        val repo = DefaultFieldStateRepository(mock, live, settings, backgroundScope)

        assertEquals("mock-1", repo.fieldState.value.fieldId)
    }

    @Test
    fun `switches to the live source when appMode changes`() = runTest(UnconfinedTestDispatcher()) {
        val mock = FakeFieldStateRepository(fieldState("mock-1", AppMode.MOCK))
        val live = FakeFieldStateRepository(fieldState("live-1", AppMode.LIVE))
        val settings = FakeSettingsRepository(initialMode = AppMode.MOCK)
        val repo = DefaultFieldStateRepository(mock, live, settings, backgroundScope)

        settings.setAppMode(AppMode.LIVE)
        advanceUntilIdle()

        assertEquals("live-1", repo.fieldState.value.fieldId)
    }

    @Test
    fun `refresh delegates to whichever source is currently active`() = runTest(UnconfinedTestDispatcher()) {
        val mock = FakeFieldStateRepository(fieldState("mock-1", AppMode.MOCK))
        val live = FakeFieldStateRepository(fieldState("live-1", AppMode.LIVE))
        val settings = FakeSettingsRepository(initialMode = AppMode.LIVE)
        val repo = DefaultFieldStateRepository(mock, live, settings, backgroundScope)

        repo.refresh()

        assertEquals(0, mock.refreshCallCount)
        assertEquals(1, live.refreshCallCount)
    }

    @Test
    fun `each source keeps its own history so switching never mixes histories`() = runTest(UnconfinedTestDispatcher()) {
        val mockOnlyReading = SensorReading(soilMoisturePct = 40f, temperatureC = 30f, humidityPct = 50f, timestamp = Instant.EPOCH)
        val mock = FakeFieldStateRepository(fieldState("mock-1", AppMode.MOCK, history = listOf(mockOnlyReading)))
        val live = FakeFieldStateRepository(fieldState("live-1", AppMode.LIVE, history = emptyList()))
        val settings = FakeSettingsRepository(initialMode = AppMode.MOCK)
        val repo = DefaultFieldStateRepository(mock, live, settings, backgroundScope)

        assertEquals(1, repo.fieldState.value.history.size)

        settings.setAppMode(AppMode.LIVE)
        advanceUntilIdle()

        assertEquals(0, repo.fieldState.value.history.size)
    }
}
