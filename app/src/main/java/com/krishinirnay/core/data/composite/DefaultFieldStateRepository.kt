package com.krishinirnay.core.data.composite

import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.di.LiveSource
import com.krishinirnay.core.data.di.MockSource
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MockControls
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.mock.SensorScenario
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Switches between [mock] and [live] at runtime, driven by
 * [SettingsRepository.appMode] — this is what lets Mock and Live Mode
 * swap instantly without restarting the DI graph or the Activity. Bound
 * to [FieldStateRepository] in [com.krishinirnay.core.data.di.RepositoryModule];
 * every screen injects this one and never [mock]/[live] directly.
 *
 * Also implements [MockControls] itself (delegating to [mock]) — every
 * screen injects this class as plain [FieldStateRepository], so
 * `fieldStateRepository as? MockControls` only ever finds anything if
 * this class implements it too; the raw [MockFieldStateRepositoryImpl]
 * behind [mock] is never directly reachable from the UI. Every operation
 * is a no-op while Live Mode is active — you can't simulate a scenario on
 * real sensor data.
 *
 * History and Alerts naturally avoid cross-mode contamination without
 * any explicit reset here: each [FieldStateRepository] implementation
 * tracks its own `history` independently (so switching sources via
 * [flatMapLatest] always yields the new source's own, already-empty-or-
 * grown-on-its-own-terms history), and
 * [com.krishinirnay.core.data.composite.AlertGenerator] treats a
 * `dataSource` change between consecutive [FieldState]s as a fresh
 * baseline rather than a real risk change.
 */
@Singleton
class DefaultFieldStateRepository @Inject constructor(
    @MockSource private val mock: FieldStateRepository,
    @LiveSource private val live: FieldStateRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : FieldStateRepository, MockControls {

    private val mockControls: MockControls? = mock as? MockControls

    override val fieldState: StateFlow<FieldState> = settingsRepository.appMode
        .flatMapLatest { mode -> activeRepository(mode).fieldState }
        .stateIn(scope, SharingStarted.Eagerly, mock.fieldState.value)

    override val syncStatus: StateFlow<SyncStatus> = settingsRepository.appMode
        .flatMapLatest { mode -> activeRepository(mode).syncStatus }
        .stateIn(scope, SharingStarted.Eagerly, mock.syncStatus.value)

    override suspend fun refresh() {
        activeRepository(settingsRepository.appMode.value).refresh()
    }

    override suspend fun recordDiseaseResult(result: DiseaseResult) {
        activeRepository(settingsRepository.appMode.value).recordDiseaseResult(result)
    }

    override suspend fun recordPestResult(result: PestResult) {
        activeRepository(settingsRepository.appMode.value).recordPestResult(result)
    }

    override fun triggerIrrigation() {
        if (isMockActive()) mockControls?.triggerIrrigation()
    }

    override fun triggerDeviceDisconnect() {
        if (isMockActive()) mockControls?.triggerDeviceDisconnect()
    }

    override fun triggerDeviceReconnect() {
        if (isMockActive()) mockControls?.triggerDeviceReconnect()
    }

    override fun applySensorScenario(scenario: SensorScenario) {
        if (isMockActive()) mockControls?.applySensorScenario(scenario)
    }

    private fun isMockActive(): Boolean = settingsRepository.appMode.value == AppMode.MOCK

    private fun activeRepository(mode: AppMode): FieldStateRepository = if (mode == AppMode.MOCK) mock else live
}
