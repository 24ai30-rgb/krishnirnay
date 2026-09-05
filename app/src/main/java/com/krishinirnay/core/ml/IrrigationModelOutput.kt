package com.krishinirnay.core.ml

import com.krishinirnay.core.data.model.RiskLevel

/**
 * Output of Model 1 (on-device irrigation/water-stress, ONNX Runtime
 * Mobile). The runner itself (`OnnxModelRunner`, `IrrigationRiskModel`)
 * lands later — this is just the data contract `DecisionEngine` consumes,
 * so the engine can be built and tested against it now without depending
 * on ONNX Runtime.
 */
data class IrrigationModelOutput(
    val riskLevel: RiskLevel,
    val probability: Float,
)
