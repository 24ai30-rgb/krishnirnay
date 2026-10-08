package com.krishinirnay.core.data.local

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.model.FeedbackEntry
import com.krishinirnay.core.data.repository.FeedbackRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real, persisted FeedbackRepository — same restore-on-init pattern as
 * FarmerProfileRepositoryImpl, but [record] explicitly awaits the initial
 * load ([initialLoad]) before reading/writing [_entries] — without that,
 * a record() called immediately after construction could race the async
 * initial load and have it silently overwrite the just-recorded entry with
 * whatever was previously on disk.
 */
@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val store: FeedbackStore,
) : FeedbackRepository {

    private val _entries = MutableStateFlow<List<FeedbackEntry>>(emptyList())
    override val entries: StateFlow<List<FeedbackEntry>> = _entries.asStateFlow()

    private val initialLoad = scope.async {
        val loaded = store.load()
        _entries.value = loaded
    }

    override suspend fun record(entry: FeedbackEntry) {
        initialLoad.await()
        val updated = _entries.value + entry
        _entries.value = updated
        store.save(updated)
    }
}
