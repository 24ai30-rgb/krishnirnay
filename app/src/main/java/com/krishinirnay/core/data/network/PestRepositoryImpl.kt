package com.krishinirnay.core.data.network

import com.krishinirnay.core.data.repository.PestRepository
import com.krishinirnay.core.network.PestApiService
import com.krishinirnay.core.network.dto.PestPredictionResponseDto
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

@Singleton
class PestRepositoryImpl @Inject constructor(
    private val pestApiService: PestApiService,
) : PestRepository {

    override suspend fun predictPest(
        imageFile: File,
    ): Result<PestPredictionResponseDto> = runCatching {

        if (!imageFile.exists()) {
            throw IllegalArgumentException(
                "Image file does not exist.",
            )
        }

        val requestBody = imageFile.asRequestBody(
            "image/*".toMediaType(),
        )

        val multipartFile =
            MultipartBody.Part.createFormData(
                name = "file",
                filename = imageFile.name,
                body = requestBody,
            )

        val response =
            pestApiService.predictPest(
                file = multipartFile,
            )

        val body = response.body()

        if (response.isSuccessful && body != null) {
            body
        } else {
            throw RuntimeException(
                "Pest prediction failed. HTTP ${response.code()}",
            )
        }
    }
}