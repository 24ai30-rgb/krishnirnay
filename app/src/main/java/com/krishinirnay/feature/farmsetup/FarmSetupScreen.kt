package com.krishinirnay.feature.farmsetup

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.data.model.IrrigationMethod
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

/**
 * Deliberately simple: plain text fields and one segmented toggle, no
 * dropdowns/pickers/geocoding — the brief asks for "extremely simple," and a
 * farmer typing a state/district/crop name is more robust than a picker with
 * an incomplete list. Sowing date isn't editable here yet (Phase 3).
 */
@Composable
fun FarmSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FarmSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    var state by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var taluka by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var acres by remember { mutableFloatStateOf(0f) }
    var acresText by remember { mutableStateOf("") }
    var soilType by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf("") }
    var cropVariety by remember { mutableStateOf("") }
    var irrigationMethod by remember { mutableStateOf(IrrigationMethod.RAIN_FED) }
    var seeded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.profile) {
        if (!seeded) {
            val profile = uiState.profile
            state = profile.farmLocation.state
            district = profile.farmLocation.district
            taluka = profile.farmLocation.taluka
            village = profile.farmLocation.village
            latitudeText = profile.farmLocation.latitude?.toString().orEmpty()
            longitudeText = profile.farmLocation.longitude?.toString().orEmpty()
            acres = profile.farmSizeAcres
            acresText = if (profile.farmSizeAcres == 0f) "" else formatAcres(profile.farmSizeAcres)
            soilType = profile.soilType
            crop = profile.primaryCrop.orEmpty()
            cropVariety = profile.cropVariety
            irrigationMethod = profile.irrigationMethod
            seeded = true
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.farmSetupTitle, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(strings.farmSetupLocationSection, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                KnCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabeledField(strings.farmSetupState, state) { state = it }
                        LabeledField(strings.farmSetupDistrict, district) { district = it }
                        LabeledField(strings.farmSetupTaluka, taluka) { taluka = it }
                        LabeledField(strings.farmSetupVillage, village) { village = it }
                    }
                }
            }
            item {
                Text(strings.farmSetupCoordinatesSection, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                KnCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.farmSetupCoordinatesHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LabeledField(strings.farmSetupLatitude, latitudeText, keyboardType = KeyboardType.Decimal) { latitudeText = it }
                        LabeledField(strings.farmSetupLongitude, longitudeText, keyboardType = KeyboardType.Decimal) { longitudeText = it }
                    }
                }
            }
            item {
                KnCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabeledField(strings.farmSetupAcres, acresText, keyboardType = KeyboardType.Number) {
                            acresText = it
                            acres = it.toFloatOrNull() ?: acres
                        }
                        LabeledField(strings.farmSetupSoilType, soilType) { soilType = it }
                        LabeledField(strings.farmSetupCrop, crop) { crop = it }
                        LabeledField(strings.farmSetupCropVariety, cropVariety) { cropVariety = it }
                    }
                }
            }
            item {
                Text(strings.farmSetupIrrigationMethod, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                KnCard {
                    IrrigationMethodToggle(
                        selected = irrigationMethod,
                        strings = strings,
                        onSelect = { irrigationMethod = it },
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.save(
                            state = state,
                            district = district,
                            taluka = taluka,
                            village = village,
                            farmSizeAcres = acres,
                            soilType = soilType,
                            crop = crop,
                            cropVariety = cropVariety,
                            irrigationMethod = irrigationMethod,
                            latitude = latitudeText.toDoubleOrNull(),
                            longitude = longitudeText.toDoubleOrNull(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(if (uiState.justSaved) strings.farmSetupSaved else strings.farmSetupSave)
                }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IrrigationMethodToggle(
    selected: IrrigationMethod,
    strings: AppStrings,
    onSelect: (IrrigationMethod) -> Unit,
) {
    val options = listOf(
        IrrigationMethod.RAIN_FED to strings.farmSetupIrrigationRainFed,
        IrrigationMethod.IRRIGATED to strings.farmSetupIrrigationIrrigated,
        IrrigationMethod.DRIP to strings.farmSetupIrrigationDrip,
        IrrigationMethod.SPRINKLER to strings.farmSetupIrrigationSprinkler,
        IrrigationMethod.OTHER to strings.farmSetupIrrigationOther,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (method, label) ->
            val isSelected = method == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(method) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp),
                ) {
                    Text(
                        text = if (isSelected) "●" else "○",
                        color = if (isSelected) MaterialTheme.colorScheme.primary else KrishiTheme.colors.surfaceAlt,
                    )
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun formatAcres(acres: Float): String =
    if (acres == acres.toInt().toFloat()) acres.toInt().toString() else acres.toString()
