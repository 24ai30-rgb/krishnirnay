package com.krishinirnay.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.common.toRelativeLabel
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.FeedbackAction
import com.krishinirnay.core.data.model.FeedbackResult
import com.krishinirnay.core.data.model.MarketMatchLevel
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.matchLevel
import com.krishinirnay.core.designsystem.components.KnButton
import com.krishinirnay.core.designsystem.components.KnButtonStyle
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.KnTopBar
import com.krishinirnay.core.designsystem.components.RiskBadge
import com.krishinirnay.core.designsystem.components.RiskBadgeSize
import com.krishinirnay.core.designsystem.components.StatusBadge
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.feature.feedback.FeedbackViewModel
import com.krishinirnay.feature.howitworks.HowItWorksDialog

@Composable
fun DashboardScreen(
    onNavigateToInsights: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onNavigateToOfflineMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToAdvisory: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToSchemes: () -> Unit,
    onNavigateToCropHealth: () -> Unit,
    onNavigateToPestDetection: () -> Unit,
    onNavigateToFarmSetup: () -> Unit,
    onViewMoreMarkets: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var showHowItWorks by remember { mutableStateOf(false) }

    if (showHowItWorks) {
        HowItWorksDialog {
            showHowItWorks = false
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KnTopBar(
                // The rich, personalized greeting (name + avatar + location)
                // now lives in HomeGreetingHeader below — this stays a short,
                // fixed section title so it never wraps/overlaps the action
                // icons the way the full "Namaste, Farmer!" string could.
                title = strings.navHome,
                isOnline = uiState.isDeviceOnline,
                onSyncChipClick = onNavigateToOfflineMode,
                onHelpClick = { showHowItWorks = true },
                onSettingsClick = onNavigateToSettings,
                onAlertsClick = onNavigateToAlerts,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToChatbot,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = strings.contentDescOpenChatbot,
                    tint = Color.White,
                )
            }
        },
    ) { innerPadding ->

        DashboardContent(
            uiState = uiState,
            strings = strings,
            onViewFullAnalysis = onNavigateToInsights,
            onNavigateToAdvisory = onNavigateToAdvisory,
            onNavigateToWeather = onNavigateToWeather,
            onNavigateToMonitoring = onNavigateToMonitoring,
            onNavigateToSchemes = onNavigateToSchemes,
            onNavigateToCropHealth = onNavigateToCropHealth,
            onNavigateToPestDetection = onNavigateToPestDetection,
            onRetryWeather = viewModel::retryWeather,
            onRetryMarket = viewModel::retryMarket,
            onNavigateToFarmSetup = onNavigateToFarmSetup,
            onViewMoreMarkets = onViewMoreMarkets,
            onNavigateToChatbot = onNavigateToChatbot,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    strings: AppStrings,
    onViewFullAnalysis: () -> Unit,
    onNavigateToAdvisory: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToSchemes: () -> Unit,
    onNavigateToCropHealth: () -> Unit,
    onNavigateToPestDetection: () -> Unit,
    onRetryWeather: () -> Unit,
    onRetryMarket: () -> Unit,
    onNavigateToFarmSetup: () -> Unit,
    onViewMoreMarkets: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One gesture refreshes both cards — they're the two provider-backed
    // readings on this screen, and a farmer pulling to refresh means "get me
    // the latest", not "just the one I happen to be looking at".
    val isRefreshing = uiState.weather?.status == DataSourceStatus.LOADING ||
        uiState.market?.status == DataSourceStatus.LOADING
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRetryWeather(); onRetryMarket() },
        modifier = modifier.fillMaxSize(),
    ) {
        // A single, one-time entrance for the above-the-fold content only —
        // not per LazyColumn item, which would cost a recomposition/measure
        // pass on every card during scroll for no visible benefit once the
        // screen has already appeared once.
        var entered by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // =========================================================
            // GREETING HEADER
            // =========================================================
            item {
                AnimatedVisibility(visible = entered, enter = fadeIn(tween(280))) {
                    HomeGreetingHeader(uiState, strings)
                }
            }

            // =========================================================
            // FARM TODAY — the one summary that answers "how is my farm
            // doing today": crop/location, weather glance, overall risk +
            // recommendation, market glance. Full weather/market detail
            // still lives in their own cards further down.
            // =========================================================
            item {
                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 12 },
                ) {
                    FarmTodayCard(
                        uiState = uiState,
                        strings = strings,
                        onViewFullAnalysis = onViewFullAnalysis,
                        onOpenWeather = onNavigateToWeather,
                        onOpenMarket = onViewMoreMarkets,
                    )
                }
            }

            // =========================================================
            // QUICK ACTIONS — the 5 places a farmer goes most.
            // =========================================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.dashboardQuickAccess,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionTile(Icons.Rounded.Spa, strings.dashboardQaAdvisory, KrishiTheme.colors.riskLow, KrishiTheme.colors.riskLowContainer, onNavigateToAdvisory, Modifier.weight(1f))
                        QuickActionTile(Icons.Rounded.Cloud, strings.navWeather, KrishiTheme.colors.info, KrishiTheme.colors.infoContainer, onNavigateToWeather, Modifier.weight(1f))
                        QuickActionTile(Icons.Rounded.CurrencyRupee, strings.dashboardQaMarket, KrishiTheme.colors.accent, KrishiTheme.colors.accentContainer, onViewMoreMarkets, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionTile(Icons.Rounded.CameraAlt, strings.dashboardQaDisease, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, onNavigateToCropHealth, Modifier.weight(1f))
                        QuickActionTile(Icons.Rounded.AutoAwesome, strings.dashboardQaAssistant, KrishiTheme.colors.secondary, KrishiTheme.colors.secondaryContainer, onNavigateToChatbot, Modifier.weight(1f))
                        QuickActionTile(Icons.Rounded.Agriculture, strings.dashboardQaFarmSetup, KrishiTheme.colors.riskUnknown, KrishiTheme.colors.riskUnknownContainer, onNavigateToFarmSetup, Modifier.weight(1f))
                    }
                }
            }

            // =========================================================
            // WEATHER — full detail (kept distinct from the Farm Today
            // glance; this is what LIVE/CACHED/UNAVAILABLE, humidity, wind,
            // UV etc. actually live in).
            // =========================================================
            uiState.weather?.let { weather ->
                item {
                    WeatherDetailCard(weather, uiState, strings, onNavigateToWeather, onRetryWeather)
                }
            }

            // =========================================================
            // MARKET — full detail.
            // =========================================================
            uiState.market?.let { market ->
                item {
                    MarketDetailCard(market, uiState, strings, onRetryMarket, onNavigateToFarmSetup, onViewMoreMarkets)
                }
            }

            // =========================================================
            // SUB RISKS
            // =========================================================
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SubRiskCard(Icons.Rounded.WaterDrop, Color(0xFF2F80ED), Color(0xFFE4EFFD), strings.dashboardWaterStress, uiState.waterStressRisk, Modifier.weight(1f))
                    SubRiskCard(Icons.Rounded.Thermostat, Color(0xFFF2994A), Color(0xFFFDECDD), strings.dashboardHeat, uiState.heatRisk, Modifier.weight(1f))
                    SubRiskCard(Icons.Rounded.Grass, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, strings.dashboardCropHealth, uiState.cropHealthRisk, Modifier.weight(1f))
                }
            }

            // =========================================================
            // LIVE SENSOR DATA
            // =========================================================
            item {
                KnCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Live Sensor Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SensorValueCard(Icons.Rounded.Thermostat, "Temperature", String.format("%.1f°C", uiState.temperatureC), Modifier.weight(1f))
                        SensorValueCard(Icons.Rounded.WaterDrop, "Humidity", String.format("%.1f%%", uiState.humidityPct), Modifier.weight(1f))
                        SensorValueCard(Icons.Rounded.Grass, "Soil Moisture", String.format("%.1f%%", uiState.soilMoisturePct), Modifier.weight(1f))
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = when (uiState.dataSourceStatus) {
                            DataSourceStatus.LIVE -> "ESP32 • Live readings"
                            DataSourceStatus.CACHED -> "Device unreachable • Showing last known reading"
                            DataSourceStatus.MOCK -> "Demo mode • Simulated readings"
                            DataSourceStatus.LOADING -> "Connecting to sensors..."
                            DataSourceStatus.NO_DATA, DataSourceStatus.UNAVAILABLE -> "Sensor data not available yet"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.dataSourceStatus == DataSourceStatus.CACHED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // =========================================================
            // CROP HEALTH SCAN — Disease + Pest folded into one card
            // (previously two full-width promotional cards).
            // =========================================================
            item {
                KnCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Crop Health Scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.size(3.dp))
                    Text("Scan a leaf with your camera to check for disease or pests", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ScanChip(Icons.Rounded.CameraAlt, "Disease", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, onNavigateToCropHealth, Modifier.weight(1f))
                        ScanChip(Icons.Rounded.BugReport, "Pests", Color(0xFFD64545), Color(0xFFFBE3E3), onNavigateToPestDetection, Modifier.weight(1f))
                    }
                }
            }

            // =========================================================
            // CROP HEALTH STATUS (real scan results, if any)
            // =========================================================
            if (uiState.diseaseResult != null || uiState.pestResult != null) {
                item {
                    KnCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Latest scan results", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(6.dp))
                        uiState.diseaseResult?.let { disease ->
                            Text("Disease: ${disease.displayName} (${(disease.confidence * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        uiState.pestResult?.let { pest ->
                            Text(
                                text = if (pest.detected) "Pest: ${pest.label ?: "detected"} (${(pest.confidence * 100).toInt()}%)" else "Pest: none detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // =========================================================
            // DEVICE STATUS — a slim strip, not a full padded card.
            // =========================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (uiState.isDeviceOnline) KrishiTheme.colors.riskLow else KrishiTheme.colors.riskUnknown),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (uiState.isDeviceOnline) strings.dashboardDeviceOnline else strings.dashboardDeviceOffline,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = String.format(strings.dashboardSyncedTemplate, uiState.lastSyncedAt.toRelativeLabel(strings)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Bottom spacer for FAB
            item { Spacer(Modifier.size(64.dp)) }
        }
    }
}

// =================================================================
// FARM TODAY — the flagship summary card
// =================================================================

@Composable
private fun FarmTodayCard(
    uiState: DashboardUiState,
    strings: AppStrings,
    onViewFullAnalysis: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenMarket: () -> Unit,
) {
    KnCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.dashboardOverallRisk, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                RiskBadge(level = uiState.overallRisk, size = RiskBadgeSize.Hero)
            }
            // Weather-at-a-glance — tappable straight through to the full
            // Weather screen. Only shown once real data (not LOADING/
            // UNAVAILABLE) exists, never a placeholder temperature. Kept to
            // icon+temp only (full detail — rain%, humidity, wind — lives in
            // the dedicated Weather card below): the more this glance takes,
            // the less room the primary risk badge gets on a narrow/scaled
            // display, which is exactly what pushed "Medium" into ellipsis
            // before this was trimmed down.
            uiState.weather?.takeIf { it.status != DataSourceStatus.LOADING && it.status != DataSourceStatus.UNAVAILABLE }?.let { weather ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onOpenWeather),
                ) {
                    Icon(Icons.Rounded.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("${weather.currentTempC}°C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.size(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.size(14.dp))

        Text(
            text = uiState.recommendation?.let(strings::textFor) ?: strings.dashboardGatheringReading,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (uiState.recommendation != null) {
            Spacer(Modifier.size(8.dp))
            Text("When: ${strings.textFor(uiState.timing)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings.textFor(uiState.expectedBenefit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (uiState.reasons.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Text(strings.dashboardWhyLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            uiState.reasons.take(3).forEach { reason ->
                Text("• ${strings.textFor(reason)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        uiState.fertilizerRecommendation?.let { fertilizer ->
            Spacer(Modifier.size(10.dp))
            Text(strings.dashboardFertilizerLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings.textFor(fertilizer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }

        // Market-at-a-glance — real price only, tappable through to Market.
        uiState.market?.takeIf { it.status == DataSourceStatus.LIVE || it.status == DataSourceStatus.CACHED }
            ?.takeIf { it.currentPricePerQuintal != null }
            ?.let { market ->
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenMarket)
                        .background(KrishiTheme.colors.surfaceAlt).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, tint = KrishiTheme.colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "${market.crop}: ₹${market.currentPricePerQuintal!!.toInt()}/quintal" + (market.market?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

        KnButton(
            text = strings.dashboardViewFullAnalysis,
            onClick = onViewFullAnalysis,
            style = KnButtonStyle.Text,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (uiState.recommendation != null) {
            Spacer(Modifier.size(4.dp))
            FeedbackWidget(strings)
        }
    }
}

// =================================================================
// WEATHER DETAIL CARD
// =================================================================

@Composable
private fun WeatherDetailCard(
    weather: com.krishinirnay.core.data.model.WeatherState,
    uiState: DashboardUiState,
    strings: AppStrings,
    onNavigateToWeather: () -> Unit,
    onRetryWeather: () -> Unit,
) {
    KnCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToWeather)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(strings.weatherTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (weather.status == DataSourceStatus.LIVE || weather.status == DataSourceStatus.CACHED) {
                StatusBadge(status = weather.status)
            }
        }
        Spacer(Modifier.size(8.dp))
        when (weather.status) {
            DataSourceStatus.LOADING -> Text(strings.weatherLoading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DataSourceStatus.UNAVAILABLE -> {
                val hasLocation = uiState.profile?.farmLocation?.isUsable() == true
                Text(
                    text = if (hasLocation) strings.weatherFetchFailed else strings.weatherNoLocation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasLocation) {
                    Spacer(Modifier.size(8.dp))
                    KnButton(strings.actionRetry, onRetryWeather, style = KnButtonStyle.Secondary)
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${weather.currentTempC}°C", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.size(8.dp))
                    Text(conditionLabel(weather.condition, strings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(4.dp))
                val statParts = buildList {
                    weather.feelsLikeC?.let { add("${strings.weatherFeelsLike} ${it}°C") }
                    add("${strings.humidity} ${weather.humidityPct}%")
                    add("${strings.weatherWind} ${weather.windKph} km/h")
                    add("${strings.weatherRainChance} ${weather.rainChancePct}%")
                    weather.uvIndex?.let { add("${strings.weatherUvIndex} ${it.toInt()}") }
                }
                Text(statParts.joinToString("  •  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// =================================================================
// MARKET DETAIL CARD
// =================================================================

@Composable
private fun MarketDetailCard(
    market: com.krishinirnay.core.data.model.MarketState,
    uiState: DashboardUiState,
    strings: AppStrings,
    onRetryMarket: () -> Unit,
    onNavigateToFarmSetup: () -> Unit,
    onViewMoreMarkets: () -> Unit,
) {
    KnCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(strings.marketTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.size(8.dp))
        when {
            market.status == DataSourceStatus.LOADING -> Text(strings.marketLoading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            market.status == DataSourceStatus.NO_DATA -> {
                Text(strings.marketNoDataToday, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KnButton(strings.actionRetry, onRetryMarket, style = KnButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    KnButton(strings.marketChangeCropOrLocation, onNavigateToFarmSetup, style = KnButtonStyle.Text)
                }
            }
            market.status == DataSourceStatus.UNAVAILABLE || market.currentPricePerQuintal == null -> {
                Text(strings.marketFetchFailed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KnButton(strings.actionRetry, onRetryMarket, style = KnButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    KnButton(strings.marketChangeCropOrLocation, onNavigateToFarmSetup, style = KnButtonStyle.Text)
                }
            }
            else -> {
                Text(strings.marketLatestAvailable, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${market.crop}: ₹${market.currentPricePerQuintal.toInt()} / quintal" + (market.market?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                market.location?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                listOfNotNull(market.variety, market.grade).takeIf { it.isNotEmpty() }?.let { details ->
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    market.minPricePerQuintal?.let { Text("${strings.marketMinLabel} ₹${it.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    market.maxPricePerQuintal?.let { Text("${strings.marketMaxLabel} ₹${it.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    marketTrendLabel(market.trend, strings)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                market.arrivalDate?.let { Text("${strings.marketDateLabel}: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(status = market.status)
                    val farmerDistrict = uiState.profile?.farmLocation?.district.orEmpty()
                    when (market.matchLevel(farmerDistrict)) {
                        MarketMatchLevel.SAME_DISTRICT -> Text(strings.marketSameDistrict, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        MarketMatchLevel.SAME_STATE -> Text(strings.marketSameState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MarketMatchLevel.UNKNOWN -> Unit
                    }
                }
                if (market.markets.size > 1) {
                    Spacer(Modifier.size(8.dp))
                    Text(strings.marketOtherMandis, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    market.markets.filter { it.market != market.market }.take(3).forEach { mandi ->
                        Text("${mandi.market}: ₹${mandi.modalPricePerQuintal?.toInt() ?: "-"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    KnButton(strings.marketViewMoreMarkets, onViewMoreMarkets, style = KnButtonStyle.Text)
                }
            }
        }
    }
}

// =================================================================
// SENSOR VALUE CARD
// =================================================================

@Composable
private fun SensorValueCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(KrishiTheme.colors.surfaceAlt).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(6.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.size(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// =================================================================
// QUICK ACTION TILE — color-coded, larger touch target than before.
// =================================================================

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    iconColor: Color,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f, label = "quickActionPress")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun ScanChip(icon: ImageVector, label: String, iconColor: Color, iconBg: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(iconBg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = iconColor, fontWeight = FontWeight.SemiBold)
    }
}

// =================================================================
// SUB RISK CARD
// =================================================================

@Composable
private fun SubRiskCard(icon: ImageVector, iconColor: Color, iconBg: Color, label: String, risk: RiskLevel, modifier: Modifier = Modifier) {
    KnCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.size(6.dp))
            RiskBadge(level = risk, size = RiskBadgeSize.Compact)
        }
    }
}

// =================================================================
// GREETING HEADER
// =================================================================

/**
 * Time-of-day greeting + real saved name/location + an avatar initial +
 * an honest live/last-available status. The greeting bucket comes from the
 * device clock — real, not fabricated — never from a guessed farm condition.
 */
@Composable
private fun HomeGreetingHeader(uiState: DashboardUiState, strings: AppStrings) {
    val profile = uiState.profile
    val name = profile?.name?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }
    val greeting = name?.let { String.format(greetingTemplateFor(strings), it) } ?: strings.dashboardGreeting
    val location = profile?.farmLocation?.let { loc ->
        listOf(loc.district, loc.state).filter { it.isNotBlank() }.joinToString(", ")
    }?.takeIf { it.isNotBlank() } ?: profile?.location
    val initial = (profile?.name?.trim()?.firstOrNull() ?: 'K').uppercaseChar()

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            if (!location.isNullOrBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            val isAnythingLive = uiState.weather?.status == DataSourceStatus.LIVE || uiState.market?.status == DataSourceStatus.LIVE
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (isAnythingLive) KrishiTheme.colors.riskLow else KrishiTheme.colors.riskUnknown))
                Text(
                    text = if (isAnythingLive) strings.dashboardLiveToday else strings.dashboardShowingLastAvailable,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile != null && profile.primaryCrop != null) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "${profile.primaryCrop} • ${formatAcres(profile.farmSizeAcres)} acres • ${irrigationLabel(profile.irrigationMethod)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun conditionLabel(condition: com.krishinirnay.core.data.model.WeatherCondition, strings: AppStrings): String =
    when (condition) {
        com.krishinirnay.core.data.model.WeatherCondition.SUNNY -> strings.weatherConditionSunny
        com.krishinirnay.core.data.model.WeatherCondition.PARTLY_CLOUDY -> strings.weatherConditionPartlyCloudy
        com.krishinirnay.core.data.model.WeatherCondition.CLOUDY -> strings.weatherConditionCloudy
        com.krishinirnay.core.data.model.WeatherCondition.RAIN -> strings.weatherConditionRain
        com.krishinirnay.core.data.model.WeatherCondition.STORM -> strings.weatherConditionStorm
    }

private fun greetingTemplateFor(strings: AppStrings): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> strings.dashboardGreetingMorning
        hour < 17 -> strings.dashboardGreetingAfternoon
        else -> strings.dashboardGreetingEvening
    }
}

/** Drops a trailing ".0" for whole-number acreage (e.g. 2f -> "2" not "2.0") — same convention as ProfileScreen. */
private fun formatAcres(acres: Float): String =
    if (acres == acres.toInt().toFloat()) acres.toInt().toString() else acres.toString()

private fun irrigationLabel(method: com.krishinirnay.core.data.model.IrrigationMethod): String = when (method) {
    com.krishinirnay.core.data.model.IrrigationMethod.RAIN_FED -> "Rain-fed"
    com.krishinirnay.core.data.model.IrrigationMethod.IRRIGATED -> "Irrigated"
    com.krishinirnay.core.data.model.IrrigationMethod.DRIP -> "Drip"
    com.krishinirnay.core.data.model.IrrigationMethod.SPRINKLER -> "Sprinkler"
    com.krishinirnay.core.data.model.IrrigationMethod.OTHER -> "Other"
}

/**
 * "Did you follow this recommendation?" -> "What happened?" -> stored via
 * FeedbackRepository (see FeedbackViewModel) — never alters DecisionEngine's
 * rules. Result options are deliberately simplified to 4 farmer-friendly
 * buckets (kept simple per the farmer-first UI principle) while still
 * recording a real FeedbackResult value, never a fabricated one.
 */
@Composable
private fun FeedbackWidget(strings: AppStrings, viewModel: FeedbackViewModel = hiltViewModel()) {
    var action by remember { mutableStateOf<FeedbackAction?>(null) }
    var submitted by remember { mutableStateOf(false) }

    if (submitted) {
        Text(strings.feedbackThanks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        return
    }

    Spacer(Modifier.size(8.dp))
    Text(
        text = if (action == null) strings.feedbackDidYouFollow else strings.feedbackWhatHappened,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (action == null) {
            FeedbackChip(strings.feedbackYes) { action = FeedbackAction.YES }
            FeedbackChip(strings.feedbackNo) { action = FeedbackAction.NO }
            FeedbackChip(strings.feedbackPartially) { action = FeedbackAction.PARTIALLY }
        } else {
            val chosenAction = action!!
            FeedbackChip(strings.feedbackResultImproved) { viewModel.submit(chosenAction, FeedbackResult.CROP_IMPROVED); submitted = true }
            FeedbackChip(strings.feedbackResultNoChange) { viewModel.submit(chosenAction, FeedbackResult.NO_CHANGE); submitted = true }
            FeedbackChip(strings.feedbackResultWorse) { viewModel.submit(chosenAction, FeedbackResult.CROP_WORSE); submitted = true }
            FeedbackChip(strings.feedbackResultOther) { viewModel.submit(chosenAction, FeedbackResult.OTHER); submitted = true }
        }
    }
}

@Composable
private fun FeedbackChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Null for UNKNOWN — a trend the provider couldn't determine is left unshown, never guessed. */
private fun marketTrendLabel(trend: MarketTrend, strings: AppStrings): String? = when (trend) {
    MarketTrend.RISING -> strings.marketTrendRising
    MarketTrend.FALLING -> strings.marketTrendFalling
    MarketTrend.STABLE -> strings.marketTrendStable
    MarketTrend.UNKNOWN -> null
}
