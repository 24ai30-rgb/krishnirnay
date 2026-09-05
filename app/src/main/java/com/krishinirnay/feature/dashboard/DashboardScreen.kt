package com.krishinirnay.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.KnTopBar
import com.krishinirnay.core.designsystem.components.RiskBadge
import com.krishinirnay.core.designsystem.components.RiskBadgeSize
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.feature.howitworks.HowItWorksDialog

@Composable
fun DashboardScreen(
    onNavigateToInsights: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onNavigateToOfflineMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdvisory: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToSchemes: () -> Unit,
    onNavigateToCropHealth: () -> Unit,
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
                title = strings.dashboardGreeting,
                isOnline = uiState.isDeviceOnline,
                onSyncChipClick = onNavigateToOfflineMode,
                onHelpClick = { showHowItWorks = true },
                onSettingsClick = onNavigateToSettings,
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
            modifier = Modifier.padding(innerPadding),
        )
    }
}

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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        // =========================================================
        // OVERALL RISK
        // =========================================================
        item {
            KnCard {
                Text(
                    text = strings.dashboardOverallRisk,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.size(12.dp))

                RiskBadge(
                    level = uiState.overallRisk,
                    size = RiskBadgeSize.Hero,
                )

                Spacer(Modifier.size(12.dp))

                Text(
                    text = uiState.recommendation?.let(strings::textFor)
                        ?: strings.dashboardGatheringReading,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                TextButton(
                    onClick = onViewFullAnalysis,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = strings.dashboardViewFullAnalysis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
                SubRiskCard(
                    icon = Icons.Rounded.WaterDrop,
                    iconColor = Color(0xFF2F80ED),
                    iconBg = Color(0xFFE4EFFD),
                    label = strings.dashboardWaterStress,
                    risk = uiState.waterStressRisk,
                    modifier = Modifier.weight(1f),
                )

                SubRiskCard(
                    icon = Icons.Rounded.Thermostat,
                    iconColor = Color(0xFFF2994A),
                    iconBg = Color(0xFFFDECDD),
                    label = strings.dashboardHeat,
                    risk = uiState.heatRisk,
                    modifier = Modifier.weight(1f),
                )

                SubRiskCard(
                    icon = Icons.Rounded.Grass,
                    iconColor = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    label = strings.dashboardCropHealth,
                    risk = uiState.cropHealthRisk,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // =========================================================
        // LIVE SENSOR DATA
        // =========================================================
        item {
            KnCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(Modifier.size(8.dp))

                    Text(
                        text = "Live Sensor Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.size(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SensorValueCard(
                        icon = Icons.Rounded.Thermostat,
                        label = "Temperature",
                        value = String.format(
                            "%.1f°C",
                            uiState.temperatureC,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    SensorValueCard(
                        icon = Icons.Rounded.WaterDrop,
                        label = "Humidity",
                        value = String.format(
                            "%.1f%%",
                            uiState.humidityPct,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    SensorValueCard(
                        icon = Icons.Rounded.Grass,
                        label = "Soil Moisture",
                        value = String.format(
                            "%.1f%%",
                            uiState.soilMoisturePct,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.size(10.dp))

                Text(
                    text = "ESP32 • Live readings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // =========================================================
        // DISEASE DETECTION
        // =========================================================
        item {
            KnCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToCropHealth),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Disease Detection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Spacer(Modifier.size(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Disease Detection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(Modifier.size(3.dp))

                        Text(
                            text = "Scan crop leaves using AI",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.size(2.dp))

                        Text(
                            text = "Detect crop diseases & check leaf health",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // =========================================================
        // QUICK ACCESS TITLE
        // =========================================================
        item {
            Text(
                text = strings.dashboardQuickAccess,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // =========================================================
        // QUICK ACCESS
        // =========================================================
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                QuickAccessItem(
                    icon = Icons.Rounded.Spa,
                    label = strings.dashboardQaAdvisory,
                    onClick = onNavigateToAdvisory,
                    modifier = Modifier.weight(1f),
                )

                QuickAccessItem(
                    icon = Icons.Rounded.Cloud,
                    label = strings.navWeather,
                    onClick = onNavigateToWeather,
                    modifier = Modifier.weight(1f),
                )

                QuickAccessItem(
                    icon = Icons.Rounded.Sensors,
                    label = strings.dashboardQaMonitoring,
                    onClick = onNavigateToMonitoring,
                    modifier = Modifier.weight(1f),
                )

                QuickAccessItem(
                    icon = Icons.Rounded.AccountBalance,
                    label = strings.dashboardQaSchemes,
                    onClick = onNavigateToSchemes,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // =========================================================
        // DEVICE STATUS
        // =========================================================
        item {
            KnCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isDeviceOnline) {
                                    KrishiTheme.colors.riskLow
                                } else {
                                    KrishiTheme.colors.riskUnknown
                                },
                            ),
                    )

                    Spacer(Modifier.size(10.dp))

                    Text(
                        text = if (uiState.isDeviceOnline) {
                            strings.dashboardDeviceOnline
                        } else {
                            strings.dashboardDeviceOffline
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = String.format(
                            strings.dashboardSyncedTemplate,
                            uiState.lastSyncedAt.toRelativeLabel(strings),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Bottom spacer for FAB
        item {
            Spacer(Modifier.size(64.dp))
        }
    }
}

// =================================================================
// SENSOR VALUE CARD
// =================================================================

@Composable
private fun SensorValueCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(KrishiTheme.colors.surfaceAlt)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.size(6.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(3.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// =================================================================
// QUICK ACCESS ITEM
// =================================================================

@Composable
private fun QuickAccessItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }

    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "quickAccessPress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(KrishiTheme.colors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.size(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// =================================================================
// SUB RISK CARD
// =================================================================

@Composable
private fun SubRiskCard(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    label: String,
    risk: RiskLevel,
    modifier: Modifier = Modifier,
) {
    KnCard(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.size(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.size(6.dp))

            RiskBadge(
                level = risk,
                size = RiskBadgeSize.Compact,
            )
        }
    }
}