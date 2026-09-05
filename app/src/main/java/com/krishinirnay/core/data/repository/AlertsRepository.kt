package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.Alert
import kotlinx.coroutines.flow.StateFlow

/**
 * Derived, never independently computed — backed by
 * [com.krishinirnay.core.data.composite.AlertGenerator], which observes
 * [FieldStateRepository.fieldState] and appends an [Alert] whenever
 * `decision.overallRisk` steps up or a disease scan returns HIGH. This
 * guarantees the Alerts screen always agrees with whatever produced the
 * risk shown on Dashboard/AI Insights.
 */
interface AlertsRepository {
    val alerts: StateFlow<List<Alert>>
}
