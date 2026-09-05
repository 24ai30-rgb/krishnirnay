package com.krishinirnay.feature.pest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.repository.PestRepository
import com.krishinirnay.core.network.dto.PestPredictionResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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