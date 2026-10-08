package com.krishinirnay.feature.alerts

import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.RiskBadgeSize
import com.krishinirnay.core.designsystem.components.RiskBadge
import com.krishinirnay.core.designsystem.components.AnimatedNumber
import com.krishinirnay.core.designsystem.components.HeroCard
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.common.toRelativeLabel
import com.krishinirnay.core.data.model.Alert
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.designsystem.components.EmptyState
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.alertsTitle, onBack = onBack) },
    ) { innerPadding ->
        if (alerts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Notifications,
                    message = strings.alertsEmpty,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero") {
                    val highCount = alerts.count { it.riskLevel == RiskLevel.HIGH }
                    HeroCard(modifier = Modifier.enterStagger(0)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNumber(
                                target = (if (highCount > 0) highCount else alerts.size).toFloat(),
                                format = { it.roundToInt().toString() },
                                style = MaterialTheme.typography.displayMedium,
                                color = KrishiTheme.colors.lime,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = strings.alertsTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (highCount > 0) RiskBadge(level = RiskLevel.HIGH, size = RiskBadgeSize.Compact)
                        }
                    }
                }
                itemsIndexed(alerts, key = { _, a -> a.id }, contentType = { _, _ -> "alert" }) { index, alert ->
                    AlertRow(alert, Modifier.enterStagger(index + 1))
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    KnCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            val (bg, tint) = riskColors(alert.riskLevel)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.size(11.dp))
            Column {
                Text(alert.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = alert.timestamp.toRelativeLabel(strings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun riskColors(risk: RiskLevel): Pair<Color, Color> {
    val colors = KrishiTheme.colors
    return when (risk) {
        RiskLevel.LOW -> colors.riskLowContainer to colors.riskLow
        RiskLevel.MEDIUM -> colors.riskMediumContainer to colors.riskMedium
        RiskLevel.HIGH -> colors.riskHighContainer to colors.riskHigh
        RiskLevel.UNKNOWN -> colors.riskUnknownContainer to colors.riskUnknown
    }
}
