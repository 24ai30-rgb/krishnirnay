package com.krishinirnay.core.mock

import com.krishinirnay.core.decision.DecisionRules

/**
 * Developer/tester-triggerable Mock Mode sensor scenarios — lets the app be
 * demonstrated and tested end to end without physical ESP32 hardware. Values
 * are chosen against the exact same thresholds [com.krishinirnay.core.decision.DecisionEngine]
 * uses ([DecisionRules]), so each scenario reliably exercises the risk level
 * its name promises — never an arbitrary or fabricated number. Applying a
 * scenario only ever changes Mock Mode's simulated readings; it can never be
 * mistaken for [com.krishinirnay.core.data.model.DataSourceStatus.LIVE] data,
 * see [NarrativeEngine.applyScenario].
 */
enum class SensorScenario {
    DRY_SOIL,
    NORMAL_SOIL,
    WET_SOIL,
    HIGH_TEMPERATURE,
    LOW_TEMPERATURE,
    HIGH_HUMIDITY,
    LOW_HUMIDITY,
}

internal data class ScenarioTarget(val moisturePct: Float, val temperatureC: Float, val humidityPct: Float)

internal fun SensorScenario.toTarget(): ScenarioTarget = when (this) {
    // Below SOIL_MOISTURE_HIGH_RISK_BELOW_PCT -> a real HIGH water-stress risk.
    SensorScenario.DRY_SOIL -> ScenarioTarget(
        moisturePct = DecisionRules.SOIL_MOISTURE_HIGH_RISK_BELOW_PCT - 8f,
        temperatureC = 28f,
        humidityPct = 45f,
    )
    SensorScenario.NORMAL_SOIL -> ScenarioTarget(moisturePct = 60f, temperatureC = 26f, humidityPct = 55f)
    SensorScenario.WET_SOIL -> ScenarioTarget(moisturePct = 85f, temperatureC = 25f, humidityPct = 65f)
    // At/above TEMPERATURE_HIGH_RISK_AT_OR_ABOVE_C -> a real HIGH heat risk.
    SensorScenario.HIGH_TEMPERATURE -> ScenarioTarget(
        moisturePct = 50f,
        temperatureC = DecisionRules.TEMPERATURE_HIGH_RISK_AT_OR_ABOVE_C + 2f,
        humidityPct = 40f,
    )
    SensorScenario.LOW_TEMPERATURE -> ScenarioTarget(moisturePct = 55f, temperatureC = 16f, humidityPct = 50f)
    // At/above HUMIDITY_DISEASE_ESCALATION_AT_OR_ABOVE_PCT -> real disease-risk escalation
    // once a disease scan + rain-expected-soon weather are also present.
    SensorScenario.HIGH_HUMIDITY -> ScenarioTarget(
        moisturePct = 55f,
        temperatureC = 27f,
        humidityPct = DecisionRules.HUMIDITY_DISEASE_ESCALATION_AT_OR_ABOVE_PCT + 10f,
    )
    SensorScenario.LOW_HUMIDITY -> ScenarioTarget(moisturePct = 50f, temperatureC = 30f, humidityPct = 25f)
}
