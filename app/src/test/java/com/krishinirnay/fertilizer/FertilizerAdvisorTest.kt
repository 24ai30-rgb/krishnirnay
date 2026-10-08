package com.krishinirnay.fertilizer

import com.krishinirnay.core.data.model.IrrigationMethod
import com.krishinirnay.core.decision.RainOutlook
import com.krishinirnay.core.fertilizer.FertilizerAdvisor
import com.krishinirnay.core.fertilizer.FertilizerInput
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import com.krishinirnay.core.fertilizer.NutrientDeficiency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FertilizerAdvisorTest {

    private fun input(
        nitrogenPpm: Float? = 60f,
        phosphorusPpm: Float? = 20f,
        potassiumPpm: Float? = 150f,
    ) = FertilizerInput(
        crop = "Cotton",
        cropVariety = null,
        soilType = "Black Soil",
        cropStage = "Flowering",
        farmAreaAcres = 2f,
        soilMoisturePct = 45f,
        nitrogenPpm = nitrogenPpm,
        phosphorusPpm = phosphorusPpm,
        potassiumPpm = potassiumPpm,
        irrigationMethod = IrrigationMethod.DRIP,
        rainOutlook = RainOutlook.UNKNOWN,
    )

    @Test
    fun `returns InsufficientData when no NPK reading is available at all`() {
        val result = FertilizerAdvisor.recommend(input(nitrogenPpm = null, phosphorusPpm = null, potassiumPpm = null))
        assertTrue(result is FertilizerRecommendation.InsufficientData)
        assertEquals(3, (result as FertilizerRecommendation.InsufficientData).missingFields.size)
    }

    @Test
    fun `flags nitrogen deficiency below the generic threshold`() {
        val result = FertilizerAdvisor.recommend(input(nitrogenPpm = 10f))
        assertTrue(result is FertilizerRecommendation.Recommended)
        assertEquals(NutrientDeficiency.NITROGEN, (result as FertilizerRecommendation.Recommended).nutrient)
    }

    @Test
    fun `checks nitrogen before phosphorus before potassium`() {
        // All three low — nitrogen must win, a deterministic, documented priority order.
        val result = FertilizerAdvisor.recommend(input(nitrogenPpm = 10f, phosphorusPpm = 2f, potassiumPpm = 20f))
        assertEquals(NutrientDeficiency.NITROGEN, (result as FertilizerRecommendation.Recommended).nutrient)
    }

    @Test
    fun `flags phosphorus deficiency when only phosphorus is low`() {
        val result = FertilizerAdvisor.recommend(input(nitrogenPpm = 60f, phosphorusPpm = 2f, potassiumPpm = 150f))
        assertEquals(NutrientDeficiency.PHOSPHORUS, (result as FertilizerRecommendation.Recommended).nutrient)
    }

    @Test
    fun `returns NoActionNeeded when every available reading is sufficient`() {
        val result = FertilizerAdvisor.recommend(input())
        assertEquals(FertilizerRecommendation.NoActionNeeded, result)
    }

    @Test
    fun `partial data still allows a real recommendation rather than InsufficientData`() {
        // Only nitrogen known, and it's low — one real reading is enough to act on.
        val result = FertilizerAdvisor.recommend(input(nitrogenPpm = 10f, phosphorusPpm = null, potassiumPpm = null))
        assertTrue(result is FertilizerRecommendation.Recommended)
    }
}
