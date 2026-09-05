package com.krishinirnay.core.data.network

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.local.FieldStateCache
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DeviceStatus
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.decision.DecisionEngine
import com.krishinirnay.core.decision.DecisionInput
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.network.SensorApiService
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class LiveFieldStateRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val sensorApiService: SensorApiService,
    private val fieldStateCache: FieldStateCache,
    private val settingsRepository: SettingsRepository,
) : FieldStateRepository {

    private val _fieldState = MutableStateFlow(initialFieldState())

    override val fieldState: StateFlow<FieldState> =
        _fieldState.asStateFlow()

    private val _syncStatus = MutableStateFlow(
        SyncStatus(
            isOnline = false,
            lastSyncedAt = null,
            source = AppMode.LIVE,
        ),
    )

    override val syncStatus: StateFlow<SyncStatus> =
        _syncStatus.asStateFlow()

    init {
        scope.launch {

            // Restore cached state first.
            fieldStateCache.load()?.let { cached ->
                _fieldState.value = cached.copy(
                    dataSource = AppMode.LIVE,
                )

                _syncStatus.value = SyncStatus(
                    isOnline = cached.deviceStatus.isOnline,
                    lastSyncedAt = cached.deviceStatus.lastSeenAt,
                    source = AppMode.LIVE,
                )
            }

            // Continuously check ESP32 sensor data.
            while (isActive) {
                fetchLatestSensor()
                delay(5000)
            }
        }
    }

    override suspend fun refresh() {
        fetchLatestSensor()
    }

    override suspend fun recordDiseaseResult(
        result: DiseaseResult,
    ) {
        val current = _fieldState.value

        val decision = DecisionEngine.evaluate(
            DecisionInput(
                sensors = current.sensors,
                modelOutput = null,
                diseaseResult = result,
                deviceOnline = current.deviceStatus.isOnline,
            ),
        )

        val next = current.copy(
            diseaseResult = result,
            decision = decision,
        )

        _fieldState.value = next
        fieldStateCache.save(next)
    }

    private suspend fun fetchLatestSensor() {
        try {
            val response = sensorApiService.getLatestSensor()

            if (!response.isSuccessful) {
                throw RuntimeException(
                    "Sensor API HTTP ${response.code()}",
                )
            }

            val body = response.body()
                ?: throw RuntimeException(
                    "Empty sensor response",
                )

            val data = body.data
                ?: throw RuntimeException(
                    "Sensor data not available yet",
                )

            val timestamp = parseTimestamp(data.timestamp)

            val sensors = SensorReading(
                soilMoisturePct = data.soil_moisture,
                temperatureC = data.temperature,
                humidityPct = data.humidity,
                timestamp = timestamp,
            )

            val previous = _fieldState.value

            val decision = DecisionEngine.evaluate(
                DecisionInput(
                    sensors = sensors,
                    modelOutput = null,
                    diseaseResult = previous.diseaseResult,
                    deviceOnline = true,
                ),
            )

            val next = FieldState(
                fieldId = LIVE_FIELD_ID,
                sensors = sensors,
                deviceStatus = DeviceStatus(
                    isOnline = true,
                    lastSeenAt = timestamp,
                ),
                decision = decision,
                diseaseResult = previous.diseaseResult,
                history = (
                    previous.history + sensors
                ).takeLast(HISTORY_LIMIT),
                dataSource = AppMode.LIVE,
            )

            _fieldState.value = next

            _syncStatus.value = SyncStatus(
                isOnline = true,
                lastSyncedAt = timestamp,
                source = AppMode.LIVE,
            )

            settingsRepository.setLastSyncedAt(timestamp)

            fieldStateCache.save(next)

            android.util.Log.d(
                "ESP32_SENSOR",
                "Sensor data received successfully. " +
                    "Moisture=${data.soil_moisture}, " +
                    "Temperature=${data.temperature}, " +
                    "Humidity=${data.humidity}",
            )

        } catch (error: Exception) {

            android.util.Log.e(
                "ESP32_SENSOR",
                "Sensor fetch failed: ${error.message}",
                error,
            )

            /*
             * IMPORTANT:
             * Do not overwrite the last valid sensor state.
             * Keep the cached state available for the UI.
             */
            val current = _fieldState.value

            _syncStatus.value = SyncStatus(
                isOnline = current.deviceStatus.isOnline,
                lastSyncedAt = current.deviceStatus.lastSeenAt,
                source = AppMode.LIVE,
            )

            /*
             * Keep the last known device status instead of
             * immediately forcing the dashboard to Offline.
             *
             * This prevents a temporary network/sensor request
             * failure from destroying the last valid state.
             */
            _fieldState.value = current.copy(
                dataSource = AppMode.LIVE,
            )
        }
    }

    private fun parseTimestamp(
        value: String?,
    ): Instant {

        if (value.isNullOrBlank()) {
            return Instant.now()
        }

        return runCatching {
            Instant.parse(value)
        }.getOrElse {
            Instant.now()
        }
    }

    private fun initialFieldState(): FieldState {

        val now = Instant.now()

        val sensors = SensorReading(
            soilMoisturePct = 0f,
            temperatureC = 0f,
            humidityPct = 0f,
            timestamp = now,
        )

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
            fieldId = LIVE_FIELD_ID,
            sensors = sensors,
            deviceStatus = DeviceStatus(
                isOnline = false,
                lastSeenAt = now,
            ),
            decision = decision,
            diseaseResult = null,
            history = emptyList(),
            dataSource = AppMode.LIVE,
        )
    }

    private companion object {

        const val LIVE_FIELD_ID = "esp32-field-1"

        const val HISTORY_LIMIT = 100
    }
}