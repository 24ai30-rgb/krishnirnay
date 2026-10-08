package com.krishinirnay.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.krishinirnay.core.common.ApplicationScope
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.ThemeMode
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
        val CLOUD_FALLBACK_ENABLED = booleanPreferencesKey("cloud_fallback_enabled")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /**
     * Defaults to [AppMode.LIVE] so a fresh install actually shows real weather
     * and real mandi prices. It previously defaulted to [AppMode.MOCK], which
     * meant every new farmer saw demo values (correctly badged MOCK, but still
     * demo) until they found the Mock/Live toggle in Settings -> Advanced.
     *
     * Live Mode is safe as a default because it degrades honestly: when the
     * server or a provider is unreachable, weather/market show CACHED or
     * UNAVAILABLE rather than inventing numbers. Mock Mode is unchanged and
     * still selectable for demos and for sensor simulation without hardware.
     */
    override val appMode: StateFlow<AppMode> = dataStore.data
        .map { prefs -> prefs[Keys.APP_MODE]?.let { raw -> runCatching { AppMode.valueOf(raw) }.getOrNull() } ?: AppMode.LIVE }
        .stateIn(scope, SharingStarted.Eagerly, AppMode.LIVE)

    override val language: StateFlow<String> = dataStore.data
        .map { it[Keys.LANGUAGE] ?: DEFAULT_LANGUAGE }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_LANGUAGE)

    override val hasSeenHowItWorks: StateFlow<Boolean> = dataStore.data
        .map { it[Keys.HAS_SEEN_HOW_IT_WORKS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val lastSyncedAt: StateFlow<Instant?> = dataStore.data
        .map { prefs -> prefs[Keys.LAST_SYNCED_AT_MILLIS]?.let(Instant::ofEpochMilli) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val cloudFallbackEnabled: StateFlow<Boolean> = dataStore.data
        .map { it[Keys.CLOUD_FALLBACK_ENABLED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val hasCompletedOnboarding: StateFlow<Boolean> = dataStore.data
        .map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

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

    override suspend fun setCloudFallbackEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CLOUD_FALLBACK_ENABLED] = enabled }
    }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    override val themeMode: StateFlow<ThemeMode> = dataStore.data
        .map { ThemeMode.fromStored(it[Keys.THEME_MODE]) }
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
