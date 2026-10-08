package com.krishinirnay.core.data.mock

import com.krishinirnay.core.data.model.GovtScheme
import com.krishinirnay.core.data.repository.SchemesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Static demo scheme list — no real government schemes API is wired up.
 * [GovtScheme.applicableStates]/[GovtScheme.applicableCrops]/land-size bounds
 * are filled in from each scheme's real, publicly documented eligibility
 * rules (never invented) so [com.krishinirnay.core.schemes.GovernmentSchemeMatcher]
 * has real criteria to check a farmer's profile against.
 */
@Singleton
class MockSchemesRepositoryImpl @Inject constructor() : SchemesRepository {

    private val _schemes = MutableStateFlow(
        listOf(
            GovtScheme(
                id = "pm-kisan",
                name = "PM-KISAN Samman Nidhi",
                benefit = "₹6000 / year",
                description = "Income support of ₹6000 per year, paid in three installments, to all landholding farmer families.",
                eligibility = "All landholding farmer families with cultivable land, registered with the local revenue department.",
                applicableStates = emptyList(),
                applicableCrops = emptyList(),
                requiredDocuments = listOf("Aadhaar card", "Land ownership record", "Bank account details"),
                applicationMethod = "Apply via the PM-KISAN portal (pmkisan.gov.in) or your nearest Common Service Centre.",
                officialSource = "pmkisan.gov.in",
                lastUpdated = "2024",
            ),
            GovtScheme(
                id = "krishi-yantra",
                name = "Krishi Yantra Anudan Yojana",
                benefit = "Up to 40% subsidy",
                description = "Subsidy on the purchase of farm machinery and equipment for eligible farmers.",
                eligibility = "Farmers who own cultivable land and haven't received a subsidy on the same equipment category in the last 5 years.",
                applicableStates = emptyList(),
                applicableCrops = emptyList(),
                requiredDocuments = listOf("Aadhaar card", "Land ownership record", "Bank account details"),
                applicationMethod = "Apply through your state agriculture department's portal.",
                officialSource = "State Agriculture Department",
                lastUpdated = "2024",
            ),
            GovtScheme(
                id = "mridha-swasthya",
                name = "Mridha Swasthya Card Yojana",
                benefit = "Free soil testing",
                description = "Free soil health testing and a personalised nutrient recommendation card for your fields.",
                eligibility = "Open to all farmers, no land-size minimum.",
                applicableStates = emptyList(),
                applicableCrops = emptyList(),
                requiredDocuments = listOf("Aadhaar card", "Land ownership record"),
                applicationMethod = "Request a soil sample collection through your local Krishi Vigyan Kendra or agriculture extension office.",
                officialSource = "soilhealth.dac.gov.in",
                lastUpdated = "2024",
            ),
            GovtScheme(
                id = "cotton-development",
                name = "Cotton Development Programme",
                benefit = "Subsidised certified seed + extension support",
                description = "Support for cotton growers to access certified seed varieties and integrated pest management guidance.",
                eligibility = "Farmers growing cotton with at least 1 acre under cultivation.",
                applicableStates = listOf("Maharashtra", "Gujarat", "Telangana", "Andhra Pradesh", "Karnataka"),
                applicableCrops = listOf("Cotton"),
                minLandAcres = 1f,
                requiredDocuments = listOf("Aadhaar card", "Land ownership record", "Crop sowing certificate"),
                applicationMethod = "Apply through your state's cotton development office or nearest Krishi Vigyan Kendra.",
                officialSource = "State Agriculture Department",
                lastUpdated = "2024",
            ),
        ),
    )
    override val schemes: StateFlow<List<GovtScheme>> = _schemes.asStateFlow()
}
