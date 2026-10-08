package com.krishinirnay.feature.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.mock.SensorScenario

/**
 * Lets KRISHINIRNAY be demonstrated and tested end to end without physical
 * ESP32 hardware. Every button here is a genuine no-op in Live Mode — see
 * [SimulationViewModel] — never a fake LIVE reading.
 */
@Composable
fun SimulationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SimulationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.simulationTitle, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = strings.simulationDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!uiState.isMockModeActive) {
                item {
                    KnCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.simulationLiveModeWarning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            val scenarios = listOf(
                SensorScenario.DRY_SOIL to strings.simulationDrySoil,
                SensorScenario.NORMAL_SOIL to strings.simulationNormalSoil,
                SensorScenario.WET_SOIL to strings.simulationWetSoil,
                SensorScenario.HIGH_TEMPERATURE to strings.simulationHighTemp,
                SensorScenario.LOW_TEMPERATURE to strings.simulationLowTemp,
                SensorScenario.HIGH_HUMIDITY to strings.simulationHighHumidity,
                SensorScenario.LOW_HUMIDITY to strings.simulationLowHumidity,
            )
            items(scenarios, key = { it.first.name }, contentType = { "scenario" }) { (scenario, label) ->
                Button(
                    onClick = { viewModel.applyScenario(scenario) },
                    enabled = uiState.isMockModeActive,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label)
                }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::triggerIrrigation,
                    enabled = uiState.isMockModeActive,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.simulationIrrigate)
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::triggerDeviceDisconnect,
                    enabled = uiState.isMockModeActive,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.simulationDisconnect)
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::triggerDeviceReconnect,
                    enabled = uiState.isMockModeActive,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.simulationReconnect)
                }
            }
        }
    }
}
