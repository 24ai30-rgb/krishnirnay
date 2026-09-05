package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.repository.SettingsRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Preferences DataStore-backed [SettingsRepository]. [AppMode] defaults
 * to MOCK when absent — the app is demo-safe out of the box with no
 * Firebase project configured.
 */
@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : SettingsRepository {

    private object Keys {
        val APP_MODE = stringPreferencesKey("app_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val HAS_SEEN_HOW_IT_WORKS = booleanPreferencesKey("has_seen_how_it_works")
        val LAST_SYNCED_AT_MILLIS = longPreferencesKey("last_synced_at_millis")
    }

    override val appMode: StateFlow<AppMode> = dataStore.data
        .map { prefs -> prefs[Keys.APP_MODE]?.let { raw -> runCatching { AppMode.valueOf(raw) }.getOrNull() } ?: AppMode.MOCK }
        .stateIn(scope, SharingStarted.Eagerly, AppMode.MOCK)

    override val language: StateFlow<String> = dataStore.data
        .map { it[Keys.LANGUAGE] ?: DEFAULT_LANGUAGE }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_LANGUAGE)

    override val hasSeenHowItWorks: StateFlow<Boolean> = dataStore.data
        .map { it[Keys.HAS_SEEN_HOW_IT_WORKS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val lastSyncedAt: StateFlow<Instant?> = dataStore.data
        .map { prefs -> prefs[Keys.LAST_SYNCED_AT_MILLIS]?.let(Instant::ofEpochMilli) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun setAppMode(mode: AppMode) {
        dataStore.edit { it[Keys.APP_MODE] = mode.name }
    }

    override suspend fun setLanguage(languageTag: String) {
        dataStore.edit { it[Keys.LANGUAGE] = languageTag }
    }

    override suspend fun setHasSeenHowItWorks(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_HOW_IT_WORKS] = seen }
    }

    override suspend fun setLastSyncedAt(instant: Instant) {
        dataStore.edit { it[Keys.LAST_SYNCED_AT_MILLIS] = instant.toEpochMilli() }
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
