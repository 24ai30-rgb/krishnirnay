package com.krishinirnay.feature.crophealth

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.repository.CropHealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiseaseUiState(
    val previewUri: Uri? = null,
    val isScanning: Boolean = false,
    val result: DiseaseResult? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DiseaseViewModel @Inject constructor(
    application: Application,
    private val cropHealthRepository: CropHealthRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiseaseUiState())
    val uiState: StateFlow<DiseaseUiState> = _uiState.asStateFlow()

    fun onImageSelected(uri: Uri) {
        _uiState.value = DiseaseUiState(
            previewUri = uri,
            isScanning = true,
        )

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                val imageBytes = context.contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: throw Exception("Unable to read selected image.")

                cropHealthRepository
                    .scanImage(imageBytes)
                    .onSuccess { result ->
                        _uiState.value = DiseaseUiState(
                            previewUri = uri,
                            isScanning = false,
                            result = result,
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = DiseaseUiState(
                            previewUri = uri,
                            isScanning = false,
                            errorMessage = error.message
                                ?: "Disease detection failed.",
                        )
                    }

            } catch (e: Exception) {
                _uiState.value = DiseaseUiState(
                    previewUri = uri,
                    isScanning = false,
                    errorMessage = e.message
                        ?: "Disease detection failed.",
                )
            }
        }
    }

    fun clearPrediction() {
        _uiState.value = DiseaseUiState()
    }
}