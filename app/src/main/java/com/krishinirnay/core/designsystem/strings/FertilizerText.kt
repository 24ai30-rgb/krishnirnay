package com.krishinirnay.core.designsystem.strings

import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import com.krishinirnay.core.fertilizer.FertilizerType
import com.krishinirnay.core.fertilizer.NutrientDeficiency

private fun AppStrings.nameFor(nutrient: NutrientDeficiency): String = when (nutrient) {
    NutrientDeficiency.NITROGEN -> fertilizerNutrientNitrogenName
    NutrientDeficiency.PHOSPHORUS -> fertilizerNutrientPhosphorusName
    NutrientDeficiency.POTASSIUM -> fertilizerNutrientPotassiumName
}

private fun AppStrings.nameFor(type: FertilizerType): String = when (type) {
    FertilizerType.UREA -> fertilizerTypeUreaName
    FertilizerType.DAP -> fertilizerTypeDapName
    FertilizerType.MOP -> fertilizerTypeMopName
}

/** The main recommendation line — one sentence, farmer-facing. Callers append [fertilizerSafetyNote] separately so it always renders as its own, unmissable line. */
fun AppStrings.textFor(recommendation: FertilizerRecommendation): String = when (recommendation) {
    is FertilizerRecommendation.Recommended -> String.format(
        fertilizerRecommendedTemplate,
        nameFor(recommendation.nutrient),
        nameFor(recommendation.fertilizerType),
        "${recommendation.quantityRangeKgPerAcre.first}–${recommendation.quantityRangeKgPerAcre.last} kg/acre",
        textFor(recommendation.timing),
    )
    FertilizerRecommendation.NoActionNeeded -> fertilizerNoActionNeeded
    is FertilizerRecommendation.InsufficientData -> fertilizerInsufficientData
}
