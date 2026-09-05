package com.krishinirnay.feature.crophealth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.repository.CropHealthRepository
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.network.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CropHealthViewModel @Inject constructor(
    private val cropHealthRepository: CropHealthRepository,
    private val fieldStateRepository: FieldStateRepository,
    private val imageCompressor: ImageCompressor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CropHealthUiState()
    )

    val uiState: StateFlow<CropHealthUiState> =
        _uiState.asStateFlow()

    fun onImageSelected(uri: Uri) {

        // ---------------------------------------------------------
        // SHOW SELECTED IMAGE
        // ---------------------------------------------------------

        _uiState.update {
            it.copy(
                previewUri = uri,
                result = null,
                errorMessage = null,
                isScanning = true,
            )
        }

        // ---------------------------------------------------------
        // COMPRESS IMAGE
        // ---------------------------------------------------------

        viewModelScope.launch {

            val imageBytes = try {

                withContext(Dispatchers.IO) {
                    imageCompressor.compress(uri)
                }

            } catch (error: Exception) {

                android.util.Log.e(
                    "KRISHI_DISEASE",
                    "Image compression failed",
                    error,
                )

                null
            }

            // -----------------------------------------------------
            // IMAGE READ FAILED
            // -----------------------------------------------------

            if (imageBytes == null || imageBytes.isEmpty()) {

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage =
                            "Couldn't read that photo. " +
                                "Please select another leaf image.",
                    )
                }

                return@launch
            }

            android.util.Log.d(
                "KRISHI_DISEASE",
                "Image ready. Size=${imageBytes.size} bytes",
            )

            // -----------------------------------------------------
            // SEND TO FASTAPI
            // -----------------------------------------------------

            cropHealthRepository
                .scanImage(imageBytes)

                .onSuccess { result ->

                    android.util.Log.d(
                        "KRISHI_DISEASE",
                        "Disease scan SUCCESS: " +
                            "${result.displayName}",
                    )

                    // Save result to shared FieldState
                    fieldStateRepository.recordDiseaseResult(
                        result
                    )

                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            result = result,
                            errorMessage = null,
                        )
                    }
                }

                .onFailure { throwable ->

                    android.util.Log.e(
                        "KRISHI_DISEASE",
                        "Disease scan FAILED: " +
                            throwable.message,
                        throwable,
                    )

                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            errorMessage =
                                throwable.message
                                    ?: "Disease analysis failed. Please try again.",
                        )
                    }
                }
        }
    }
}