package com.krishinirnay.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.llm.ExplanationResult
import com.krishinirnay.core.llm.ExplanationService
import com.krishinirnay.core.llm.local.LocalLlmContextBuilder
import com.krishinirnay.core.llm.local.LocalLlmRepository
import com.krishinirnay.core.llm.local.LocalLlmResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Explanation polish is tried in order: (1) the Local LLM, grounded in the
 * same real profile/weather/market/decision data as the Chatbot (Phase 4E),
 * calling only this app's own server, never a cloud LLM; (2) cloud Gemini
 * (ExplanationService) only if SettingsRepository.cloudFallbackEnabled is
 * explicitly turned on (off by default) and the Local LLM didn't answer.
 * Either way, the plain rule-based recommendation renders immediately and
 * offline-safe — this only ever silently upgrades it, never blocks or shows
 * an error.
 */
@HiltViewModel
class AiInsightsViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    private val profileRepository: ProfileRepository,
    private val weatherRepository: WeatherRepository,
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
    private val explanationService: ExplanationService,
    private val localLlmRepository: LocalLlmRepository,
    private val localLlmContextBuilder: LocalLlmContextBuilder,
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

        // Only re-request a polished explanation when the risk level itself
        // actually changes — not on every sensor fluctuation — to avoid
        // spamming the server on every Mock Mode tick.
        viewModelScope.launch {
            fieldStateRepository.fieldState
                .distinctUntilChangedBy { it.decision.overallRisk }
                .collect { state ->
                    polishedForRisk = state.decision.overallRisk

                    val context = localLlmContextBuilder.build(
                        fieldState = state,
                        profile = profileRepository.profile.value,
                        weather = weatherRepository.weather.value,
                        market = marketRepository.market.value,
                        language = settingsRepository.language.value,
                    )
                    val localResult = localLlmRepository.ask(
                        "Explain today's field status and recommendation simply.",
                        context,
                    )

                    val polished = when {
                        localResult is LocalLlmResult.Answered -> localResult.reply
                        settingsRepository.cloudFallbackEnabled.value -> {
                            val cloudResult = explanationService.polish(state)
                            (cloudResult as? ExplanationResult.Polished)?.text
                        }
                        else -> null
                    }

                    if (polished != null && polishedForRisk == state.decision.overallRisk) {
                        _uiState.update { it.copy(polishedText = polished, isPolished = true) }
                    }
                }
        }
    }
}
