package com.krishinirnay.feature.offline

import java.time.Instant

data class OfflineModeUiState(
    val isOnline: Boolean = true,
    val lastSyncedAt: Instant? = null,
)
