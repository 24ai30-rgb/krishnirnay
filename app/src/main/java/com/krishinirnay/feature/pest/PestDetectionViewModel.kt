package com.krishinirnay.feature.pest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.PestRepository
import com.krishinirnay.core.network.dto.PestPredictionResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PestDetectionUiState(
    val isLoading: Boolean = false,
    val result: PestPredictionResponseDto? = null,
    val error: String? = null,
)

@HiltViewModel
class PestDetectionViewModel @Inject constructor(
    private val pestRepository: PestRepository,
    private val fieldStateRepository: FieldStateRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(PestDetectionUiState())

    val uiState: StateFlow<PestDetectionUiState> =
        _uiState.asStateFlow()

    fun detectPest(imageFile: File) {

        if (!imageFile.exists()) {
            _uiState.value = PestDetectionUiState(
                error = "Selected image not found.",
            )
            return
        }

        viewModelScope.launch {

            _uiState.value =
                PestDetectionUiState(
                    isLoading = true,
                )

            pestRepository
                .predictPest(imageFile)
                .onSuccess { result ->

                    _uiState.value =
                        PestDetectionUiState(
                            isLoading = false,
                            result = result,
                        )

                    // Feed the real scan result into the shared decision pipeline —
                    // see FieldStateRepository.recordPestResult / DecisionEngine.
                    fieldStateRepository.recordPestResult(result.toPestResult())
                }
                .onFailure { error ->

                    android.util.Log.e(
                        "PEST_DETECTION",
                        "Pest detection failed",
                        error,
                    )

                    _uiState.value =
                        PestDetectionUiState(
                            isLoading = false,
                            error =
                                error.message
                                    ?: "Pest detection failed.",
                        )
                }
        }
    }

    fun clearResult() {
        _uiState.value =
            PestDetectionUiState()
    }
}

/**
 * The real YOLOv8 response has no risk_level field (same gap as disease — see
 * KRISHINIRNAY_IMPLEMENTATION_PLAN.md finding B6), so risk is derived here from
 * `detected` + the top detection's confidence, never invented.
 */
private fun PestPredictionResponseDto.toPestResult(): PestResult {
    val topConfidence = top_detection?.confidence ?: 0f
    val riskLevel = when {
        !detected -> RiskLevel.LOW
        topConfidence >= 0.7f -> RiskLevel.HIGH
        topConfidence >= 0.4f -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
    return PestResult(
        detected = detected,
        label = top_detection?.class_name,
        confidence = topConfidence,
        riskLevel = riskLevel,
        modelVersion = model_version,
        scannedAt = Instant.now(),
    )
}