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


/**
 * [message] is always farmer-safe (no IPs, hostnames, or raw exception
 * text) — [technicalDetail], when present, is the real cause for a
 * developer-mode/diagnostic view only (see ProfessionalErrorCard). Never
 * shown to a normal farmer, per Part 13's "do not expose technical IP
 * addresses to normal farmers."
 */
class CropHealthScanException(message: String, val technicalDetail: String? = null) : Exception(message)


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
                message = "AI server is unavailable. Please check your connection and try again.",
                technicalDetail = "${error::class.simpleName}: ${error.message}\n\n" +
                    "Check:\n" +
                    "1. Uvicorn is running\n" +
                    "2. adb reverse tcp:8000 tcp:8000 is active (physical device), " +
                    "or the app's base URL matches your LAN IP\n" +
                    "3. FastAPI is listening on port 8000",
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

    // The server's real /v1/predict/disease response has no risk_level field (see
    // KRISHINIRNAY_IMPLEMENTATION_PLAN.md, finding B6) — it must be derived here from
    // `status` + confidence, not left UNKNOWN. Previously this was hardcoded to
    // RiskLevel.UNKNOWN, which meant a real disease scan never affected crop-health
    // risk or DecisionEngine's overall risk at all.
    val riskLevel = when {
        status.contains("HEALTHY", ignoreCase = true) -> RiskLevel.LOW
        normalizedConfidence >= 0.6f -> RiskLevel.HIGH
        else -> RiskLevel.MEDIUM
    }

    return DiseaseResult(
        label = prediction,
        displayName = displayName,
        confidence = normalizedConfidence,
        riskLevel = riskLevel,
        modelVersion = "disease-v1",
        scannedAt = Instant.now(),
    )
}