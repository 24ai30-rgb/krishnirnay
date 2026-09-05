package com.krishinirnay.feature.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.common.toRelativeLabel
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.KnTopBar
import com.krishinirnay.core.designsystem.components.MetricTile
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.feature.howitworks.HowItWorksDialog
import kotlin.math.roundToInt

@Composable
fun LiveMonitoringScreen(
    onNavigateToOfflineMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveMonitoringViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var showHowItWorks by remember { mutableStateOf(false) }

    if (showHowItWorks) {
        HowItWorksDialog(onDismiss = { showHowItWorks = false })
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KnTopBar(
                title = strings.monitoringTitle,
                isOnline = uiState.isOnline,
                onSyncChipClick = onNavigateToOfflineMode,
                onHelpClick = { showHowItWorks = true },
                onSettingsClick = onNavigateToSettings,
            )
        },
    ) { innerPadding ->
        LiveMonitoringContent(uiState = uiState, strings = strings, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun LiveMonitoringContent(uiState: LiveMonitoringUiState, strings: AppStrings, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        KnCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(if (uiState.isOnline) KrishiTheme.colors.riskLow else KrishiTheme.colors.riskUnknown),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = if (uiState.isOnline) strings.dashboardDeviceOnline else strings.dashboardDeviceOffline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (uiState.batteryPct != null) {
                    Icon(Icons.Rounded.BatteryStd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("${uiState.batteryPct}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.size(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                MetricTile(
                    icon = Icons.Rounded.WaterDrop,
                    value = uiState.soilMoisturePct.roundToInt().toString(),
                    unit = "%",
                    label = strings.soilMoisture,
                    accentColor = Color(0xFF2F80ED),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                MetricTile(
                    icon = Icons.Rounded.Thermostat,
                    value = uiState.temperatureC.roundToInt().toString(),
                    unit = "°C",
                    label = strings.temperature,
                    accentColor = Color(0xFFF2994A),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                MetricTile(
                    icon = Icons.Rounded.Opacity,
                    value = uiState.humidityPct.roundToInt().toString(),
                    unit = "%",
                    label = strings.humidity,
                    accentColor = Color(0xFF12A594),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                MetricTile(
                    icon = Icons.Rounded.Science,
                    value = uiState.ph?.let { "%.1f".format(it) } ?: "—",
                    unit = "",
                    label = strings.phLabel,
                    accentColor = Color(0xFF8E5FD1),
                    enabled = uiState.ph != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            text = String.format(strings.lastUpdatedTemplate, uiState.lastUpdatedAt.toRelativeLabel(strings)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}
