package com.krishinirnay.core.decision

/**
 * Threshold table backing [DecisionEngine]. Named constants, not magic
 * numbers, so the rules are easy to defend to judges and easy to
 * unit-test as a table (see DecisionEngineTest).
 */
object DecisionRules {
    // Water stress — soil moisture percent. Used as the rule-based
    // fallback when Model 1's on-device prediction is unavailable.
    const val SOIL_MOISTURE_HIGH_RISK_BELOW_PCT = 20f
    const val SOIL_MOISTURE_MEDIUM_RISK_BELOW_PCT = 40f

    // Heat — temperature in Celsius. Phase 1 has no dedicated heat model
    // (that's Model 4, Phase 2 risk fusion), so this is rule-only.
    const val TEMPERATURE_HIGH_RISK_AT_OR_ABOVE_C = 38f
    const val TEMPERATURE_MEDIUM_RISK_AT_OR_ABOVE_C = 33f

    // NPK / pH — Phase 2 sensors. Rules exist so the architecture has
    // room for them without blocking Phase 1; they're only applied when
    // the corresponding reading is non-null.
    const val PH_LOW_RISK_MIN = 6.0f
    const val PH_LOW_RISK_MAX = 7.5f
    const val PH_MEDIUM_RISK_MIN = 5.5f
    const val PH_MEDIUM_RISK_MAX = 8.0f
}
