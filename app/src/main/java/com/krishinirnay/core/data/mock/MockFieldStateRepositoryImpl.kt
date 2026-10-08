package com.krishinirnay.core.data.mock

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.common.DispatcherProvider
import com.krishinirnay.core.data.composite.FieldDecisionResolver
import com.krishinirnay.core.data.local.FieldStateCache
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DeviceStatus
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MockControls
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.decision.DecisionEngine
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.mock.NarrativeEngine
import com.krishinirnay.core.mock.SensorScenario
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Mock Mode's [FieldStateRepository] — a [NarrativeEngine] evolving
 * sensor readings, run through the same [DecisionEngine] Live Mode will
 * use, cached to DataStore on every update.
 */
@Singleton
class MockFieldStateRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val fieldStateCache: FieldStateCache,
    private val settingsRepository: SettingsRepository,
    private val fieldDecisionResolver: FieldDecisionResolver,
) : FieldStateRepository, MockControls {

    private val narrativeEngine = NarrativeEngine(dispatcherProvider = dispatcherProvider)

    private val _fieldState = MutableStateFlow(initialFieldState())
    override val fieldState: StateFlow<FieldState> = _fieldState.asStateFlow()

    private val _syncStatus = MutableStateFlow(
        SyncStatus(isOnline = true, lastSyncedAt = null, source = AppMode.MOCK),
    )
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        scope.launch {
            // Cache first, so cold start never shows blank/loading — see
            // FieldStateCache. The narrative engine's own initial reading
            // (identical defaults to initialFieldState()) is dropped
            // below so it can't clobber a just-restored cached value.
            fieldStateCache.load()?.let { cached ->
                _fieldState.value = cached.copy(dataSource = AppMode.MOCK)
            }

            narrativeEngine.start(scope)

            combine(narrativeEngine.sensorReading, narrativeEngine.deviceOnline) { sensors, online -> sensors to online }
                .drop(1)
                .collect { (sensors, online) -> updateFieldState(sensors, online) }
        }
    }

    override suspend fun refresh() {
        // The narrative engine ticks continuously on its own — refresh()
        // exists for interface parity with the Live Mode implementation
        // and so a pull-to-refresh gesture still feels responsive.
        val now = Instant.now()
        _syncStatus.value = _syncStatus.value.copy(lastSyncedAt = now)
        settingsRepository.setLastSyncedAt(now)
    }

    override suspend fun recordDiseaseResult(result: DiseaseResult) {
        val current = _fieldState.value
        val decision = fieldDecisionResolver.evaluate(
            sensors = current.sensors,
            modelOutput = null,
            diseaseResult = result,
            pestResult = current.pestResult,
            deviceOnline = current.deviceStatus.isOnline,
        )
        val next = current.copy(diseaseResult = result, decision = decision)
        _fieldState.value = next
        fieldStateCache.save(next)
    }

    override suspend fun recordPestResult(result: PestResult) {
        val current = _fieldState.value
        val decision = fieldDecisionResolver.evaluate(
            sensors = current.sensors,
            modelOutput = null,
            diseaseResult = current.diseaseResult,
            pestResult = result,
            deviceOnline = current.deviceStatus.isOnline,
        )
        val next = current.copy(pestResult = result, decision = decision)
        _fieldState.value = next
        fieldStateCache.save(next)
    }

    override fun triggerIrrigation() = narrativeEngine.triggerIrrigation()
    override fun triggerDeviceDisconnect() = narrativeEngine.triggerDeviceDisconnect()
    override fun triggerDeviceReconnect() = narrativeEngine.triggerDeviceReconnect()
    override fun applySensorScenario(scenario: SensorScenario) = narrativeEngine.applyScenario(scenario)

    private fun updateFieldState(sensors: SensorReading, online: Boolean) {
        val previous = _fieldState.value
        val decision = fieldDecisionResolver.evaluate(
            sensors = sensors,
            modelOutput = null, // Model 1 wiring lands with AI Insights/What-If
            diseaseResult = previous.diseaseResult,
            pestResult = previous.pestResult,
            deviceOnline = online,
        )

        val next = FieldState(
            fieldId = MOCK_FIELD_ID,
            sensors = sensors,
            deviceStatus = DeviceStatus(isOnline = online, lastSeenAt = sensors.timestamp),
            decision = decision,
            diseaseResult = previous.diseaseResult,
            pestResult = previous.pestResult,
            history = (previous.history + sensors).takeLast(HISTORY_LIMIT),
            dataSource = AppMode.MOCK,
        )

        _fieldState.value = next
        _syncStatus.value = SyncStatus(isOnline = online, lastSyncedAt = sensors.timestamp, source = AppMode.MOCK)

        scope.launch(dispatcherProvider.io) {
            fieldStateCache.save(next)
        }
    }

    /**
     * A placeholder seed shown for the instant before the cache-restored (or first
     * narrative-engine) reading arrives. Its risk is deliberately [RiskLevel.UNKNOWN] rather
     * than run through [DecisionEngine] against made-up sensor values — a confident LOW here
     * would become AlertGenerator's comparison baseline and fire a spurious "risk increased"
     * alert the moment the real (possibly higher-risk) state replaces it.
     */
    private fun initialFieldState(): FieldState {
        val now = Instant.now()
        val sensors = SensorReading(soilMoisturePct = 60f, temperatureC = 26f, humidityPct = 60f, timestamp = now)
        val decision = DecisionOutput(
            overallRisk = RiskLevel.UNKNOWN,
            waterStressRisk = RiskLevel.UNKNOWN,
            heatRisk = RiskLevel.UNKNOWN,
            cropHealthRisk = RiskLevel.UNKNOWN,
            recommendation = RecommendationOutcome.NotEnoughData,
            confidence = 0f,
            reasons = emptyList(),
        )
        return FieldState(
            fieldId = MOCK_FIELD_ID,
            sensors = sensors,
            deviceStatus = DeviceStatus(isOnline = true, lastSeenAt = now),
            decision = decision,
            diseaseResult = null,
            history = emptyList(),
            dataSource = AppMode.MOCK,
        )
    }

    private companion object {
        const val MOCK_FIELD_ID = "mock-field-1"
        const val HISTORY_LIMIT = 100
    }
}
