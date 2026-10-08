package com.krishinirnay.feature.monitoring

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import com.krishinirnay.core.designsystem.motion.pulse
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.GaugeFormat
import com.krishinirnay.core.designsystem.components.RingGauge
import com.krishinirnay.core.designsystem.components.HeroCard
import java.util.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
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
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
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
    val c = KrishiTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            HeroCard(modifier = Modifier.enterStagger(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).pulse(uiState.isOnline).clip(CircleShape)
                            .background(if (uiState.isOnline) c.lime else Color.White.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (uiState.isOnline) strings.dashboardDeviceOnline else strings.dashboardDeviceOffline,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.batteryPct != null) {
                        Icon(Icons.Rounded.BatteryStd, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("${uiState.batteryPct}%", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.size(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val heroGauge = Modifier.weight(1f)
                    RingGauge(
                        GaugeFormat.fraction(uiState.soilMoisturePct, 100f), GaugeFormat.percent(uiState.soilMoisturePct), strings.soilMoisture,
                        c.lime, heroGauge, textColor = Color.White, labelColor = Color.White.copy(alpha = 0.85f), trackColor = Color.White.copy(alpha = 0.18f),
                    )
                    RingGauge(
                        GaugeFormat.fraction(uiState.temperatureC, 50f), "${uiState.temperatureC.roundToInt()}°", strings.temperature,
                        c.lime, heroGauge, textColor = Color.White, labelColor = Color.White.copy(alpha = 0.85f), trackColor = Color.White.copy(alpha = 0.18f),
                    )
                    RingGauge(
                        GaugeFormat.fraction(uiState.humidityPct, 100f), GaugeFormat.percent(uiState.humidityPct), strings.humidity,
                        c.lime, heroGauge, textColor = Color.White, labelColor = Color.White.copy(alpha = 0.85f), trackColor = Color.White.copy(alpha = 0.18f),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = String.format(strings.lastUpdatedTemplate, uiState.lastUpdatedAt.toRelativeLabel(strings)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        item(key = "tiles1") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().enterStagger(1)) {
                MetricTile(
                    icon = Icons.Rounded.WaterDrop,
                    value = uiState.soilMoisturePct.roundToInt().toString(),
                    unit = "%",
                    label = strings.soilMoisture,
                    accentColor = c.info,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    icon = Icons.Rounded.Thermostat,
                    value = uiState.temperatureC.roundToInt().toString(),
                    unit = "°C",
                    label = strings.temperature,
                    accentColor = c.accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "tiles2") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().enterStagger(2)) {
                MetricTile(
                    icon = Icons.Rounded.Opacity,
                    value = uiState.humidityPct.roundToInt().toString(),
                    unit = "%",
                    label = strings.humidity,
                    accentColor = c.secondary,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    icon = Icons.Rounded.Science,
                    value = uiState.ph?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    unit = "",
                    label = strings.phLabel,
                    accentColor = MaterialTheme.colorScheme.primary,
                    enabled = uiState.ph != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
