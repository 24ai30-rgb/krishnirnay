package com.krishinirnay.feature.whatif

import com.krishinirnay.core.data.model.RiskLevel

data class WhatIfUiState(
    val delayHours: Float = 0f,
    val predictedRisk: RiskLevel = RiskLevel.UNKNOWN,
    val isCalculating: Boolean = false,
)
