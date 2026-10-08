package com.krishinirnay.data

import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.model.toDataSourceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DataSourceStatusTest {

    @Test
    fun `mock source is always MOCK regardless of the isOnline flag`() {
        assertEquals(DataSourceStatus.MOCK, SyncStatus(isOnline = true, lastSyncedAt = null, source = AppMode.MOCK).toDataSourceStatus())
        assertEquals(DataSourceStatus.MOCK, SyncStatus(isOnline = false, lastSyncedAt = null, source = AppMode.MOCK).toDataSourceStatus())
    }

    @Test
    fun `live source that is online is LIVE`() {
        assertEquals(DataSourceStatus.LIVE, SyncStatus(isOnline = true, lastSyncedAt = null, source = AppMode.LIVE).toDataSourceStatus())
    }

    @Test
    fun `live source that is offline is CACHED not silently LIVE`() {
        assertEquals(DataSourceStatus.CACHED, SyncStatus(isOnline = false, lastSyncedAt = null, source = AppMode.LIVE).toDataSourceStatus())
    }
}
