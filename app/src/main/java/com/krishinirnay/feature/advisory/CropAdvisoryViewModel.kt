package com.krishinirnay.feature.advisory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.appStringsFor
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.core.fertilizer.FertilizerRecommendation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * No dedicated repository — derives its recommendations straight from the same
 * [FieldStateRepository.fieldState] Dashboard/Alerts read, so the advice here always agrees
 * with the risk shown elsewhere. Purely a live projection of current sensor/decision state —
 * there's no user-owned completion state to track (these aren't tasks a farmer "finishes,"
 * they're guidance that changes on its own as conditions change).
 *
 * Phase 4B: the fertilizer task now reads [FieldState.decision]'s
 * [com.krishinirnay.core.data.model.DecisionOutput.fertilizerRecommendation] — computed
 * once, by [com.krishinirnay.core.data.composite.FieldDecisionResolver], not
 * recomputed here. This ViewModel no longer needs `ProfileRepository` at all.
 */
@HiltViewModel
class CropAdvisoryViewModel @Inject constructor(
    fieldStateRepository: FieldStateRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<CropAdvisoryUiState> = combine(
        fieldStateRepository.fieldState,
        settingsRepository.language,
    ) { fieldState, language -> fieldState.toAdvisoryUiState(appStringsFor(language)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CropAdvisoryUiState())
}

private fun FieldState.toAdvisoryUiState(strings: AppStrings): CropAdvisoryUiState {
    // Absent only if this FieldState predates Phase 4B (e.g. a value restored from an
    // older cache entry) — never invented, honestly falls back to "need more data."
    val fertilizerRecommendation = decision.fertilizerRecommendation
        ?: FertilizerRecommendation.InsufficientData(listOf("nitrogen", "phosphorus", "potassium"))

    val tasks = listOf(
        AdvisoryTask(
            type = AdvisoryTaskType.IRRIGATE,
            detail = if (decision.waterStressRisk != RiskLevel.LOW) {
                String.format(strings.advisoryIrrigateHighTemplate, sensors.soilMoisturePct.toInt())
            } else {
                String.format(strings.advisoryIrrigateLowTemplate, sensors.soilMoisturePct.toInt())
            },
        ),
        AdvisoryTask(
            type = AdvisoryTaskType.PEST_CONTROL,
            detail = if (decision.cropHealthRisk != RiskLevel.LOW || decision.pestRisk != RiskLevel.LOW) {
                diseaseResult?.displayName?.let { String.format(strings.advisoryPestWithDiseaseTemplate, it) }
                    ?: strings.advisoryPestElevated
            } else {
                strings.advisoryPestNone
            },
        ),
        AdvisoryTask(
            type = AdvisoryTaskType.FERTILIZER,
            detail = strings.textFor(fertilizerRecommendation),
        ),
    )
    return CropAdvisoryUiState(
        overallRisk = decision.overallRisk,
        confidencePct = (decision.confidence * 100).toInt(),
        aiAdvice = decision.recommendation,
        isOnline = deviceStatus.isOnline,
        tasks = tasks,
    )
}
