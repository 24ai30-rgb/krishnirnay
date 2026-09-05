package com.krishinirnay.core.llm

import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.llm.dto.ChatRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

sealed interface ExplanationResult {
    data class Polished(val text: String) : ExplanationResult
    data object Fallback : ExplanationResult
}

/**
 * Wraps [FieldState.decision]'s rule-based recommendation with an
 * optional AI-polished version — called from the ViewModel layer, never
 * from inside DecisionEngine. AI Insights renders the plain
 * recommendation immediately (offline-safe) and silently upgrades to
 * the polished text if/when this succeeds; it never blocks or shows an
 * error, unlike the Chatbot's explicit "needs internet" banner. Calls
 * this app's own server (`mode: "explain"`), never Gemini directly — see
 * the class doc on [ChatApiService].
 */
@Singleton
class ExplanationService @Inject constructor(
    private val chatApiService: ChatApiService,
    private val chatContextBuilder: ChatContextBuilder,
) {
    suspend fun polish(fieldState: FieldState): ExplanationResult {
        return try {
            val response = chatApiService.chat(
                ChatRequestDto(
                    mode = "explain",
                    message = "Explain this recommendation in one or two friendly sentences for a farmer.",
                    context = chatContextBuilder.build(fieldState),
                ),
            )
            val body = response.body()
            if (response.isSuccessful && body != null) ExplanationResult.Polished(body.reply) else ExplanationResult.Fallback
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExplanationResult.Fallback
        }
    }
}
