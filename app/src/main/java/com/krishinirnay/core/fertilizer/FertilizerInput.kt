package com.krishinirnay.core.fertilizer

import com.krishinirnay.core.data.model.IrrigationMethod
import com.krishinirnay.core.decision.RainOutlook

/**
 * Everything [FertilizerAdvisor] needs. NPK fields mirror
 * [com.krishinirnay.core.data.model.SensorReading]'s nullable ppm readings —
 * Phase 2 sensors, absent on most hardware today. When all three are null,
 * [FertilizerAdvisor] returns [FertilizerRecommendation.InsufficientData]
 * rather than guessing.
 */
data class FertilizerInput(
    val crop: String?,
    val cropVariety: String?,
    val soilType: String?,
    val cropStage: String?,
    val farmAreaAcres: Float?,
    val soilMoisturePct: Float?,
    val nitrogenPpm: Float?,
    val phosphorusPpm: Float?,
    val potassiumPpm: Float?,
    val irrigationMethod: IrrigationMethod?,
    val rainOutlook: RainOutlook = RainOutlook.UNKNOWN,
)
