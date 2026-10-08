package com.krishinirnay.feature.advisory

import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.HeroCard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Grass
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.KnTopBar
import com.krishinirnay.core.designsystem.components.RiskBadge
import com.krishinirnay.core.designsystem.components.RiskBadgeSize
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.strings.textFor
import com.krishinirnay.feature.howitworks.HowItWorksDialog

@Composable
fun CropAdvisoryScreen(
    onNavigateToOfflineMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CropAdvisoryViewModel = hiltViewModel(),
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
                title = strings.advisoryTitle,
                isOnline = uiState.isOnline,
                onSyncChipClick = onNavigateToOfflineMode,
                onHelpClick = { showHowItWorks = true },
                onSettingsClick = onNavigateToSettings,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                HeroCard(modifier = Modifier.enterStagger(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.advisoryAiAdvice.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${uiState.confidencePct}%", style = MaterialTheme.typography.labelLarge, color = KrishiTheme.colors.lime)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = uiState.aiAdvice?.let(strings::textFor) ?: strings.dashboardGatheringReading,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.size(12.dp))
                    RiskBadge(level = uiState.overallRisk, size = RiskBadgeSize.Compact)
                }
            }
            item(key = "tasks") {
                KnCard(modifier = Modifier.enterStagger(1)) {
                    Text(
                        text = strings.advisoryRecommendedTasks,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    uiState.tasks.forEach { task ->
                        TaskRow(
                            type = task.type,
                            title = taskTitle(task.type),
                            detail = task.detail,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun taskTitle(type: AdvisoryTaskType): String {
    val strings = LocalAppStrings.current
    return when (type) {
        AdvisoryTaskType.IRRIGATE -> strings.advisoryTaskIrrigate
        AdvisoryTaskType.PEST_CONTROL -> strings.advisoryTaskPestControl
        AdvisoryTaskType.FERTILIZER -> strings.advisoryTaskFertilizer
    }
}

private data class TaskIconStyle(val icon: ImageVector, val color: Color, val background: Color)

@Composable
private fun iconStyleFor(type: AdvisoryTaskType): TaskIconStyle {
    val c = KrishiTheme.colors
    return when (type) {
        AdvisoryTaskType.IRRIGATE -> TaskIconStyle(Icons.Rounded.WaterDrop, c.info, c.infoContainer)
        AdvisoryTaskType.PEST_CONTROL -> TaskIconStyle(Icons.Rounded.BugReport, c.riskHigh, c.riskHighContainer)
        AdvisoryTaskType.FERTILIZER -> TaskIconStyle(Icons.Rounded.Grass, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    }
}

@Composable
private fun TaskRow(type: AdvisoryTaskType, title: String, detail: String) {
    val style = iconStyleFor(type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(style.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(10.dp))
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
