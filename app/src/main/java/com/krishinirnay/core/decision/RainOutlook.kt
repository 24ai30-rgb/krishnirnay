package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.WeatherState

/**
 * A coarse rain signal [DecisionEngine] can reason about, deliberately smaller than
 * the full `WeatherState` model — the pure decision layer stays free of network/DTO
 * imports, matching [DecisionInput]'s existing shape. Derived from `WeatherRepository`'s
 * current state via [toRainOutlook] — see
 * [com.krishinirnay.core.data.composite.FieldDecisionResolver], the one place that
 * actually calls it in the live app flow.
 */
enum class RainOutlook {
    RAIN_EXPECTED_SOON,
    DRY,
    UNKNOWN,
}

/**
 * [RainOutlook.UNKNOWN] whenever the weather itself is [DataSourceStatus.UNAVAILABLE] —
 * never a guessed forecast. Mock Mode's fully-populated synthetic forecast is
 * intentionally allowed to drive this (exactly like every other DecisionEngine input in
 * Mock Mode), so the whole pipeline — including rain-aware logic — stays demoable
 * without a real weather provider configured.
 */
fun WeatherState.toRainOutlook(): RainOutlook = when (status) {
    DataSourceStatus.UNAVAILABLE -> RainOutlook.UNKNOWN
    else -> if (rainChancePct >= DecisionRules.RAIN_EXPECTED_SOON_AT_OR_ABOVE_PCT) {
        RainOutlook.RAIN_EXPECTED_SOON
    } else {
        RainOutlook.DRY
    }
}
