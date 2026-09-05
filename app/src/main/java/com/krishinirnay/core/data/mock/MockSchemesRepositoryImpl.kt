package com.krishinirnay.core.data.mock

import com.krishinirnay.core.data.model.GovtScheme
import com.krishinirnay.core.data.repository.SchemesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Static demo scheme list — no real government schemes API is wired up in Phase 1. */
@Singleton
class MockSchemesRepositoryImpl @Inject constructor() : SchemesRepository {

    private val _schemes = MutableStateFlow(
        listOf(
            GovtScheme(
                id = "pm-kisan",
                name = "PM-KISAN Samman Nidhi",
                benefit = "₹6000 / year",
                description = "Income support of ₹6000 per year, paid in three installments, to all landholding farmer families.",
                eligibility = "All landholding farmer families with cultivable land, registered with the local revenue department. Apply via the PM-KISAN portal or your nearest Common Service Centre.",
            ),
            GovtScheme(
                id = "krishi-yantra",
                name = "Krishi Yantra Anudan Yojana",
                benefit = "Up to 40% subsidy",
                description = "Subsidy on the purchase of farm machinery and equipment for eligible farmers.",
                eligibility = "Farmers who own cultivable land and haven't received a subsidy on the same equipment category in the last 5 years. Apply through your state agriculture department's portal.",
            ),
            GovtScheme(
                id = "mridha-swasthya",
                name = "Mridha Swasthya Card Yojana",
                benefit = "Free soil testing",
                description = "Free soil health testing and a personalised nutrient recommendation card for your fields.",
                eligibility = "Open to all farmers, no land-size minimum. Request a soil sample collection through your local Krishi Vigyan Kendra or agriculture extension office.",
            ),
        ),
    )
    override val schemes: StateFlow<List<GovtScheme>> = _schemes.asStateFlow()
}
