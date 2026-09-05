package com.krishinirnay.feature.chatbot

import androidx.lifecycle.ViewModel
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.SchemesRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.appStringsFor
import com.krishinirnay.core.voice.SpeechRecognizerManager
import com.krishinirnay.core.voice.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fully offline — no server/Gemini call. Answers are resolved locally by keyword-matching
 * the message against the same [FieldStateRepository]/[WeatherRepository]/[SchemesRepository]
 * data every other screen reads, so replies always agree with what's shown elsewhere and work
 * with no API key configured. See ChatbotScreen's quick-reply chips for the intents covered.
 */
@HiltViewModel
class ChatbotViewModel @Inject constructor(
    private val fieldStateRepository: FieldStateRepository,
    private val weatherRepository: WeatherRepository,
    private val schemesRepository: SchemesRepository,
    private val speechRecognizerManager: SpeechRecognizerManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

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

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** [overrideText] lets quick-reply chips send their own label without touching the input field. */
    fun sendMessage(overrideText: String? = null) {
        val text = (overrideText ?: _uiState.value.inputText).trim()
        if (text.isBlank()) return

        val strings = appStringsFor(settingsRepository.language.value)
        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), text = text, isFromUser = true)
        val assistantMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = resolveReply(text, strings),
            isFromUser = false,
        )
        _uiState.update { it.copy(messages = it.messages + userMessage + assistantMessage, inputText = "") }
    }

    private fun resolveReply(text: String, strings: AppStrings): String {
        val field = fieldStateRepository.fieldState.value
        return when {
            matchesAny(text, "weather", "rain", "wind", "मौसम", "बारिश", "हवा") -> weatherReply(strings)
            matchesAny(text, "soil", "moisture", "मिट्टी", "नमी") -> soilMoistureReply(field, strings)
            matchesAny(text, "should i do", "what to do", "advice", "क्या करना", "सलाह", "क्या करें") -> adviceReply(field, strings)
            matchesAny(text, "scheme", "योजना") -> schemesReply(strings)
            else -> strings.chatbotFallbackHelp
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
        _uiState.update { it.copy(isListening = true) }
        speechRecognizerManager.startListening(
            languageTag = currentSpeechLocale(),
            onResult = { text ->
                _uiState.update { it.copy(isListening = false, inputText = text) }
                sendMessage()
            },
            onRecognitionError = {
                _uiState.update { it.copy(isListening = false) }
            },
        )
    }

    fun speak(text: String) {
        textToSpeechManager.speak(text, languageTag = currentSpeechLocale())
    }

    /** [SettingsRepository.language] is "en"/"hi"; STT/TTS need a full BCP-47 tag. */
    private fun currentSpeechLocale(): String = if (settingsRepository.language.value == "hi") "hi-IN" else "en-IN"

    override fun onCleared() {
        // textToSpeechManager is @Singleton (app-scoped) — shutting it down here would leave
        // isReady stale and permanently break voice output for the rest of the app session.
        speechRecognizerManager.destroy()
    }
}
