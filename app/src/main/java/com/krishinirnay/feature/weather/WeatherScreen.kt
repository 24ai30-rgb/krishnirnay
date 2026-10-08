package com.krishinirnay.feature.weather

import com.krishinirnay.core.designsystem.motion.pulse
import com.krishinirnay.core.designsystem.motion.pressClickable
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import com.krishinirnay.core.designsystem.components.AnimatedNumber
import com.krishinirnay.core.designsystem.components.HeroCard
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.EmptyState
import com.krishinirnay.core.designsystem.components.KnButton
import com.krishinirnay.core.designsystem.components.KnButtonStyle
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.StatusBadge
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = hiltViewModel(),
) {
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.weatherTitle, onBack = onBack) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = weather.status == DataSourceStatus.LOADING,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (weather.status == DataSourceStatus.UNAVAILABLE) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    item {
                        EmptyState(icon = Icons.Rounded.Cloud, message = strings.weatherUnavailableMessage)
                    }
                    item {
                        KnButton(strings.actionRetry, viewModel::refresh, style = KnButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                    }
                }
                return@PullToRefreshBox
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "loc") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.enterStagger(0)) {
                        Text(weather.locationLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusBadge(status = weather.status)
                    }
                }
                item(key = "current") { CurrentWeatherCard(weather, strings) }

                // Real, rule-based farming interpretation — never generated
                // when the underlying reading isn't real (see agricultureInsights).
                val insights = agricultureInsights(weather, strings)
                if (insights.isNotEmpty()) {
                    item { FarmingTipsCard(insights, strings) }
                }

                item(key = "tabs") {
                    WeatherTabs(
                        selected = selectedTab,
                        labels = listOf(strings.weatherToday, strings.weatherThreeDay, strings.weatherSevenDay),
                        onSelect = { selectedTab = it },
                    )
                }
                val daysToShow = when (selectedTab) {
                    0 -> weather.daily.take(1)
                    1 -> weather.daily.take(3)
                    else -> weather.daily
                }
                items(daysToShow.size, key = { "${daysToShow[it].dayLabel}-$it" }, contentType = { "day" }) { index ->
                    DayForecastRow(daysToShow[index], index + 3)
                }
                item(key = "rain") {
                    KnCard(modifier = Modifier.enterStagger(8)) {
                        Text(
                            text = String.format(strings.weatherRainInTemplate, weather.rainInHoursLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherTabs(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(KrishiMotion.STANDARD), label = "wTabBg")
            val fg by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(KrishiMotion.STANDARD), label = "wTabFg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .pressClickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CurrentWeatherCard(weather: WeatherState, strings: AppStrings) {
    val c = KrishiTheme.colors
    HeroCard(modifier = Modifier.enterStagger(1)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedNumber(
                    target = weather.currentTempC.toFloat(),
                    format = { "${it.roundToInt()}°C" },
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                weather.feelsLikeC?.let {
                    Text("${strings.weatherFeelsLike} ${it}°C", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
            // Gentle breathing on the icon only — never on data text, and it plays
            // only once real data is already on screen. Skipped when motion is off.
            Icon(
                imageVector = iconFor(weather.condition),
                contentDescription = null,
                tint = c.lime,
                modifier = Modifier.size(64.dp).pulse(true),
            )
        }
        Spacer(Modifier.size(16.dp))
        // Row 1: the three readings every forecast provider always supplies.
        // Wind direction (when present) moves to the label line, never
        // appended to the value — "23 km/h NE" doesn't fit a 3-across column.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherStat(Icons.Rounded.Air, weather.windDirection?.let { "${strings.weatherWind} · $it" } ?: strings.weatherWind, "${weather.windKph} km/h", Modifier.weight(1f))
            WeatherStat(Icons.Rounded.Opacity, strings.humidity, "${weather.humidityPct}%", Modifier.weight(1f))
            WeatherStat(Icons.Rounded.Grain, strings.weatherRainChance, "${weather.rainChancePct}%", Modifier.weight(1f))
        }
        // Row 2: only the fields the provider actually returned — visibility,
        // UV, and pressure are all nullable and never shown as a fake 0.
        val extraStats = buildList {
            weather.visibilityKm?.let { add(Triple(Icons.Rounded.Visibility, strings.weatherVisibility, "$it km")) }
            weather.uvIndex?.let { add(Triple(Icons.Rounded.LightMode, strings.weatherUvIndex, it.toInt().toString())) }
            weather.pressureMb?.let { add(Triple(Icons.Rounded.Compress, strings.weatherPressure, "${it.toInt()} mb")) }
        }
        if (extraStats.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                extraStats.forEach { (icon, label, value) ->
                    WeatherStat(icon, label, value, Modifier.weight(1f))
                }
            }
        }
    }
}

// Vertically stacked (icon above value above label, all centered) — a 3-across
// grid on a narrow/scaled display has no room for "23 km/h" beside an icon on
// one line (measured clipping mid-word), but has plenty of width for centered
// text that can wrap onto 2 lines if needed.
@Composable
private fun WeatherStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = KrishiTheme.colors.lime, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(6.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, textAlign = TextAlign.Center)
    }
}

/**
 * Simple, honest, threshold-based farming interpretation of the real
 * numbers already on screen — never a separate guess, never shown when the
 * underlying reading doesn't actually support it. This is deliberately NOT
 * a second decision engine: it doesn't compute risk or override
 * DecisionEngine's own irrigation timing, it only narrates the weather
 * screen's own numbers in plain language, the same way a farmer would read
 * them out loud.
 */
private fun agricultureInsights(weather: WeatherState, strings: AppStrings): List<String> {
    if (weather.status == DataSourceStatus.UNAVAILABLE || weather.status == DataSourceStatus.LOADING) return emptyList()
    return buildList {
        if (weather.rainChancePct >= 60) add(strings.weatherTipRainLikely)
        if (weather.rainChancePct < 20 && weather.condition != WeatherCondition.RAIN && weather.condition != WeatherCondition.STORM) {
            add(strings.weatherTipGoodForIrrigation)
        }
        if (weather.humidityPct >= 80) add(strings.weatherTipHighHumidityDisease)
        if (weather.windKph >= 30) add(strings.weatherTipHighWindSpraying)
    }
}

@Composable
private fun FarmingTipsCard(insights: List<String>, strings: AppStrings) {
    KnCard(modifier = Modifier.fillMaxWidth().enterStagger(2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.size(10.dp))
            Text(strings.weatherFarmingTips, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.size(8.dp))
        insights.forEach { tip ->
            Text("• $tip", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayForecastRow(day: com.krishinirnay.core.data.model.DayForecast, index: Int) {
    KnCard(modifier = Modifier.enterStagger(index), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(day.dayLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(iconFor(day.condition), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                text = "${day.highC}°/${day.lowC}°",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun iconFor(condition: WeatherCondition): ImageVector = when (condition) {
    WeatherCondition.SUNNY -> Icons.Rounded.WbSunny
    WeatherCondition.PARTLY_CLOUDY -> Icons.Rounded.WbCloudy
    WeatherCondition.CLOUDY -> Icons.Rounded.Cloud
    WeatherCondition.RAIN -> Icons.Rounded.Grain
    WeatherCondition.STORM -> Icons.Rounded.Thunderstorm
}
