package com.krishinirnay.core.data.model

/**
 * How closely the mandi actually shown matches the farmer's own saved farm
 * location. The server's hard rule (never another state) means a successful
 * [MarketState] is always at least [SAME_STATE] — this only distinguishes
 * "their own district reported today" from "a different district in their
 * state reported instead". Derived client-side from data already on
 * [MarketState] (its real [MarketState.district]) compared against the
 * farmer's saved district — no new field or backend change needed.
 */
enum class MarketMatchLevel {
    /** The farmer's own saved district reported a real price today. */
    SAME_DISTRICT,

    /** Their own district had nothing; another district in their own state did. */
    SAME_STATE,

    /** Not enough saved location to compare (e.g. no district saved yet). */
    UNKNOWN,
}

/**
 * [farmerDistrict] is the farmer's own saved [com.krishinirnay.core.data.model.FarmLocation.district],
 * not anything from the response — comparing against the response's own
 * requested-district field would be circular.
 */
fun MarketState.matchLevel(farmerDistrict: String): MarketMatchLevel {
    val actual = district?.trim().orEmpty()
    val saved = farmerDistrict.trim()
    if (actual.isBlank() || saved.isBlank()) return MarketMatchLevel.UNKNOWN
    return if (actual.equals(saved, ignoreCase = true)) {
        MarketMatchLevel.SAME_DISTRICT
    } else {
        MarketMatchLevel.SAME_STATE
    }
}
