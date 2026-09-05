package com.krishinirnay.core.data.composite

import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.SyncStatus
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Switches between [mock] and [live] at runtime, driven by
 * [SettingsRepository.appMode] — this is what lets Mock and Live Mode
 * swap instantly without restarting the DI graph or the Activity.
 *
 * History and Alerts naturally avoid cross-mode contamination without
 * any explicit reset here: each [FieldStateRepository] implementation
 * tracks its own `history` independently (so switching sources via
 * [flatMapLatest] always yields the new source's own, already-empty-or-
 * grown-on-its-own-terms history), and
 * [com.krishinirnay.core.data.composite.AlertGenerator] treats a
 * `dataSource` change between consecutive [FieldState]s as a fresh
 * baseline rather than a real risk change.
 *
 * Not yet wired into the Hilt graph — [RepositoryModule] binds
 * `FieldStateRepository` directly to `MockFieldStateRepositoryImpl`
 * until a real Firebase-backed implementation exists to plug in as
 * [live] (Live Mode wiring). This class is complete and unit-tested
 * against fakes now so the switch-over is a wiring change only, not a
 * logic change, once that implementation lands.
 */
class DefaultFieldStateRepository(
    private val mock: FieldStateRepository,
    private val live: FieldStateRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope,
) : FieldStateRepository {

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

    private fun activeRepository(mode: AppMode): FieldStateRepository = if (mode == AppMode.MOCK) mock else live
}
