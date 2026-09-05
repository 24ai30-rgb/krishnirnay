package com.krishinirnay.core.mock

enum class ScenarioStage {
    HEALTHY,
    DRYING,
    STRESSED,
    IRRIGATED,
}

data class StageSpec(
    val stage: ScenarioStage,
    val moistureTarget: Float,
    val temperatureTarget: Float,
    val humidityTarget: Float,
    val durationSimMinutes: Long,
)

/**
 * Staged narrative the [NarrativeEngine] walks through: Healthy -> Drying
 * -> Stressed (fires an Alert, once Alerts exist) -> Irrigated (recovers),
 * then loops. [NarrativeEngine.triggerIrrigation] lets a presenter jump
 * straight to the recovery beat on cue instead of waiting on the
 * schedule.
 */
object ScenarioScript {
    val stages: List<StageSpec> = listOf(
        StageSpec(
            stage = ScenarioStage.HEALTHY,
            moistureTarget = 60f,
            temperatureTarget = 26f,
            humidityTarget = 60f,
            durationSimMinutes = 90,
        ),
        StageSpec(
            stage = ScenarioStage.DRYING,
            moistureTarget = 22f,
            temperatureTarget = 29f,
            humidityTarget = 45f,
            durationSimMinutes = 180,
        ),
        StageSpec(
            stage = ScenarioStage.STRESSED,
            moistureTarget = 12f,
            temperatureTarget = 32f,
            humidityTarget = 35f,
            durationSimMinutes = 60,
        ),
        StageSpec(
            stage = ScenarioStage.IRRIGATED,
            moistureTarget = 62f,
            temperatureTarget = 27f,
            humidityTarget = 58f,
            durationSimMinutes = 60,
        ),
    )

    fun indexOf(stage: ScenarioStage): Int = stages.indexOfFirst { it.stage == stage }
}
