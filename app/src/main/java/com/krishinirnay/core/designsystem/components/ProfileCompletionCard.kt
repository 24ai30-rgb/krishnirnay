package com.krishinirnay.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shows real completion computed from stored [com.krishinirnay.core.data.model.FarmerProfile]
 * fields — see [com.krishinirnay.feature.profile.completion] — never a hardcoded percentage.
 */
@Composable
fun ProfileCompletionCard(
    percent: Int,
    missingLabels: List<String>,
    completeMessage: String,
    percentTemplate: String,
    missingLabel: String,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(durationMillis = 400),
        label = "profileCompletionProgress",
    )
    KnCard(modifier = modifier) {
        Text(
            text = String.format(percentTemplate, percent),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        if (missingLabels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = missingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = missingLabels.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = completeMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
