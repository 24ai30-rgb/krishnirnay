package com.krishinirnay.feature.insights

import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.KnButton
import com.krishinirnay.core.designsystem.components.HeroCard
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.RiskBadge
import com.krishinirnay.core.designsystem.components.RiskBadgeSize
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.strings.textFor

@Composable
fun AiInsightsScreen(
    onBack: () -> Unit,
    onNavigateToWhatIf: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiInsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = "AI Insights", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                HeroCard(modifier = Modifier.enterStagger(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RiskBadge(level = uiState.overallRisk, size = RiskBadgeSize.Compact)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${uiState.confidencePct}% confidence",
                            style = MaterialTheme.typography.labelLarge,
                            color = KrishiTheme.colors.lime,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = if (uiState.isPolished) {
                            uiState.polishedText.orEmpty()
                        } else {
                            uiState.recommendation?.let(strings::textFor) ?: strings.dashboardGatheringReading
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            item(key = "why") {
                KnCard(modifier = Modifier.enterStagger(1)) {
                    Text("WHY THIS RECOMMENDATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(10.dp))
                    uiState.reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp).padding(top = 2.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(strings.textFor(reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (uiState.isPolished) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp).padding(top = 1.dp),
                        )
                        Spacer(Modifier.size(9.dp))
                        Text(
                            text = "AI-polished explanation — falls back to the plain text above if offline.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            item(key = "whatif") {
                KnButton(
                    text = "Try What-If: Irrigation Delay",
                    onClick = onNavigateToWhatIf,
                    icon = Icons.Rounded.WaterDrop,
                    modifier = Modifier.fillMaxWidth().enterStagger(2),
                )
            }
        }
    }
}
