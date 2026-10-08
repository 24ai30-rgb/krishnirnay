package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

/**
 * The one place [DataSourceStatus] becomes a color + label — every screen
 * that shows weather/market/sensor freshness (Dashboard, Weather, Market,
 * Sensors) reads this instead of re-deriving its own color/text mapping, so
 * "what does CACHED look like" only has one answer app-wide. Never invents
 * a status — always reflects the real [DataSourceStatus] passed in.
 */
@Composable
fun StatusBadge(status: DataSourceStatus, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    val colors = KrishiTheme.colors
    val (dotColor, label) = when (status) {
        DataSourceStatus.LIVE -> colors.riskLow to strings.dataSourceLive
        DataSourceStatus.CACHED -> colors.accent to strings.dataSourceCached
        DataSourceStatus.MOCK -> colors.info to strings.dataSourceMock
        DataSourceStatus.LOADING -> colors.riskUnknown to strings.dataSourceLoading
        DataSourceStatus.NO_DATA -> colors.riskUnknown to strings.dataSourceNoData
        DataSourceStatus.UNAVAILABLE -> colors.riskUnknown to strings.dataSourceUnavailable
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceAlt)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Plain color (no chip background) for callers embedding the label inline in existing text. */
@Composable
fun dataSourceColor(status: DataSourceStatus): Color = when (status) {
    DataSourceStatus.LIVE -> KrishiTheme.colors.riskLow
    DataSourceStatus.CACHED -> KrishiTheme.colors.accent
    DataSourceStatus.MOCK -> KrishiTheme.colors.info
    DataSourceStatus.LOADING, DataSourceStatus.NO_DATA -> KrishiTheme.colors.riskUnknown
    DataSourceStatus.UNAVAILABLE -> KrishiTheme.colors.riskUnknown
}

@Composable
fun dataSourceLabel(status: DataSourceStatus): String {
    val strings = LocalAppStrings.current
    return when (status) {
        DataSourceStatus.LIVE -> strings.dataSourceLive
        DataSourceStatus.CACHED -> strings.dataSourceCached
        DataSourceStatus.MOCK -> strings.dataSourceMock
        DataSourceStatus.LOADING -> strings.dataSourceLoading
        DataSourceStatus.NO_DATA -> strings.dataSourceNoData
        DataSourceStatus.UNAVAILABLE -> strings.dataSourceUnavailable
    }
}
