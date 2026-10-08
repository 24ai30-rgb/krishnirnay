package com.krishinirnay.mock

import com.krishinirnay.core.common.DispatcherProvider
import com.krishinirnay.core.decision.DecisionRules
import com.krishinirnay.core.mock.NarrativeEngine
import com.krishinirnay.core.mock.ScenarioStage
import com.krishinirnay.core.mock.SensorScenario
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeEngineTest {

    // tick() is synchronous and called directly in these tests — the
    // dispatchers are only exercised by start()/stop(), not under test
    // here — so any DispatcherProvider implementation works.
    private val fakeDispatcherProvider = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
    }

    @Test
    fun `seeded run stays within sensor bounds over many ticks`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 42L)
        var now = Instant.now()
        repeat(500) {
            now = now.plusSeconds(3)
            engine.tick(now)
            val reading = engine.sensorReading.value
            assertTrue(reading.soilMoisturePct in 5f..95f)
            assertTrue(reading.temperatureC in 15f..42f)
            assertTrue(reading.humidityPct in 20f..90f)
        }
    }

    @Test
    fun `same seed produces the same sequence`() {
        // Kept well inside a single stage's duration (30 sim-minutes vs
        // HEALTHY's 90) so this only exercises the seeded random walk,
        // not stage-transition timing (which depends on each engine's
        // own construction-time clock and shouldn't be compared
        // cross-instance).
        val engineA = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 7L)
        val engineB = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 7L)
        var now = Instant.now()
        repeat(10) {
            now = now.plusSeconds(3)
            engineA.tick(now)
            engineB.tick(now)
        }
        assertEquals(engineA.sensorReading.value, engineB.sensorReading.value)
    }

    @Test
    fun `stage transitions fire once a stage's simulated duration elapses`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 5L)
        val start = Instant.now()
        engine.tick(start)
        assertEquals(ScenarioStage.HEALTHY, engine.currentStage)

        // HEALTHY lasts 90 sim-minutes; at the default 60x acceleration
        // that's 90 real seconds. 95 gives a comfortable margin.
        engine.tick(start.plusSeconds(95))
        assertEquals(ScenarioStage.DRYING, engine.currentStage)
    }

    @Test
    fun `triggerIrrigation jumps straight to the IRRIGATED stage`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 3L)
        engine.triggerIrrigation()
        assertEquals(ScenarioStage.IRRIGATED, engine.currentStage)
    }

    @Test
    fun `triggerDeviceDisconnect and triggerDeviceReconnect toggle deviceOnline`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 9L)
        assertTrue(engine.deviceOnline.value)

        engine.triggerDeviceDisconnect()
        assertFalse(engine.deviceOnline.value)

        engine.triggerDeviceReconnect()
        assertTrue(engine.deviceOnline.value)
    }

    // --- Sensor scenario simulation (testing without physical ESP32 hardware) ---

    @Test
    fun `DRY_SOIL scenario applies immediately and crosses the real HIGH water-stress threshold`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 1L)
        engine.applyScenario(SensorScenario.DRY_SOIL)
        assertTrue(engine.sensorReading.value.soilMoisturePct < DecisionRules.SOIL_MOISTURE_HIGH_RISK_BELOW_PCT)
    }

    @Test
    fun `WET_SOIL scenario applies immediately and stays well above the HIGH water-stress threshold`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 1L)
        engine.applyScenario(SensorScenario.WET_SOIL)
        assertTrue(engine.sensorReading.value.soilMoisturePct > DecisionRules.SOIL_MOISTURE_MEDIUM_RISK_BELOW_PCT)
    }

    @Test
    fun `HIGH_TEMPERATURE scenario crosses the real HIGH heat-risk threshold`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 1L)
        engine.applyScenario(SensorScenario.HIGH_TEMPERATURE)
        assertTrue(engine.sensorReading.value.temperatureC >= DecisionRules.TEMPERATURE_HIGH_RISK_AT_OR_ABOVE_C)
    }

    @Test
    fun `LOW_TEMPERATURE scenario stays well below any heat-risk threshold`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 1L)
        engine.applyScenario(SensorScenario.LOW_TEMPERATURE)
        assertTrue(engine.sensorReading.value.temperatureC < DecisionRules.TEMPERATURE_MEDIUM_RISK_AT_OR_ABOVE_C)
    }

    @Test
    fun `HIGH_HUMIDITY scenario crosses the real disease-escalation humidity threshold`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 1L)
        engine.applyScenario(SensorScenario.HIGH_HUMIDITY)
        assertTrue(engine.sensorReading.value.humidityPct >= DecisionRules.HUMIDITY_DISEASE_ESCALATION_AT_OR_ABOVE_PCT)
    }

    @Test
    fun `applying a scenario is an immediate jump, never a gradual drift`() {
        val engine = NarrativeEngine(dispatcherProvider = fakeDispatcherProvider, seed = 1L)
        engine.tick(Instant.now()) // establish a baseline reading via the normal walk
        engine.applyScenario(SensorScenario.DRY_SOIL)
        // No further tick() was called — the jump must already be visible.
        assertTrue(engine.sensorReading.value.soilMoisturePct < DecisionRules.SOIL_MOISTURE_HIGH_RISK_BELOW_PCT)
    }
}
