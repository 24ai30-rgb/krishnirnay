package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.FeedbackEntry
import kotlinx.coroutines.flow.StateFlow

interface FeedbackRepository {
    val entries: StateFlow<List<FeedbackEntry>>

    suspend fun record(entry: FeedbackEntry)
}
