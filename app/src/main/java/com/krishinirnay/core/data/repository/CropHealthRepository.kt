package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.DiseaseResult

interface CropHealthRepository {
    suspend fun scanImage(imageBytes: ByteArray): Result<DiseaseResult>
}
