package com.krishinirnay.core.mock

import com.krishinirnay.core.common.DispatcherProvider
import com.krishinirnay.core.data.model.SensorReading
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Generates coherent, evolving Mock Mode sensor data — a narrative, not
 * noise. [MockClock] drives how fast the [ScenarioScript] stages
 * progress; [SensorReading.timestamp] itself always uses real wall-clock
 * time so "last synced" displays stay meaningful during a live demo
 * (values evolve fast, timestamps don't jump into the future).
 *
 * Not a `@Singleton @Inject` class deliberately — its tunables (seed,
 * tick interval, acceleration) need to vary between the real app (random
 * seed, default speed) and tests (fixed seed, deterministic [tick]
 * calls), which Dagger/Hilt's generated factories can't express via
 * Kotlin default parameters. See `MockModule` for how it's provided to
 * the app.
 */
class NarrativeEngine(
    private val dispatcherProvider: DispatcherProvider,
    seed: Long = System.currentTimeMillis(),
    private val tickIntervalMillis: Long = 2_500L,
    accelerationFactor: Long = MockClock.DEFAULT_ACCELERATION_FACTOR,
) {
    private val random = Random(seed)
    private val clock = MockClock(accelerationFactor = accelerationFactor)

    private val moistureWalk = SensorWalk(min = 5f, max = 95f, stepSize = 1.5f, initialValue = 60f, random = random)
    private val temperatureWalk = SensorWalk(min = 15f, max = 42f, stepSize = 0.8f, initialValue = 26f, random = random)
    private val humidityWalk = SensorWalk(min = 20f, max = 90f, stepSize = 1.2f, initialValue = 60f, random = random)

    private var currentStageIndex = 0
    private var stageStartSimTime: Instant = clock.simulatedNow()

    private var loopJob: Job? = null

    private val _sensorReading = MutableStateFlow(buildReading(Instant.now()))
    val sensorReading: StateFlow<SensorReading> = _sensorReading.asStateFlow()

    private val _deviceOnline = MutableStateFlow(true)
    val deviceOnline: StateFlow<Boolean> = _deviceOnline.asStateFlow()

    val currentStage: ScenarioStage get() = ScenarioScript.stages[currentStageIndex].stage

    /** Starts the app-scoped coroutine loop. Safe to call more than once — a no-op if already running. */
    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(dispatcherProvider.default) {
            while (isActive) {
                tick()
                delay(tickIntervalMillis)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * Advances the simulation by one step. Public and driven off an
     * explicit [realNow] (default: actual now) so tests can call it
     * repeatedly with synthetic timestamps instead of depending on the
     * coroutine loop or real elapsed time.
     */
    fun tick(realNow: Instant = Instant.now()) {
        val stage = ScenarioScript.stages[currentStageIndex]
        moistureWalk.step(stage.moistureTarget)
        temperatureWalk.step(stage.temperatureTarget)
        humidityWalk.step(stage.humidityTarget)

        val simNow = clock.simulatedNow(realNow)
        if (Duration.between(stageStartSimTime, simNow).toMinutes() >= stage.durationSimMinutes) {
            currentStageIndex = (currentStageIndex + 1) % ScenarioScript.stages.size
            stageStartSimTime = simNow
        }

        _sensorReading.value = buildReading(realNow)
    }

    /**
     * Presenter-triggerable override — jumps straight to the recovery
     * beat on cue during a scripted demo, and gives unsupervised judges
     * an immediate visible reaction to a tap instead of a dead end.
     */
    fun triggerIrrigation() {
        currentStageIndex = ScenarioScript.indexOf(ScenarioStage.IRRIGATED)
        stageStartSimTime = clock.simulatedNow()
    }

    fun triggerDeviceDisconnect() {
        _deviceOnline.value = false
    }

    fun triggerDeviceReconnect() {
        _deviceOnline.value = true
    }

    /**
     * Developer/tester-triggerable scenario — jumps all three sensor values
     * immediately (see [SensorWalk.jumpTo]) rather than gradually drifting
     * toward them, so the app can be demonstrated and tested end to end
     * without physical ESP32 hardware. Resets the stage timer so the normal
     * narrative doesn't immediately drift the reading away again.
     */
    fun applyScenario(scenario: SensorScenario, realNow: Instant = Instant.now()) {
        val target = scenario.toTarget()
        moistureWalk.jumpTo(target.moisturePct)
        temperatureWalk.jumpTo(target.temperatureC)
        humidityWalk.jumpTo(target.humidityPct)
        stageStartSimTime = clock.simulatedNow(realNow)
        _sensorReading.value = buildReading(realNow)
    }

    private fun buildReading(realNow: Instant) = SensorReading(
        soilMoisturePct = moistureWalk.value,
        temperatureC = temperatureWalk.value,
        humidityPct = humidityWalk.value,
        timestamp = realNow,
    )
}
