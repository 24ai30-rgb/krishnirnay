package com.krishinirnay.feature.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.common.toRelativeLabel
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.MandiPrice
import com.krishinirnay.core.data.model.MarketMatchLevel
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.MarketTrend
import com.krishinirnay.core.data.model.matchLevel
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.EmptyState
import com.krishinirnay.core.designsystem.components.KnButton
import com.krishinirnay.core.designsystem.components.KnButtonStyle
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.StatusBadge
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

/**
 * The full-detail counterpart to the Dashboard's Market card: every real
 * mandi the provider returned for the farmer's crop/state (never just the
 * top 3), each labeled with the same [MarketMatchLevel] the Dashboard shows —
 * never a fabricated price, never another state's data (the server already
 * enforces that; see `market_provider.py`'s `_same_state`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onBack: () -> Unit,
    onChangeCropOrLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MarketViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val market = uiState.market

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.marketTitle, onBack = onBack) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = market?.status == DataSourceStatus.LOADING,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when {
                market == null || market.status == DataSourceStatus.LOADING -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    item { Text(strings.marketLoading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                market.status == DataSourceStatus.NO_DATA -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.CurrencyRupee,
                            message = String.format(strings.marketNoDataTodayTemplate, market.crop, market.state.orEmpty()),
                        )
                    }
                    item { KnButton(strings.actionRetry, viewModel::refresh, style = KnButtonStyle.Secondary, modifier = Modifier.fillMaxWidth()) }
                    item { KnButton(strings.marketChangeCropOrLocation, onChangeCropOrLocation, style = KnButtonStyle.Text, modifier = Modifier.fillMaxWidth()) }
                }

                market.status == DataSourceStatus.UNAVAILABLE || market.currentPricePerQuintal == null -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { EmptyState(icon = Icons.Rounded.CurrencyRupee, message = strings.marketFetchFailed) }
                    item { KnButton(strings.actionRetry, viewModel::refresh, style = KnButtonStyle.Secondary, modifier = Modifier.fillMaxWidth()) }
                    item { KnButton(strings.marketChangeCropOrLocation, onChangeCropOrLocation, style = KnButtonStyle.Text, modifier = Modifier.fillMaxWidth()) }
                }

                else -> MarketContent(market, uiState.farmerDistrict, strings, onChangeCropOrLocation, viewModel::refresh)
            }
        }
    }
}

@Composable
private fun MarketContent(
    market: MarketState,
    farmerDistrict: String,
    strings: AppStrings,
    onChangeCropOrLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            KnCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.marketBestAvailable, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    StatusBadge(status = market.status)
                }
                Spacer(Modifier.size(6.dp))
                Text(market.crop, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${market.currentPricePerQuintal?.toInt()}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(strings.marketPerQuintal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    trendIconFor(market.trend)?.let { icon ->
                        Spacer(Modifier.size(8.dp))
                        Icon(icon, contentDescription = marketTrendLabel(market.trend, strings), tint = trendColorFor(market.trend), modifier = Modifier.size(22.dp))
                    }
                }
                marketTrendLabel(market.trend, strings)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = trendColorFor(market.trend))
                }
                Spacer(Modifier.size(10.dp))
                market.market?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
                market.location?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                listOfNotNull(market.variety, market.grade).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    market.minPricePerQuintal?.let { Text("${strings.marketMinLabel} ₹${it.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    market.maxPricePerQuintal?.let { Text("${strings.marketMaxLabel} ₹${it.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    market.averagePricePerQuintal?.let { Text("${strings.marketAvgLabel} ₹${it.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                market.arrivalDate?.let {
                    Spacer(Modifier.size(4.dp))
                    Text("${strings.marketDateLabel}: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                market.fetchedAt?.let {
                    Text(String.format(strings.marketLastUpdatedTemplate, it.toRelativeLabel(strings)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(8.dp))
                when (market.matchLevel(farmerDistrict)) {
                    MarketMatchLevel.SAME_DISTRICT -> Text(strings.marketSameDistrict, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    MarketMatchLevel.SAME_STATE -> Text(strings.marketSameState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MarketMatchLevel.UNKNOWN -> Unit
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KnButton(strings.actionRetry, onRefresh, style = KnButtonStyle.Secondary, modifier = Modifier.weight(1f))
                KnButton(strings.marketChangeCropOrLocation, onChangeCropOrLocation, style = KnButtonStyle.Secondary, modifier = Modifier.weight(1f))
            }
        }

        if (market.markets.isNotEmpty()) {
            item {
                Text(strings.marketOtherMandis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(market.markets, key = { "${it.market}-${it.district}-${it.arrivalDate}" }) { mandi ->
                MandiRow(mandi, farmerDistrict, strings)
            }
        }
    }
}

@Composable
private fun MandiRow(mandi: MandiPrice, farmerDistrict: String, strings: AppStrings) {
    KnCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(mandi.market, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                text = mandi.modalPricePerQuintal?.let { "₹${it.toInt()}" } ?: "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "${mandi.district}, ${mandi.state}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            mandi.minPricePerQuintal?.let { Text("${strings.marketMinLabel} ₹${it.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            mandi.maxPricePerQuintal?.let { Text("${strings.marketMaxLabel} ₹${it.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (mandi.district.trim().equals(farmerDistrict.trim(), ignoreCase = true) && farmerDistrict.isNotBlank()) {
            Text(
                strings.marketSameDistrict,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun trendIconFor(trend: MarketTrend): ImageVector? = when (trend) {
    MarketTrend.RISING -> Icons.Rounded.TrendingUp
    MarketTrend.FALLING -> Icons.Rounded.TrendingDown
    MarketTrend.STABLE -> Icons.Rounded.TrendingFlat
    MarketTrend.UNKNOWN -> null
}

// Matches RiskLow/RiskHigh/onSurfaceVariant from Color.kt — a plain function
// (not @Composable) so it can be called from marketTrendLabel's sibling
// without threading MaterialTheme through, same tokens either way.
private fun trendColorFor(trend: MarketTrend): androidx.compose.ui.graphics.Color = when (trend) {
    MarketTrend.RISING -> androidx.compose.ui.graphics.Color(0xFF2E9E5B)
    MarketTrend.FALLING -> androidx.compose.ui.graphics.Color(0xFFD64545)
    MarketTrend.STABLE, MarketTrend.UNKNOWN -> androidx.compose.ui.graphics.Color(0xFF5B665F)
}

/** Null for UNKNOWN — a trend the provider couldn't determine is left unshown, never guessed. */
private fun marketTrendLabel(trend: MarketTrend, strings: AppStrings): String? = when (trend) {
    MarketTrend.RISING -> strings.marketTrendRising
    MarketTrend.FALLING -> strings.marketTrendFalling
    MarketTrend.STABLE -> strings.marketTrendStable
    MarketTrend.UNKNOWN -> null
}
