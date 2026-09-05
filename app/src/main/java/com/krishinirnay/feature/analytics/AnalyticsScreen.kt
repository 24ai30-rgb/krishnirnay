package com.krishinirnay.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.EmptyState
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.KnTopBar
import com.krishinirnay.core.designsystem.components.SimpleLineChart
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.feature.howitworks.HowItWorksDialog

@Composable
fun AnalyticsScreen(
    onNavigateToOfflineMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showHowItWorks by remember { mutableStateOf(false) }
    var selectedMetric by remember { mutableStateOf(AnalyticsMetric.MOISTURE) }

    if (showHowItWorks) {
        HowItWorksDialog(onDismiss = { showHowItWorks = false })
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KnTopBar(
                title = "Analytics",
                isOnline = uiState.isOnline,
                onSyncChipClick = onNavigateToOfflineMode,
                onHelpClick = { showHowItWorks = true },
                onSettingsClick = onNavigateToSettings,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("Moisture", selectedMetric == AnalyticsMetric.MOISTURE) { selectedMetric = AnalyticsMetric.MOISTURE }
                MetricChip("Temperature", selectedMetric == AnalyticsMetric.TEMPERATURE) { selectedMetric = AnalyticsMetric.TEMPERATURE }
                MetricChip("Humidity", selectedMetric == AnalyticsMetric.HUMIDITY) { selectedMetric = AnalyticsMetric.HUMIDITY }
            }
            Spacer(Modifier.size(14.dp))

            if (!uiState.hasEnoughData) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.ShowChart,
                        message = "Not enough data yet — check back after a few more readings come in.",
                    )
                }
            } else {
                val (values, unit, color) = when (selectedMetric) {
                    AnalyticsMetric.MOISTURE -> Triple(uiState.soilMoistureHistory, "%", Color(0xFF2F80ED))
                    AnalyticsMetric.TEMPERATURE -> Triple(uiState.temperatureHistory, "°C", Color(0xFFF2994A))
                    AnalyticsMetric.HUMIDITY -> Triple(uiState.humidityHistory, "%", Color(0xFF12A594))
                }
                KnCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = "${metricLabel(selectedMetric)} · this session",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    SimpleLineChart(
                        values = values,
                        color = color,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Session start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Now: ${values.last().let { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it) }}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else KrishiTheme.colors.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun metricLabel(metric: AnalyticsMetric): String = when (metric) {
    AnalyticsMetric.MOISTURE -> "Soil Moisture"
    AnalyticsMetric.TEMPERATURE -> "Temperature"
    AnalyticsMetric.HUMIDITY -> "Humidity"
}
