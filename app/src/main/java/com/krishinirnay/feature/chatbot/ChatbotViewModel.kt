package com.krishinirnay.feature.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SchemesRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.appStringsFor
import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmContextBuilder
import com.krishinirnay.core.llm.local.LocalLlmRepository
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.LocalLlmStreamEvent
import com.krishinirnay.core.voice.SpeechRecognizerManager
import com.krishinirnay.core.voice.TextToSpeechManager
import com.krishinirnay.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Answers are resolved two ways, in order: (1) an instant, fully offline
 * keyword match against the same FieldStateRepository/WeatherRepository/
 * SchemesRepository data every other screen reads — this always works, with
 * no server and no API key, exactly as before; (2) when no keyword matches,
 * a real Local LLM call (see LocalLlmRepository) grounded in that same data
 * plus the current DecisionOutput, which only ever runs against this app's
 * own server (never a cloud LLM directly). If the local model is unreachable,
 * the honest fallback-help text from (1) stays on screen — no cloud call is
 * ever made silently, and the chat never blocks waiting for the model.
 *
 * Phase 4F: a question asked by voice (mic button) is also spoken back
 * automatically once resolved — a typed question is never auto-spoken, only
 * shown in the transcript, exactly as before.
 */
@HiltViewModel
class ChatbotViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    private val weatherRepository: WeatherRepository,
    private val marketRepository: MarketRepository,
    private val profileRepository: ProfileRepository,
    private val schemesRepository: SchemesRepository,
    private val speechRecognizerManager: SpeechRecognizerManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val settingsRepository: SettingsRepository,
    private val localLlmRepository: LocalLlmRepository,
    private val localLlmContextBuilder: LocalLlmContextBuilder,
) : ViewModel() {

    /** The in-flight streamed generation, if any — cancelled by [cancelGeneration] or replaced by the next question. */
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(
        ChatbotUiState(
            messages = listOf(
                ChatMessage(
                    id = "welcome",
                    text = appStringsFor(settingsRepository.language.value).chatbotWelcome,
                    isFromUser = false,
                ),
            ),
        ),
    )
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            localLlmRepository.status.collect { status ->
                _uiState.update { it.copy(aiStatus = status) }
            }
        }
        viewModelScope.launch {
            localLlmRepository.activeProviderKind.collect { kind ->
                _uiState.update { it.copy(aiProviderKind = kind) }
            }
        }
        // Fire-and-forget, never awaited before a message can be sent (see
        // sendMessage/streamLocalLlmReply, which read whatever status is
        // already cached) — this only makes sure the status shown on screen
        // is fresh as of opening Chat, e.g. after the farmer downloaded the
        // on-device model in Settings during a previous app session.
        viewModelScope.launch { localLlmRepository.refreshStatus() }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** Runs a real (~15-30s) connection check — only ever triggered by the user tapping the diagnostics button, never automatically. */
    fun checkConnection() {
        _uiState.update { it.copy(isCheckingConnection = true, diagnostics = null) }
        viewModelScope.launch {
            val result = localLlmRepository.runDiagnostics()
            _uiState.update { it.copy(isCheckingConnection = false, diagnostics = result) }
        }
    }

    /** [overrideText] lets quick-reply chips send their own label without touching the input field. */
    fun sendMessage(overrideText: String? = null) {
        val text = (overrideText ?: _uiState.value.inputText).trim()
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = "") }
        resolveAndAppend(text, autoSpeak = false)
    }

    /** Appends the user's question and an assistant reply, resolving it via keyword match then Local LLM. */
    private fun resolveAndAppend(text: String, autoSpeak: Boolean) {
        val strings = appStringsFor(settingsRepository.language.value)
        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), text = text, isFromUser = true)
        val keywordReply = resolveKeywordReply(text, strings)

        if (keywordReply != null) {
            val assistantMessage = ChatMessage(id = UUID.randomUUID().toString(), text = keywordReply, isFromUser = false)
            _uiState.update { it.copy(messages = it.messages + userMessage + assistantMessage) }
            if (autoSpeak) speakAndReturnToReady(keywordReply) else setVoiceState(VoiceState.READY)
            return
        }

        // Empty text + isGenerating=true is the "Thinking..." state — see
        // ChatbotScreen's MessageBubble, which shows a typing indicator until
        // the first streamed delta arrives and starts filling this in live.
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantMessage = ChatMessage(id = assistantMessageId, text = "", isFromUser = false, isGenerating = true)
        _uiState.update { it.copy(messages = it.messages + userMessage + assistantMessage, isGenerating = true) }
        streamLocalLlmReply(text, assistantMessageId, autoSpeak)
    }

    /** Streams a Local LLM reply into the already-appended placeholder message identified by [assistantMessageId] — shared by a fresh question and by [retryMessage]. */
    private fun streamLocalLlmReply(question: String, assistantMessageId: String, autoSpeak: Boolean) {
        val strings = appStringsFor(settingsRepository.language.value)
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            if (autoSpeak) setVoiceState(VoiceState.PROCESSING)
            val context = localLlmContextBuilder.build(
                fieldState = fieldStateRepository.fieldState.value,
                profile = profileRepository.profile.value,
                weather = weatherRepository.weather.value,
                market = marketRepository.market.value,
                language = settingsRepository.language.value,
            )
            var finalText = ""
            try {
                localLlmRepository.askStream(question, context).collect { event ->
                    when (event) {
                        is LocalLlmStreamEvent.Delta -> updateMessage(assistantMessageId) { it.copy(text = it.text + event.text) }
                        is LocalLlmStreamEvent.Final -> {
                            finalText = if (event.success && !event.answer.isNullOrBlank()) {
                                event.answer
                            } else {
                                // The honest reason, never the streamed-so-far
                                // (unvalidated) partial text — see
                                // LocalLlmStreamEvent's own doc comment.
                                localAiUnavailableMessage(strings)
                            }
                            updateMessage(assistantMessageId) {
                                it.copy(text = finalText, isGenerating = false, isError = finalText != event.answer)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
            _uiState.update { it.copy(isGenerating = false) }
            if (autoSpeak && finalText.isNotBlank()) speakAndReturnToReady(finalText) else setVoiceState(VoiceState.READY)
        }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    /** Stops the in-flight generation — the partial (unvalidated) text is never kept, matching the "never fake success" rule for a completed answer. */
    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        val strings = appStringsFor(settingsRepository.language.value)
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map {
                    if (it.isGenerating) it.copy(text = strings.localAiStopped, isGenerating = false, isError = true) else it
                },
            )
        }
        setVoiceState(VoiceState.READY)
    }

    /** Re-runs generation for the question paired with [assistantMessageId], replacing that same bubble rather than adding a new one. */
    fun retryMessage(assistantMessageId: String) {
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == assistantMessageId }
        val question = messages.getOrNull(index - 1)?.takeIf { it.isFromUser }?.text ?: return
        updateMessage(assistantMessageId) { it.copy(text = "", isGenerating = true, isError = false) }
        _uiState.update { it.copy(isGenerating = true) }
        streamLocalLlmReply(question, assistantMessageId, autoSpeak = false)
    }

    /** Resets the transcript to the welcome message — cancels any generation still in flight first. */
    fun clearChat() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        id = "welcome",
                        text = appStringsFor(settingsRepository.language.value).chatbotWelcome,
                        isFromUser = false,
                    ),
                ),
                isGenerating = false,
                inputText = "",
            )
        }
    }

    /**
     * The honest reason the Local AI produced nothing, never a stand-in answer.
     * [AiProviderKind.NONE] means neither the on-device model nor the server
     * was ready when this was checked — the actionable, farmer-facing case
     * (Phase 5 Part 4): tell them exactly what would fix it (download the
     * on-device model, or get the AI server reachable) rather than a generic
     * "unavailable" label. A provider that *was* selected but whose specific
     * call then failed (activeProviderKind still ON_DEVICE/SERVER) gets the
     * distinct "generation failed" message instead — that is a different
     * problem with a different fix (retry) than "nothing is set up at all".
     */
    private fun localAiUnavailableMessage(strings: AppStrings): String =
        when {
            localLlmRepository.activeProviderKind.value == AiProviderKind.NONE -> strings.localAiBothUnavailable
            localLlmRepository.status.value == LocalLlmStatus.MODEL_MISSING -> strings.settingsAiModeModelMissing
            localLlmRepository.status.value == LocalLlmStatus.UNAVAILABLE -> strings.settingsAiModeUnavailable
            else -> strings.localAiGenerationFailed
        }

    /** Null when nothing matches — callers show a real Local LLM answer or the honest fallback-help text instead. */
    private fun resolveKeywordReply(text: String, strings: AppStrings): String? {
        val field = fieldStateRepository.fieldState.value
        return when {
            matchesAny(text, "weather", "rain", "wind", "मौसम", "बारिश", "हवा") -> weatherReply(strings)
            matchesAny(text, "soil", "moisture", "मिट्टी", "नमी") -> soilMoistureReply(field, strings)
            matchesAny(text, "should i do", "what to do", "advice", "क्या करना", "सलाह", "क्या करें") -> adviceReply(field, strings)
            matchesAny(text, "scheme", "योजना") -> schemesReply(strings)
            else -> null
        }
    }

    private fun matchesAny(text: String, vararg keywords: String) = keywords.any { text.contains(it, ignoreCase = true) }

    private fun weatherReply(strings: AppStrings): String {
        val weather = weatherRepository.weather.value
        return String.format(strings.chatbotWeatherReplyTemplate, weather.rainInHoursLabel, weather.rainChancePct, weather.windKph)
    }

    private fun soilMoistureReply(field: FieldState, strings: AppStrings): String {
        val status = when (field.decision.waterStressRisk) {
            RiskLevel.HIGH -> strings.chatbotSoilStatusDeficient
            RiskLevel.MEDIUM -> strings.chatbotSoilStatusTrendingLow
            RiskLevel.LOW -> strings.chatbotSoilStatusHealthy
            RiskLevel.UNKNOWN -> strings.chatbotSoilStatusUnknown
        }
        return String.format(strings.chatbotSoilMoistureReplyTemplate, field.sensors.soilMoisturePct, status)
    }

    private fun adviceReply(field: FieldState, strings: AppStrings): String = when (field.decision.waterStressRisk) {
        RiskLevel.HIGH -> String.format(strings.chatbotAdviceIrrigateNowTemplate, 40)
        RiskLevel.MEDIUM -> String.format(strings.chatbotAdviceIrrigateSoonTemplate, 20)
        RiskLevel.LOW -> strings.chatbotAdviceNoIrrigationNeeded
        RiskLevel.UNKNOWN -> strings.chatbotAdviceNotEnoughData
    }

    private fun schemesReply(strings: AppStrings): String {
        val names = schemesRepository.schemes.value.joinToString(", ") { it.name }
        return String.format(strings.chatbotSchemesReplyTemplate, names)
    }

    /** Caller must already hold RECORD_AUDIO — see ChatbotScreen's permission launcher. */
    fun startListening() {
        _uiState.update { it.copy(isListening = true, voiceState = VoiceState.LISTENING) }
        speechRecognizerManager.startListening(
            languageTag = currentSpeechLocale(),
            onResult = { text ->
                _uiState.update { it.copy(isListening = false) }
                if (text.isBlank()) {
                    setVoiceState(VoiceState.READY)
                } else {
                    resolveAndAppend(text, autoSpeak = true)
                }
            },
            onRecognitionError = {
                _uiState.update { it.copy(isListening = false, voiceState = VoiceState.ERROR) }
            },
        )
    }

    fun speak(text: String) {
        textToSpeechManager.speak(text, languageTag = currentSpeechLocale())
    }

    private fun speakAndReturnToReady(text: String) {
        setVoiceState(VoiceState.SPEAKING)
        textToSpeechManager.speak(text, languageTag = currentSpeechLocale()) {
            setVoiceState(VoiceState.READY)
        }
    }

    private fun setVoiceState(state: VoiceState) {
        _uiState.update { it.copy(voiceState = state) }
    }

    /** [SettingsRepository.language] is "en"/"hi"/"mr"; STT/TTS need a full BCP-47 tag. */
    private fun currentSpeechLocale(): String = when (settingsRepository.language.value) {
        "hi" -> "hi-IN"
        "mr" -> "mr-IN"
        else -> "en-IN"
    }

    override fun onCleared() {
        // textToSpeechManager is @Singleton (app-scoped) — shutting it down here would leave
        // isReady stale and permanently break voice output for the rest of the app session.
        speechRecognizerManager.destroy()
    }
}
