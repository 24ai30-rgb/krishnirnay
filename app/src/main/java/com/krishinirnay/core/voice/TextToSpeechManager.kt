package com.krishinirnay.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Android TextToSpeech wrapper — wired into ChatbotScreen's
 * per-message "Listen" affordance and, since Phase 4F, into
 * VoiceConversationManager's automatic voice-to-voice reply.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private var isReady = false
    private val textToSpeech: TextToSpeech = TextToSpeech(context) { status ->
        isReady = status == TextToSpeech.SUCCESS
    }

    /** [onDone] fires once speech playback actually finishes (or immediately if the engine isn't ready) — never left hanging. */
    fun speak(text: String, languageTag: String = "en-IN", onDone: () -> Unit = {}) {
        if (!isReady) {
            onDone()
            return
        }
        textToSpeech.language = Locale.forLanguageTag(languageTag)
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = onDone()

                @Deprecated("Deprecated in Java", ReplaceWith(""))
                override fun onError(utteranceId: String?) = onDone()

                override fun onError(utteranceId: String?, errorCode: Int) = onDone()
            },
        )
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        textToSpeech.stop()
    }

    fun shutdown() {
        textToSpeech.shutdown()
    }
}
