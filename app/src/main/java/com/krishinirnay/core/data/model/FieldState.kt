package com.krishinirnay.core.data.model

/**
 * The single source of truth every screen's ViewModel reads from (via
 * `FieldStateRepository.fieldState: StateFlow<FieldState>`). No screen
 * ever queries Firebase or the mock engine directly — this is what
 * guarantees Dashboard risk == Alerts risk for the same field state.
 */
data class FieldState(
    val fieldId: String,
    val sensors: SensorReading,
    val deviceStatus: DeviceStatus,
    val decision: DecisionOutput,
    val diseaseResult: DiseaseResult? = null,
    /** Capped ring buffer for Analytics — session-depth only, see docs/architecture.md. */
    val history: List<SensorReading> = emptyList(),
    val dataSource: AppMode,
)
