package com.krishinirnay.core.decision

import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.ml.IrrigationModelOutput

/**
 * [modelOutput] is nullable so the engine works even if Model 1 failed to
 * load — it falls back to a pure soil-moisture threshold rule.
 * [diseaseResult] is nullable until the farmer has run a Crop Health scan
 * this session; when absent, `cropHealthRisk` in the output is UNKNOWN,
 * never LOW. [pestResult] follows the same contract for Pest Detection.
 * [rainOutlook] and [cropStage] are optional context from Weather/the
 * farmer's profile — both default to values that reproduce Phase 1's
 * exact behavior when absent, so every existing call site keeps working
 * unchanged.
 */
data class DecisionInput(
    val sensors: SensorReading,
    val modelOutput: IrrigationModelOutput?,
    val diseaseResult: DiseaseResult?,
    val deviceOnline: Boolean,
    val pestResult: PestResult? = null,
    val rainOutlook: RainOutlook = RainOutlook.UNKNOWN,
    val cropStage: String? = null,
)
