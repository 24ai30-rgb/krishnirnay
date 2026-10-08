package com.krishinirnay.core.data.model

/** How the farmer waters the field — one field covers both "irrigation type" and "farming method" in the Farm Setup flow; they're the same concept asked twice in the product brief. */
enum class IrrigationMethod {
    RAIN_FED,
    IRRIGATED,
    DRIP,
    SPRINKLER,
    OTHER,
}
