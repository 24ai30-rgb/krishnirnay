package com.krishinirnay.feature.dashboard

import androidx.compose.material.icons.rounded.Mic
import kotlin.math.roundToInt
import com.krishinirnay.core.designsystem.strings.nameFor
import com.krishinirnay.core.designsystem.motion.pulse
import com.krishinirnay.core.designsystem.motion.pressClickable
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.SectionHeader
import com.krishinirnay.core.designsystem.components.RingGauge
import com.krishinirnay.core.designsystem.components.QuickActionTile
import com.krishinirnay.core.designsystem.components.HeroCard
import com.krishinirnay.core.designsystem.components.GaugeFormat
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.FlowRow
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
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = strings.contentDescOpenChatbot,
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
            onListen = viewModel::speakDecision,
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
    onListen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One gesture refreshes both provider-backed cards.
    val isRefreshing = uiState.weather?.status == DataSourceStatus.LOADING ||
        uiState.market?.status == DataSourceStatus.LOADING
    // Hoisted so the hero's "Mark done" jumps the feedback card straight to "What happened?".
    var feedbackAction by remember { mutableStateOf<FeedbackAction?>(null) }
    var feedbackSubmitted by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRetryWeather(); onRetryMarket() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "greeting") {
                HomeGreetingHeader(uiState, strings, Modifier.enterStagger(0))
            }

            item(key = "hero") {
                DecisionHero(
                    uiState = uiState,
                    strings = strings,
                    markedDone = feedbackAction == FeedbackAction.YES || feedbackSubmitted,
                    onMarkDone = { if (!feedbackSubmitted) feedbackAction = FeedbackAction.YES },
                    onListen = onListen,
                    onOpenInsights = onViewFullAnalysis,
                    modifier = Modifier.enterStagger(1),
                )
            }

            if (uiState.recommendation != null) {
                item(key = "feedback") {
                    KnCard(modifier = Modifier.fillMaxWidth().enterStagger(2)) {
                        FeedbackWidget(
                            strings = strings,
                            action = feedbackAction,
                            onAction = { feedbackAction = it },
                            submitted = feedbackSubmitted,
                            onSubmitted = { feedbackSubmitted = true },
                        )
                    }
                }
            }

            item(key = "gauges") {
                SensorGaugesCard(uiState, strings, onNavigateToMonitoring, Modifier.enterStagger(3))
            }

            item(key = "qa") {
                Column(modifier = Modifier.enterStagger(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(strings.dashboardQuickAccess)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionTile(Icons.Rounded.CameraAlt, strings.dashboardQaDisease, onNavigateToCropHealth, Modifier.weight(1f))
                        QuickActionTile(Icons.Rounded.BugReport, strings.dashboardQaPests, onNavigateToPestDetection, Modifier.weight(1f), tint = KrishiTheme.colors.riskHigh)
                        QuickActionTile(Icons.Rounded.Cloud, strings.navWeather, onNavigateToWeather, Modifier.weight(1f), tint = KrishiTheme.colors.info)
                        QuickActionTile(Icons.Rounded.AccountBalance, strings.dashboardQaSchemes, onNavigateToSchemes, Modifier.weight(1f), tint = KrishiTheme.colors.accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionTile(Icons.Rounded.CurrencyRupee, strings.dashboardQaMarket, onViewMoreMarkets, Modifier.weight(1f), tint = KrishiTheme.colors.accent)
                        QuickActionTile(Icons.Rounded.Spa, strings.dashboardQaAdvisory, onNavigateToAdvisory, Modifier.weight(1f))
                        QuickActionTile(Icons.Rounded.AutoAwesome, strings.dashboardQaAssistant, onNavigateToChatbot, Modifier.weight(1f), tint = KrishiTheme.colors.secondary)
                        QuickActionTile(Icons.Rounded.Agriculture, strings.dashboardQaFarmSetup, onNavigateToFarmSetup, Modifier.weight(1f), tint = KrishiTheme.colors.riskUnknown)
                    }
                }
            }

            item(key = "subrisks") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().enterStagger(5)) {
                    SubRiskCard(Icons.Rounded.WaterDrop, KrishiTheme.colors.info, KrishiTheme.colors.infoContainer, strings.dashboardWaterStress, uiState.waterStressRisk, Modifier.weight(1f))
                    SubRiskCard(Icons.Rounded.Thermostat, KrishiTheme.colors.accent, KrishiTheme.colors.accentContainer, strings.dashboardHeat, uiState.heatRisk, Modifier.weight(1f))
                    SubRiskCard(Icons.Rounded.Grass, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, strings.dashboardCropHealth, uiState.cropHealthRisk, Modifier.weight(1f))
                }
            }

            uiState.weather?.let { weather ->
                item(key = "weather") {
                    Box(Modifier.enterStagger(6)) { WeatherDetailCard(weather, uiState, strings, onNavigateToWeather, onRetryWeather) }
                }
            }

            uiState.market?.let { market ->
                item(key = "market") {
                    Box(Modifier.enterStagger(7)) { MarketDetailCard(market, uiState, strings, onRetryMarket, onNavigateToFarmSetup, onViewMoreMarkets) }
                }
            }

            uiState.fertilizerRecommendation?.let { fertilizer ->
                item(key = "fertilizer") {
                    KnCard(modifier = Modifier.fillMaxWidth().enterStagger(8)) {
                        Text(strings.dashboardFertilizerLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(4.dp))
                        Text(strings.textFor(fertilizer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (uiState.diseaseResult != null || uiState.pestResult != null) {
                item(key = "scan") {
                    KnCard(modifier = Modifier.fillMaxWidth()) {
                        Text(strings.dashboardLatestScan, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(6.dp))
                        uiState.diseaseResult?.let { disease ->
                            Text("${disease.displayName} · ${(disease.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        uiState.pestResult?.let { pest ->
                            Text(
                                text = if (pest.detected) "${pest.label ?: strings.dashboardQaPests} · ${(pest.confidence * 100).toInt()}%" else "${strings.dashboardQaPests}: —",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            item(key = "device") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).pulse(uiState.isDeviceOnline).clip(CircleShape)
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
        }
    }
}

// =================================================================
// DECISION HERO — today's one answer, on the brand gradient
// =================================================================

@Composable
private fun DecisionHero(
    uiState: DashboardUiState,
    strings: AppStrings,
    markedDone: Boolean,
    onMarkDone: () -> Unit,
    onListen: (String) -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KrishiTheme.colors
    val decision = uiState.recommendation?.let(strings::textFor) ?: strings.dashboardGatheringReading
    val reasons = uiState.reasons.take(3).map(strings::textFor)
    val crop = uiState.profile?.primaryCrop
    HeroCard(modifier = modifier.pressClickable(onClick = onOpenInsights)) {
        Text(
            text = listOfNotNull(strings.dashboardTodaysDecision, crop).joinToString(" · ").uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(6.dp))
        // Short decisions get the big display size; full-sentence ones step down so they never take 4+ lines.
        Text(
            text = decision,
            style = if (decision.length <= 32) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (uiState.recommendation != null) {
            Spacer(Modifier.size(4.dp))
            Text(strings.textFor(uiState.timing), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
        Spacer(Modifier.size(10.dp))
        Row(
            modifier = Modifier.pulse(uiState.overallRisk == RiskLevel.HIGH).clip(RoundedCornerShape(50)).background(c.lime)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (uiState.overallRisk) {
                    RiskLevel.HIGH -> Icons.Rounded.Error
                    RiskLevel.MEDIUM -> Icons.Rounded.Warning
                    RiskLevel.LOW -> Icons.Rounded.CheckCircle
                    RiskLevel.UNKNOWN -> Icons.Rounded.HelpOutline
                },
                contentDescription = null,
                tint = c.onLime,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "${strings.dashboardOverallRisk}: ${strings.nameFor(uiState.overallRisk)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = c.onLime,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (reasons.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            reasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
        }
        if (uiState.recommendation != null) {
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroPillButton(
                    icon = if (markedDone) Icons.Rounded.CheckCircle else Icons.Rounded.Check,
                    label = if (markedDone) strings.feedbackYes else strings.dashboardMarkDone,
                    container = c.lime,
                    content = c.onLime,
                    onClick = onMarkDone,
                    modifier = Modifier.weight(1f),
                )
                HeroPillButton(
                    icon = Icons.Rounded.VolumeUp,
                    label = strings.dashboardListen,
                    container = Color.White.copy(alpha = 0.16f),
                    content = Color.White,
                    onClick = { onListen((listOf(decision) + reasons).joinToString(". ")) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroPillButton(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(container).pressClickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =================================================================
// SENSOR GAUGES — three rings that sweep in; "—" when there's no reading
// =================================================================

@Composable
private fun SensorGaugesCard(uiState: DashboardUiState, strings: AppStrings, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val hasReading = uiState.dataSourceStatus !in setOf(DataSourceStatus.LOADING, DataSourceStatus.NO_DATA, DataSourceStatus.UNAVAILABLE)
    val soil = uiState.soilMoisturePct.takeIf { hasReading }
    val temp = uiState.temperatureC.takeIf { hasReading }
    val humidity = uiState.humidityPct.takeIf { hasReading }
    KnCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(strings.dashboardLiveSensors, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(8.dp))
            Spacer(Modifier.weight(1f))
            StatusBadge(uiState.dataSourceStatus, Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.size(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RingGauge(GaugeFormat.fraction(soil, 100f), GaugeFormat.percent(soil), strings.soilMoisture, riskColor(uiState.waterStressRisk), Modifier.weight(1f))
            RingGauge(GaugeFormat.fraction(temp, 50f), temp?.let { "${it.roundToInt()}°" } ?: "—", strings.temperature, riskColor(uiState.heatRisk), Modifier.weight(1f))
            RingGauge(GaugeFormat.fraction(humidity, 100f), GaugeFormat.percent(humidity), strings.humidity, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.LOW -> KrishiTheme.colors.riskLow
    RiskLevel.MEDIUM -> KrishiTheme.colors.riskMedium
    RiskLevel.HIGH -> KrishiTheme.colors.riskHigh
    RiskLevel.UNKNOWN -> MaterialTheme.colorScheme.primary
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
private fun HomeGreetingHeader(uiState: DashboardUiState, strings: AppStrings, modifier: Modifier = Modifier) {
    val profile = uiState.profile
    val name = profile?.name?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }
    val greeting = name?.let { String.format(greetingTemplateFor(strings), it) } ?: strings.dashboardGreeting
    val location = profile?.farmLocation?.let { loc ->
        listOf(loc.district, loc.state).filter { it.isNotBlank() }.joinToString(", ")
    }?.takeIf { it.isNotBlank() } ?: profile?.location
    val initial = (profile?.name?.trim()?.firstOrNull() ?: 'K').uppercaseChar()

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
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
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(KrishiTheme.colors.heroStart, KrishiTheme.colors.heroEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KrishiTheme.colors.lime)
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FeedbackWidget(
    strings: AppStrings,
    action: FeedbackAction?,
    onAction: (FeedbackAction) -> Unit,
    submitted: Boolean,
    onSubmitted: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    if (submitted) {
        Text(strings.feedbackThanks, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        return
    }
    Text(
        text = if (action == null) strings.feedbackDidYouFollow else strings.feedbackWhatHappened,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (action == null) {
            FeedbackChip(strings.feedbackYes) { onAction(FeedbackAction.YES) }
            FeedbackChip(strings.feedbackNo) { onAction(FeedbackAction.NO) }
            FeedbackChip(strings.feedbackPartially) { onAction(FeedbackAction.PARTIALLY) }
        } else {
            FeedbackChip(strings.feedbackResultImproved) { viewModel.submit(action, FeedbackResult.CROP_IMPROVED); onSubmitted() }
            FeedbackChip(strings.feedbackResultNoChange) { viewModel.submit(action, FeedbackResult.NO_CHANGE); onSubmitted() }
            FeedbackChip(strings.feedbackResultWorse) { viewModel.submit(action, FeedbackResult.CROP_WORSE); onSubmitted() }
            FeedbackChip(strings.feedbackResultOther) { viewModel.submit(action, FeedbackResult.OTHER); onSubmitted() }
        }
    }
}

@Composable
private fun FeedbackChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .pressClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
