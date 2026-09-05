package com.krishinirnay.core.data.model

import java.time.Instant

data class SyncStatus(
    val isOnline: Boolean,
    val lastSyncedAt: Instant?,
    val source: AppMode,
)
