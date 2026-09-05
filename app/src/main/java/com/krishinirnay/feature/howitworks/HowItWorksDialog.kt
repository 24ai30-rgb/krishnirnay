package com.krishinirnay.feature.howitworks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Not a nav route — local Compose dialog state, toggled from the "?"
 * icon in [com.krishinirnay.core.designsystem.components.KnTopBar] on
 * every top-level screen. Explains the Sense -> AI Models -> Decision
 * Engine -> Explanation -> Farmer pipeline for unsupervised judges.
 */
@Composable
fun HowItWorksDialog(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("How KrishiNirnay Works", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.size(16.dp))

                PipelineStep("1. Sense", "An ESP32 device in the field reads soil moisture, temperature, and humidity.")
                PipelineStep("2. AI Models", "An on-device model predicts irrigation risk; a server model detects crop disease from a photo.")
                PipelineStep(
                    "3. Decision Engine",
                    "Rule-based logic combines everything into a risk level and a plain-English recommendation — this step works even fully offline.",
                )
                PipelineStep("4. Explanation", "An optional AI layer polishes the wording in AI Insights and the chatbot when you're online.")
                PipelineStep("5. Farmer", "You see a clear risk level and what to do next, consistently across every screen in the app.")

                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Got it")
                }
            }
        }
    }
}

@Composable
private fun PipelineStep(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
