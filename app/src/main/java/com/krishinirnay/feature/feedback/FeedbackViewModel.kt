package com.krishinirnay.feature.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishinirnay.core.data.model.FeedbackAction
import com.krishinirnay.core.data.model.FeedbackEntry
import com.krishinirnay.core.data.model.FeedbackResult
import com.krishinirnay.core.data.repository.FeedbackRepository
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Records a farmer's reaction to today's recommendation — never alters
 * DecisionEngine's deterministic rules; this is purely a persisted record
 * for later analytics/model-improvement (see FeedbackEntry's doc comment).
 */
@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val fieldStateRepository: FieldStateRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    fun submit(action: FeedbackAction, result: FeedbackResult?, notes: String? = null) {
        val field = fieldStateRepository.fieldState.value
        val profile = profileRepository.profile.value
        val entry = FeedbackEntry(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            crop = profile.primaryCrop,
            cropStage = profile.seedlingStage.ifBlank { null },
            recommendation = field.decision.recommendation.toString(),
            actionTaken = action,
            result = result,
            notes = notes,
        )
        viewModelScope.launch { feedbackRepository.record(entry) }
    }
}
