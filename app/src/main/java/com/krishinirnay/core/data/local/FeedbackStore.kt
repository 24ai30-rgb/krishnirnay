package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.data.model.FeedbackAction
import com.krishinirnay.core.data.model.FeedbackEntry
import com.krishinirnay.core.data.model.FeedbackResult
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * DataStore-backed persistence for the farmer's feedback history — same
 * pattern as [FarmerProfileStore]/[FieldStateCache]: local-only, works with
 * zero network, and never alters DecisionEngine's rules automatically (see
 * FeedbackEntry's doc comment).
 */
@Singleton
class FeedbackStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(CachedFeedbackEntryDto.serializer())

    suspend fun save(entries: List<FeedbackEntry>) {
        dataStore.edit { it[Keys.FEEDBACK_ENTRIES] = json.encodeToString(listSerializer, entries.map { entry -> entry.toDto() }) }
    }

    suspend fun load(): List<FeedbackEntry> {
        val raw = dataStore.data.map { it[Keys.FEEDBACK_ENTRIES] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw).map { it.toDomain() } }.getOrDefault(emptyList())
    }

    private object Keys {
        val FEEDBACK_ENTRIES = stringPreferencesKey("feedback_entries")
    }
}

@Serializable
private data class CachedFeedbackEntryDto(
    val id: String,
    val timestampMillis: Long,
    val crop: String?,
    val cropStage: String?,
    val recommendation: String,
    val actionTaken: String,
    val result: String?,
    val notes: String?,
)

private fun FeedbackEntry.toDto() = CachedFeedbackEntryDto(
    id = id,
    timestampMillis = timestamp.toEpochMilli(),
    crop = crop,
    cropStage = cropStage,
    recommendation = recommendation,
    actionTaken = actionTaken.name,
    result = result?.name,
    notes = notes,
)

private fun CachedFeedbackEntryDto.toDomain() = FeedbackEntry(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampMillis),
    crop = crop,
    cropStage = cropStage,
    recommendation = recommendation,
    actionTaken = runCatching { FeedbackAction.valueOf(actionTaken) }.getOrDefault(FeedbackAction.NO),
    result = result?.let { runCatching { FeedbackResult.valueOf(it) }.getOrNull() },
    notes = notes,
)
