package com.krishinirnay.core.ml

import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain wrapper around [OnnxModelRunner] for Model 1 (on-device
 * irrigation/water-stress).
 *
 * **Placeholder contract — TODO confirm before relying on this for the
 * live demo.** No real feature order/scaling or output shape was
 * provided by whoever trained the model. Assumed here: input
 * `[soilMoisturePct, temperatureC, humidityPct, delayHours]`, output
 * class probabilities for `[LOW, MEDIUM, HIGH]` in that order. This
 * assumption is isolated to [predict]/[parseOutput] so correcting it
 * later only touches this file.
 */
@Singleton
class IrrigationRiskModel @Inject constructor(
    private val onnxModelRunner: OnnxModelRunner,
) {
    suspend fun predict(sensors: SensorReading, delayHours: Float = 0f): IrrigationModelOutput? {
        val input = floatArrayOf(sensors.soilMoisturePct, sensors.temperatureC, sensors.humidityPct, delayHours)
        val output = onnxModelRunner.run(input) ?: return null
        return parseOutput(output)
    }

    private fun parseOutput(output: FloatArray): IrrigationModelOutput? {
        if (output.size < 3) return null
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: return null
        val riskLevel = when (maxIndex) {
            0 -> RiskLevel.LOW
            1 -> RiskLevel.MEDIUM
            2 -> RiskLevel.HIGH
            else -> return null
        }
        return IrrigationModelOutput(riskLevel = riskLevel, probability = output[maxIndex])
    }
}
