package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The single source of truth every screen's ViewModel reads from. No
 * screen ever queries Firebase or the mock engine directly — this is
 * what guarantees Dashboard risk == Alerts risk for the same field
 * state. Both [com.krishinirnay.core.data.mock.MockFieldStateRepositoryImpl]
 * and the future Firebase-backed implementation satisfy this same
 * interface. Alerts are intentionally not exposed here — see
 * [AlertsRepository], which derives them from this repository's
 * [fieldState] rather than tracking them independently.
 */
interface FieldStateRepository {
    val fieldState: StateFlow<FieldState>
    val syncStatus: StateFlow<SyncStatus>

    suspend fun refresh()

    /**
     * Merges a fresh Crop Health scan result into the shared
     * [FieldState] and immediately re-runs the Decision Engine — this is
     * how [CropHealthRepository]'s network-only result becomes visible
     * on Dashboard/Alerts/AI Insights without waiting for the next
     * sensor tick.
     */
    suspend fun recordDiseaseResult(result: DiseaseResult)

    /** Same contract as [recordDiseaseResult], for a real Pest Detection scan result. */
    suspend fun recordPestResult(result: PestResult)
}
