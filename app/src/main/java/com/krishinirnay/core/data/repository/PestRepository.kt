package com.krishinirnay.core.data.repository

import com.krishinirnay.core.network.dto.PestPredictionResponseDto
import java.io.File

interface PestRepository {

    suspend fun predictPest(
        imageFile: File,
    ): Result<PestPredictionResponseDto>
}