package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DeviceStatus
import com.krishinirnay.core.data.model.DiseaseResult
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.PestResult
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.decision.BenefitOutcome
import com.krishinirnay.core.decision.ReasonOutcome
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.decision.TimingOutcome
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore-backed cache of the last known [FieldState] — the single
 * authority for what the UI shows on cold start / in Offline Mode.
 * Firebase Realtime Database's own disk persistence is a transport
 * optimization only and is never read directly by ViewModels; this
 * cache is. `history` is intentionally not persisted — session-depth
 * trend data re-populates from the live source each launch (see
 * docs/architecture.md's Analytics scoping note); persisting a
 * cropped history buffer isn't worth the extra schema surface.
 */
@Singleton
class FieldStateCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(fieldState: FieldState) {
        dataStore.edit { it[Keys.CACHED_FIELD_STATE] = json.encodeToString(CachedFieldStateDto.serializer(), fieldState.toDto()) }
    }

    suspend fun load(): FieldState? {
        val raw = dataStore.data.map { it[Keys.CACHED_FIELD_STATE] }.first() ?: return null
        return runCatching { json.decodeFromString(CachedFieldStateDto.serializer(), raw).toDomain() }.getOrNull()
    }

    private object Keys {
        val CACHED_FIELD_STATE = stringPreferencesKey("cached_field_state")
    }
}

@Serializable
private data class CachedFieldStateDto(
    val fieldId: String,
    val soilMoisturePct: Float,
    val temperatureC: Float,
    val humidityPct: Float,
    val nitrogenPpm: Float?,
    val phosphorusPpm: Float?,
    val potassiumPpm: Float?,
    val ph: Float?,
    val sensorTimestampMillis: Long,
    val deviceOnline: Boolean,
    val deviceLastSeenMillis: Long,
    val batteryPct: Int?,
    val overallRisk: String,
    val waterStressRisk: String,
    val heatRisk: String,
    val cropHealthRisk: String,
    val recommendation: String,
    val confidence: Float,
    val reasons: List<CachedReasonDto>,
    val diseaseLabel: String?,
    val diseaseDisplayName: String?,
    val diseaseConfidence: Float?,
    val diseaseRiskLevel: String?,
    val diseaseModelVersion: String?,
    val diseaseScannedAtMillis: Long?,
    val dataSource: String,
    // Added in Phase 2 — defaulted so a cache written by an older build still decodes.
    val pestRisk: String = "UNKNOWN",
    val timing: String = "NoActionNeeded",
    val expectedBenefit: String = "Unknown",
    val pestDetected: Boolean? = null,
    val pestLabel: String? = null,
    val pestConfidence: Float? = null,
    val pestRiskLevel: String? = null,
    val pestModelVersion: String? = null,
    val pestScannedAtMillis: Long? = null,
)

/**
 * [RecommendationOutcome]/[ReasonOutcome] are plain sealed Kotlin types (no
 * kotlinx.serialization annotations — keeping core/decision free of that
 * dependency), so they're encoded here as a stable `type` tag plus whatever
 * fields that variant carries, the same way [RiskLevel] is encoded via `.name`.
 */
@Serializable
private data class CachedReasonDto(
    val type: String,
    val pct: Float? = null,
    val risk: String? = null,
    val confidencePct: Int? = null,
    val celsius: Float? = null,
    val diseaseName: String? = null,
    val pestName: String? = null,
)

private fun RecommendationOutcome.toKey(): String = this::class.simpleName!!

private fun recommendationFromKey(key: String): RecommendationOutcome = when (key) {
    "IrrigateSevere" -> RecommendationOutcome.IrrigateSevere
    "IrrigateWaterHigh" -> RecommendationOutcome.IrrigateWaterHigh
    "ShadeOrIrrigateHeat" -> RecommendationOutcome.ShadeOrIrrigateHeat
    "ReviewCropHealthHigh" -> RecommendationOutcome.ReviewCropHealthHigh
    "PlanIrrigationSoon" -> RecommendationOutcome.PlanIrrigationSoon
    "MonitorTemperature" -> RecommendationOutcome.MonitorTemperature
    "ReviewCropHealthModerate" -> RecommendationOutcome.ReviewCropHealthModerate
    "HealthyRange" -> RecommendationOutcome.HealthyRange
    "TreatPestDetected" -> RecommendationOutcome.TreatPestDetected
    "MonitorPestRisk" -> RecommendationOutcome.MonitorPestRisk
    else -> RecommendationOutcome.NotEnoughData
}

private fun TimingOutcome.toKey(): String = this::class.simpleName!!

private fun timingFromKey(key: String): TimingOutcome = when (key) {
    "Immediate" -> TimingOutcome.Immediate
    "ThisEvening" -> TimingOutcome.ThisEvening
    "Within24Hours" -> TimingOutcome.Within24Hours
    "Within3Days" -> TimingOutcome.Within3Days
    else -> TimingOutcome.NoActionNeeded
}

private fun BenefitOutcome.toKey(): String = this::class.simpleName!!

private fun benefitFromKey(key: String): BenefitOutcome = when (key) {
    "PreventCropLoss" -> BenefitOutcome.PreventCropLoss
    "ImprovedYield" -> BenefitOutcome.ImprovedYield
    "HealthyGrowthContinues" -> BenefitOutcome.HealthyGrowthContinues
    else -> BenefitOutcome.Unknown
}

private fun ReasonOutcome.toDto(): CachedReasonDto = when (this) {
    is ReasonOutcome.SoilMoisture -> CachedReasonDto(type = "SoilMoisture", pct = pct, risk = risk.name)
    is ReasonOutcome.ModelPrediction -> CachedReasonDto(type = "ModelPrediction", risk = risk.name, confidencePct = confidencePct)
    is ReasonOutcome.Temperature -> CachedReasonDto(type = "Temperature", celsius = celsius, risk = risk.name)
    ReasonOutcome.CropHealthNotAssessed -> CachedReasonDto(type = "CropHealthNotAssessed")
    is ReasonOutcome.CropHealthAssessed -> CachedReasonDto(type = "CropHealthAssessed", diseaseName = diseaseName, risk = risk.name)
    ReasonOutcome.DeviceOffline -> CachedReasonDto(type = "DeviceOffline")
    ReasonOutcome.PestNotAssessed -> CachedReasonDto(type = "PestNotAssessed")
    is ReasonOutcome.PestAssessed -> CachedReasonDto(type = "PestAssessed", pestName = pestName, risk = risk.name)
    ReasonOutcome.RainExpectedSoon -> CachedReasonDto(type = "RainExpectedSoon")
}

private fun CachedReasonDto.toDomain(): ReasonOutcome = when (type) {
    "SoilMoisture" -> ReasonOutcome.SoilMoisture(pct!!, RiskLevel.valueOf(risk!!))
    "ModelPrediction" -> ReasonOutcome.ModelPrediction(RiskLevel.valueOf(risk!!), confidencePct!!)
    "Temperature" -> ReasonOutcome.Temperature(celsius!!, RiskLevel.valueOf(risk!!))
    "CropHealthAssessed" -> ReasonOutcome.CropHealthAssessed(diseaseName, RiskLevel.valueOf(risk!!))
    "DeviceOffline" -> ReasonOutcome.DeviceOffline
    "PestAssessed" -> ReasonOutcome.PestAssessed(pestName, RiskLevel.valueOf(risk!!))
    "RainExpectedSoon" -> ReasonOutcome.RainExpectedSoon
    "PestNotAssessed" -> ReasonOutcome.PestNotAssessed
    else -> ReasonOutcome.CropHealthNotAssessed
}

private fun FieldState.toDto() = CachedFieldStateDto(
    fieldId = fieldId,
    soilMoisturePct = sensors.soilMoisturePct,
    temperatureC = sensors.temperatureC,
    humidityPct = sensors.humidityPct,
    nitrogenPpm = sensors.nitrogenPpm,
    phosphorusPpm = sensors.phosphorusPpm,
    potassiumPpm = sensors.potassiumPpm,
    ph = sensors.ph,
    sensorTimestampMillis = sensors.timestamp.toEpochMilli(),
    deviceOnline = deviceStatus.isOnline,
    deviceLastSeenMillis = deviceStatus.lastSeenAt.toEpochMilli(),
    batteryPct = deviceStatus.batteryPct,
    overallRisk = decision.overallRisk.name,
    waterStressRisk = decision.waterStressRisk.name,
    heatRisk = decision.heatRisk.name,
    cropHealthRisk = decision.cropHealthRisk.name,
    recommendation = decision.recommendation.toKey(),
    confidence = decision.confidence,
    reasons = decision.reasons.map(ReasonOutcome::toDto),
    diseaseLabel = diseaseResult?.label,
    diseaseDisplayName = diseaseResult?.displayName,
    diseaseConfidence = diseaseResult?.confidence,
    diseaseRiskLevel = diseaseResult?.riskLevel?.name,
    diseaseModelVersion = diseaseResult?.modelVersion,
    diseaseScannedAtMillis = diseaseResult?.scannedAt?.toEpochMilli(),
    dataSource = dataSource.name,
    pestRisk = decision.pestRisk.name,
    timing = decision.timing.toKey(),
    expectedBenefit = decision.expectedBenefit.toKey(),
    pestDetected = pestResult?.detected,
    pestLabel = pestResult?.label,
    pestConfidence = pestResult?.confidence,
    pestRiskLevel = pestResult?.riskLevel?.name,
    pestModelVersion = pestResult?.modelVersion,
    pestScannedAtMillis = pestResult?.scannedAt?.toEpochMilli(),
)

private fun CachedFieldStateDto.toDomain() = FieldState(
    fieldId = fieldId,
    sensors = SensorReading(
        soilMoisturePct = soilMoisturePct,
        temperatureC = temperatureC,
        humidityPct = humidityPct,
        nitrogenPpm = nitrogenPpm,
        phosphorusPpm = phosphorusPpm,
        potassiumPpm = potassiumPpm,
        ph = ph,
        timestamp = Instant.ofEpochMilli(sensorTimestampMillis),
    ),
    deviceStatus = DeviceStatus(
        isOnline = deviceOnline,
        lastSeenAt = Instant.ofEpochMilli(deviceLastSeenMillis),
        batteryPct = batteryPct,
    ),
    decision = DecisionOutput(
        overallRisk = RiskLevel.valueOf(overallRisk),
        waterStressRisk = RiskLevel.valueOf(waterStressRisk),
        heatRisk = RiskLevel.valueOf(heatRisk),
        cropHealthRisk = RiskLevel.valueOf(cropHealthRisk),
        recommendation = recommendationFromKey(recommendation),
        confidence = confidence,
        reasons = reasons.map(CachedReasonDto::toDomain),
        pestRisk = runCatching { RiskLevel.valueOf(pestRisk) }.getOrDefault(RiskLevel.UNKNOWN),
        timing = timingFromKey(timing),
        expectedBenefit = benefitFromKey(expectedBenefit),
    ),
    diseaseResult = if (diseaseLabel != null && diseaseDisplayName != null && diseaseConfidence != null &&
        diseaseRiskLevel != null && diseaseModelVersion != null && diseaseScannedAtMillis != null
    ) {
        DiseaseResult(
            label = diseaseLabel,
            displayName = diseaseDisplayName,
            confidence = diseaseConfidence,
            riskLevel = RiskLevel.valueOf(diseaseRiskLevel),
            modelVersion = diseaseModelVersion,
            scannedAt = Instant.ofEpochMilli(diseaseScannedAtMillis),
        )
    } else {
        null
    },
    pestResult = if (pestDetected != null && pestConfidence != null && pestRiskLevel != null &&
        pestModelVersion != null && pestScannedAtMillis != null
    ) {
        PestResult(
            detected = pestDetected,
            label = pestLabel,
            confidence = pestConfidence,
            riskLevel = RiskLevel.valueOf(pestRiskLevel),
            modelVersion = pestModelVersion,
            scannedAt = Instant.ofEpochMilli(pestScannedAtMillis),
        )
    } else {
        null
    },
    history = emptyList(),
    dataSource = AppMode.valueOf(dataSource),
)
