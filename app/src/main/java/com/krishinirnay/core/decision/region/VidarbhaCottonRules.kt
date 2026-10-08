package com.krishinirnay.core.decision.region

/**
 * Vidarbha + Cotton — the pilot region/crop pairing. Moisture/heat thresholds are
 * left at [DefaultRuleSet]'s generic values (not overridden) because this project has
 * no verified, region-specific calibration data for them yet; overriding those with
 * invented numbers would be exactly the kind of unsafe, unverified agricultural claim
 * this system must avoid. What *is* safe to encode is well-established agronomy: cotton
 * is most vulnerable to pest damage (notably bollworm) during flowering and boll
 * formation, when an infestation costs the most yield.
 */
object VidarbhaCottonRules : RegionCropRuleSet {
    override val regionLabel: String = "Vidarbha"
    override val cropLabel: String = "Cotton"

    override val pestVulnerableCropStages: Set<String> = setOf(
        "Flowering",
        "Boll Formation",
        "Boll Development",
    )
}
