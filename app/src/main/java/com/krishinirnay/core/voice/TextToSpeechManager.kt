package com.krishinirnay.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Android TextToSpeech wrapper — wired into ChatbotScreen's
 * per-message "Listen" affordance.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private var isReady = false
    private val textToSpeech: TextToSpeech = TextToSpeech(context) { status ->
        isReady = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, languageTag: String = "en-IN") {
        if (isReady) {
            textToSpeech.language = Locale.forLanguageTag(languageTag)
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun stop() {
        textToSpeech.stop()
    }

    fun shutdown() {
        textToSpeech.shutdown()
    }
}
