package com.krishinirnay.core.data.network

import android.util.Log
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.CropHealthRepository
import com.krishinirnay.core.network.DiseaseApiService
import com.krishinirnay.core.network.dto.DiseaseResponseDto
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response


class CropHealthScanException(message: String) : Exception(message)


@Singleton
class CropHealthRepositoryImpl @Inject constructor(
    private val diseaseApiService: DiseaseApiService,
) : CropHealthRepository {

    override suspend fun scanImage(
        imageBytes: ByteArray,
    ): Result<DiseaseResult> = runCatching {

        Log.d(
            "KRISHI_DISEASE",
            "========================================",
        )

        Log.d(
            "KRISHI_DISEASE",
            "Starting disease scan",
        )

        Log.d(
            "KRISHI_DISEASE",
            "Image size = ${imageBytes.size} bytes",
        )

        if (imageBytes.isEmpty()) {
            throw CropHealthScanException(
                "Selected image is empty. Please choose another photo.",
            )
        }

        // =========================================================
        // IMAGE REQUEST
        // =========================================================

        val requestBody = imageBytes.toRequestBody(
            "image/jpeg".toMediaType(),
        )

        val part = MultipartBody.Part.createFormData(
            name = "image",
            filename = "scan.jpg",
            body = requestBody,
        )

        Log.d(
            "KRISHI_DISEASE",
            "Calling POST /v1/predict/disease",
        )

        // =========================================================
        // API CALL
        // =========================================================

        val response: Response<DiseaseResponseDto>

        try {
            response = diseaseApiService.predictDisease(part)
        } catch (error: IOException) {

            Log.e(
                "KRISHI_DISEASE",
                "NETWORK ERROR",
                error,
            )

            throw CropHealthScanException(
                "Cannot connect to FastAPI server.\n\n" +
                    "Check:\n" +
                    "1. Uvicorn is running\n" +
                    "2. adb reverse is active\n" +
                    "3. FastAPI is running on port 8000",
            )
        }

        Log.d(
            "KRISHI_DISEASE",
            "FastAPI HTTP status = ${response.code()}",
        )

        // =========================================================
        // SUCCESS
        // =========================================================

        if (response.isSuccessful) {

            val body = response.body()
                ?: throw CropHealthScanException(
                    "FastAPI returned an empty disease prediction.",
                )

            Log.d(
                "KRISHI_DISEASE",
                "========================================",
            )

            Log.d(
                "KRISHI_DISEASE",
                "DISEASE PREDICTION SUCCESS",
            )

            Log.d(
                "KRISHI_DISEASE",
                "Crop = ${body.crop}",
            )

            Log.d(
                "KRISHI_DISEASE",
                "Prediction = ${body.prediction}",
            )

            Log.d(
                "KRISHI_DISEASE",
                "Confidence = ${body.confidence}",
            )

            Log.d(
                "KRISHI_DISEASE",
                "Status = ${body.status}",
            )

            Log.d(
                "KRISHI_DISEASE",
                "Top Predictions = ${body.top_predictions}",
            )

            Log.d(
                "KRISHI_DISEASE",
                "========================================",
            )

            body.toDomain()

        } else {

            val errorBody = runCatching {
                response.errorBody()?.string()
            }.getOrNull()

            Log.e(
                "KRISHI_DISEASE",
                "Disease API failed: HTTP ${response.code()}",
            )

            Log.e(
                "KRISHI_DISEASE",
                "Error body = $errorBody",
            )

            throw CropHealthScanException(
                mapErrorMessage(response),
            )
        }
    }

    // =============================================================
    // ERROR MAPPING
    // =============================================================

    private fun mapErrorMessage(
        response: Response<DiseaseResponseDto>,
    ): String {

        return when (response.code()) {

            400 ->
                "Invalid image. Please select a clear crop leaf photo."

            401 ->
                "Server authentication failed. Please check API key."

            403 ->
                "Access denied by FastAPI server."

            404 ->
                "Disease detection endpoint not found.\n\n" +
                    "Expected:\n" +
                    "POST /v1/predict/disease"

            413 ->
                "Photo is too large. Please choose a smaller image."

            422 ->
                "Image format is invalid. Please select a JPG or PNG leaf photo."

            500 ->
                "Disease model server error. Please try again."

            501 ->
                "Disease detection model is not available."

            503 ->
                "Disease detection service is temporarily unavailable."

            else ->
                "Couldn't analyze the photo.\nHTTP ${response.code()}"
        }
    }
}


// ================================================================
// DTO -> DOMAIN
// ================================================================

private fun DiseaseResponseDto.toDomain(): DiseaseResult {

    val displayName = if (crop.isNotBlank()) {
        "$crop — ${prediction.replace("_", " ")}"
    } else {
        prediction.replace("_", " ")
    }

    // FastAPI returns confidence as percentage.
    // Example: 75.30 -> Android needs 0.753.
    val normalizedConfidence =
        (confidence / 100f).coerceIn(0f, 1f)

    Log.d(
        "KRISHI_DISEASE",
        "Raw confidence = $confidence%",
    )

    Log.d(
        "KRISHI_DISEASE",
        "Normalized confidence = $normalizedConfidence",
    )

    return DiseaseResult(
        label = prediction,
        displayName = displayName,
        confidence = normalizedConfidence,
        riskLevel = RiskLevel.UNKNOWN,
        modelVersion = "disease-v1",
        scannedAt = Instant.now(),
    )
}