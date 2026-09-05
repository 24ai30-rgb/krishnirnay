package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.ml.IrrigationModelOutput

/**
 * [modelOutput] is nullable so the engine works even if Model 1 failed to
 * load — it falls back to a pure soil-moisture threshold rule.
 * [diseaseResult] is nullable until the farmer has run a Crop Health scan
 * this session; when absent, `cropHealthRisk` in the output is UNKNOWN,
 * never LOW.
 */
data class DecisionInput(
    val sensors: SensorReading,
    val modelOutput: IrrigationModelOutput?,
    val diseaseResult: DiseaseResult?,
    val deviceOnline: Boolean,
)
