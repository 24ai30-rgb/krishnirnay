package com.krishinirnay.feature.crophealth

import android.net.Uri
import com.krishinirnay.core.data.model.DiseaseResult

data class CropHealthUiState(
    val isOnline: Boolean = true,
    val isScanning: Boolean = false,
    val previewUri: Uri? = null,
    val result: DiseaseResult? = null,
    val errorMessage: String? = null,
)
