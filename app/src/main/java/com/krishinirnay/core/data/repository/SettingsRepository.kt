package com.krishinirnay.core.data.repository

import com.krishinirnay.core.data.model.AppMode
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * User/session-level preferences, backed by Preferences DataStore
 * ([com.krishinirnay.core.data.local.AppPreferences]). [appMode] is what
 * [com.krishinirnay.core.data.composite.DefaultFieldStateRepository]
 * switches on; language and How-it-Works dismissal live here too since
 * they're the same kind of small, durable, per-device preference.
 */
interface SettingsRepository {
    val appMode: StateFlow<AppMode>
    val language: StateFlow<String>
    val hasSeenHowItWorks: StateFlow<Boolean>
    val lastSyncedAt: StateFlow<Instant?>

    /**
     * Off by default (Phase 4E) — cloud Gemini is an explicit, opt-in fallback
     * for AI Insights' explanation polish, never the primary conversational
     * path, and never called silently. See ExplanationService/AiInsightsViewModel.
     */
    val cloudFallbackEnabled: StateFlow<Boolean>

    /** True once the farmer has completed (or skipped) the multi-step onboarding flow — see `feature.onboarding`. */
    val hasCompletedOnboarding: StateFlow<Boolean>

    suspend fun setAppMode(mode: AppMode)
    suspend fun setLanguage(languageTag: String)
    suspend fun setHasSeenHowItWorks(seen: Boolean)
    suspend fun setLastSyncedAt(instant: Instant)
    suspend fun setCloudFallbackEnabled(enabled: Boolean)
    suspend fun setHasCompletedOnboarding(completed: Boolean)
}
