package com.krishinirnay.core.data.model

import java.time.Instant

data class DeviceStatus(
    val isOnline: Boolean,
    val lastSeenAt: Instant,
    val batteryPct: Int? = null,
)
