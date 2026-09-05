package com.krishinirnay.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.llm.ExplanationResult
import com.krishinirnay.core.llm.ExplanationService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiInsightsViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    private val explanationService: ExplanationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var polishedForRisk: RiskLevel? = null

    init {
        viewModelScope.launch {
            fieldStateRepository.fieldState.collect { state ->
                val stillFreshPolish = _uiState.value.isPolished && polishedForRisk == state.decision.overallRisk
                _uiState.update {
                    it.copy(
                        overallRisk = state.decision.overallRisk,
                        confidencePct = (state.decision.confidence * 100).roundToInt(),
                        reasons = state.decision.reasons,
                        recommendation = state.decision.recommendation,
                        polishedText = if (stillFreshPolish) it.polishedText else null,
                        isPolished = stillFreshPolish,
                    )
                }
            }
        }

        // Only re-request an AI-polished explanation when the risk level
        // itself actually changes — not on every sensor fluctuation —
        // to avoid spamming the server on every Mock Mode tick.
        viewModelScope.launch {
            fieldStateRepository.fieldState
                .distinctUntilChangedBy { it.decision.overallRisk }
                .collect { state ->
                    polishedForRisk = state.decision.overallRisk
                    val result = explanationService.polish(state)
                    if (result is ExplanationResult.Polished && polishedForRisk == state.decision.overallRisk) {
                        _uiState.update { it.copy(polishedText = result.text, isPolished = true) }
                    }
                }
        }
    }
}
