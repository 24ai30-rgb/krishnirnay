package com.krishinirnay.core.data.model

/** Mock-only demo profile for Phase 1. */
data class FarmerProfile(
    val name: String,
    val phone: String,
    val location: String,
    val farmSizeAcres: Float,
    val crops: List<String>,

    // Inputs required by the Agricultural Risk ML model.
    val soilType: String = "Black Soil",
    val seedlingStage: String = "Germination",
)