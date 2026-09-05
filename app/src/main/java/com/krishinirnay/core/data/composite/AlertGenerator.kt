package com.krishinirnay.core.data.composite

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.model.Alert
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.AlertsRepository
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.designsystem.strings.EnglishStrings
import com.krishinirnay.core.designsystem.strings.textFor
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Derives alerts from [FieldStateRepository.fieldState] — never a
 * separate source of truth, so Alerts always agrees with whatever
 * produced the risk shown on Dashboard/AI Insights.
 *
 * Three loophole fixes live here:
 * - **Cold-start alert storm**: the first-ever observation only
 *   establishes a baseline, it never alerts — so a fresh app launch
 *   never compares against a synthetic "no prior state" and fires a
 *   spurious "risk increased" alert.
 * - **Mock/Live mode switch leaves stale data**: whenever consecutive
 *   states differ in [FieldState.dataSource], that's treated the same
 *   as a first observation (fresh baseline, no alert) rather than a
 *   real risk change — this falls out naturally from watching
 *   `dataSource` rather than needing an explicit reset call from
 *   whichever repository is switching sources.
 * - **Synthetic seed as baseline**: a repository's placeholder seed value
 *   (e.g. [com.krishinirnay.core.data.mock.MockFieldStateRepositoryImpl]'s
 *   pre-cache-load default) reports [RiskLevel.UNKNOWN], not a guessed
 *   risk — so the real first reading that replaces it is treated as
 *   baseline-settling, not a risk change worth alerting on.
 *
 * Alerts are in-memory only for now (not yet DataStore-persisted) —
 * that lands with the Alerts screen itself.
 */
@Singleton
class AlertGenerator @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
    fieldStateRepository: FieldStateRepository,
) : AlertsRepository {

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    override val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private var previousState: FieldState? = null

    init {
        scope.launch {
            fieldStateRepository.fieldState.collect { state -> onFieldState(state) }
        }
    }

    private fun onFieldState(state: FieldState) {
        val previous = previousState
        previousState = state

        if (previous == null || previous.dataSource != state.dataSource) {
            return
        }

        // A transition out of UNKNOWN is the real baseline settling in (e.g. the synthetic
        // cold-start seed being replaced by a cache-restored or first live reading) — not an
        // actual risk change, so it must not fire an alert. See RiskLevel's doc comment.
        if (previous.decision.overallRisk == RiskLevel.UNKNOWN) {
            return
        }

        if (severity(state.decision.overallRisk) > severity(previous.decision.overallRisk)) {
            appendAlert(
                Alert(
                    id = UUID.randomUUID().toString(),
                    timestamp = state.sensors.timestamp,
                    riskLevel = state.decision.overallRisk,
                    title = titleFor(state.decision.overallRisk),
                    // Alerts aren't localized yet (pre-existing gap, same as their English
                    // titleFor() below) — pinned to English so behavior doesn't regress.
                    message = EnglishStrings.textFor(state.decision.recommendation),
                ),
            )
        }

        val diseaseResult = state.diseaseResult
        if (diseaseResult != null && diseaseResult.riskLevel == RiskLevel.HIGH && diseaseResult != previous.diseaseResult) {
            appendAlert(
                Alert(
                    id = UUID.randomUUID().toString(),
                    timestamp = diseaseResult.scannedAt,
                    riskLevel = RiskLevel.HIGH,
                    title = "Crop health risk detected",
                    message = "${diseaseResult.displayName} detected — ${(diseaseResult.confidence * 100).toInt()}% confidence",
                ),
            )
        }
    }

    private fun appendAlert(alert: Alert) {
        _alerts.value = (listOf(alert) + _alerts.value).take(ALERT_LIMIT)
    }

    private fun severity(risk: RiskLevel): Int = when (risk) {
        RiskLevel.LOW -> 0
        RiskLevel.MEDIUM -> 1
        RiskLevel.HIGH -> 2
        RiskLevel.UNKNOWN -> -1
    }

    private fun titleFor(risk: RiskLevel): String = when (risk) {
        RiskLevel.HIGH -> "Risk increased to High"
        RiskLevel.MEDIUM -> "Risk increased to Medium"
        else -> "Risk changed"
    }

    private companion object {
        const val ALERT_LIMIT = 50
    }
}
