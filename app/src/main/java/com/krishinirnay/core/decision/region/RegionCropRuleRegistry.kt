package com.krishinirnay.core.decision.region

/**
 * The one place that maps (region, crop) -> [RegionCropRuleSet]. Adding
 * "Maharashtra + Soybean" or "Punjab + Wheat" later is one new entry here plus one
 * new [RegionCropRuleSet] implementation — never a change to
 * [com.krishinirnay.core.decision.DecisionEngine].
 */
object RegionCropRuleRegistry {

    private val rules: Map<Pair<String, String>, RegionCropRuleSet> = listOf(
        VidarbhaCottonRules,
    ).associateBy { it.regionLabel.normalize() to it.cropLabel.normalize() }

    fun forRegionAndCrop(region: String?, crop: String?): RegionCropRuleSet {
        val key = region?.normalize().orEmpty() to crop?.normalize().orEmpty()
        return rules[key] ?: DefaultRuleSet
    }

    private fun String.normalize(): String = trim().lowercase()
}
