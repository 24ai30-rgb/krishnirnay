package com.krishinirnay.core.data.model

/**
 * What the UI should honestly tell the farmer about the reading on
 * screen — derived from [SyncStatus] for sensors, or computed directly
 * by Weather/Market repositories for their own state, never stored
 * independently so it can't drift out of sync with the repository that
 * produced it.
 *
 * - [LIVE]: Mock Mode's narrative engine, or a Live Mode poll that
 *   just succeeded.
 * - [CACHED]: Live Mode, but the last poll failed (server/ESP32/
 *   provider unreachable) — the values on screen are the last
 *   known-good reading, not a fresh one.
 * - [MOCK]: Mock Mode is never "live" in the freshness sense even
 *   though its data updates continuously — it's synthetic.
 * - [LOADING]: a first real request is in flight and there is nothing
 *   cached to show yet. Distinct from [UNAVAILABLE] on purpose — without
 *   it, every cold start rendered "not available" for the second or two
 *   before the first response landed, which reads to a farmer exactly
 *   like a permanent failure.
 * - [NO_DATA]: the provider was reached and answered honestly that it has
 *   no record for this crop/location today. Nothing is broken and there is
 *   nothing to retry — distinct from [UNAVAILABLE], which means we could
 *   not get an answer at all.
 * - [UNAVAILABLE]: the request failed (server or provider unreachable) and
 *   there is no cached value either — never render fake numbers for this.
 */
enum class DataSourceStatus {
    LIVE,
    CACHED,
    MOCK,
    LOADING,
    NO_DATA,
    UNAVAILABLE,
}

fun SyncStatus.toDataSourceStatus(): DataSourceStatus = when {
    source == AppMode.MOCK -> DataSourceStatus.MOCK
    isOnline -> DataSourceStatus.LIVE
    else -> DataSourceStatus.CACHED
}
