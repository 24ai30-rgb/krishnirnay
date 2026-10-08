package com.krishinirnay.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.KrishiTextField
import com.krishinirnay.core.designsystem.components.SproutMark
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

/**
 * Multi-step Farmer Registration / Onboarding — see OnboardingViewModel for
 * where each field ends up (the existing FarmerProfile, through the existing
 * ProfileRepository; never a second farmer data store). Progress is kept in
 * the ViewModel's own state for the lifetime of this screen — closing the
 * app mid-flow before reaching the final "Save" restarts onboarding from
 * Welcome next time, since nothing is persisted until Save is pressed.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.step > 0) {
                LinearProgressIndicator(
                    progress = uiState.step / (ONBOARDING_STEP_COUNT - 1).toFloat(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = KrishiTheme.colors.surfaceAlt,
                )
                Text(
                    text = String.format(strings.onboardingStepOfTemplate, uiState.step, ONBOARDING_STEP_COUNT - 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            AnimatedContent(
                targetState = uiState.step,
                modifier = Modifier.weight(1f),
                transitionSpec = { (fadeIn(tween(200))).togetherWith(fadeOut(tween(150))) },
                label = "onboardingStep",
            ) { step ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (step) {
                        0 -> welcomeStep(strings)
                        1 -> personalStep(uiState, viewModel, strings)
                        2 -> farmStep(uiState, viewModel, strings)
                        3 -> cropStep(uiState, viewModel, strings)
                        4 -> preferencesStep(uiState, viewModel, strings)
                        5 -> confirmationStep(uiState, strings)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.step > 0) {
                    OutlinedButton(
                        onClick = viewModel::back,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text(strings.onboardingBack)
                    }
                }
                Button(
                    onClick = {
                        if (uiState.step == ONBOARDING_STEP_COUNT - 1) {
                            viewModel.save(onDone = onComplete)
                        } else {
                            viewModel.next()
                        }
                    },
                    enabled = uiState.isCurrentStepValid() && !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(
                        text = if (uiState.step == ONBOARDING_STEP_COUNT - 1) strings.onboardingSaveProfile else strings.onboardingContinue,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.welcomeStep(strings: AppStrings) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SproutMark(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.size(24.dp))
            Text(
                text = strings.onboardingWelcomeTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = strings.onboardingWelcomeSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personalStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    strings: AppStrings,
) {
    item { StepTitle(strings.onboardingPersonalStepTitle) }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KrishiTextField(strings.onboardingFullName, state.name, required = true) { v -> viewModel.update { it.copy(name = v) } }
                KrishiTextField(strings.onboardingMobileNumber, state.phone, required = true, keyboardType = KeyboardType.Phone) { v -> viewModel.update { it.copy(phone = v) } }
                KrishiTextField(strings.onboardingAlternateMobile, state.alternateMobile, optionalLabel = strings.onboardingOptionalLabel, keyboardType = KeyboardType.Phone) { v -> viewModel.update { it.copy(alternateMobile = v) } }
                KrishiTextField(strings.onboardingGender, state.gender, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(gender = v) } }
                KrishiTextField(strings.onboardingAddress, state.address, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(address = v) } }
            }
        }
    }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KrishiTextField(strings.farmSetupState, state.state, required = true) { v -> viewModel.update { it.copy(state = v) } }
                KrishiTextField(strings.farmSetupDistrict, state.district, required = true) { v -> viewModel.update { it.copy(district = v) } }
                KrishiTextField(strings.farmSetupTaluka, state.taluka, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(taluka = v) } }
                KrishiTextField(strings.farmSetupVillage, state.village, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(village = v) } }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.farmStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    strings: AppStrings,
) {
    item { StepTitle(strings.onboardingFarmStepTitle) }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KrishiTextField(strings.farmSetupAcres, state.farmSizeAcresText, required = true, keyboardType = KeyboardType.Number) { v -> viewModel.update { it.copy(farmSizeAcresText = v) } }
                KrishiTextField(strings.onboardingOwnershipType, state.ownershipType, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(ownershipType = v) } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.onboardingIrrigationAvailable, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.irrigationAvailable, onCheckedChange = { v -> viewModel.update { it.copy(irrigationAvailable = v) } })
                }
                KrishiTextField(strings.onboardingWaterSource, state.waterSource, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(waterSource = v) } }
            }
        }
    }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.farmSetupCoordinatesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KrishiTextField(strings.farmSetupLatitude, state.latitudeText, optionalLabel = strings.onboardingOptionalLabel, keyboardType = KeyboardType.Decimal) { v -> viewModel.update { it.copy(latitudeText = v) } }
                KrishiTextField(strings.farmSetupLongitude, state.longitudeText, optionalLabel = strings.onboardingOptionalLabel, keyboardType = KeyboardType.Decimal) { v -> viewModel.update { it.copy(longitudeText = v) } }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cropStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    strings: AppStrings,
) {
    item { StepTitle(strings.onboardingCropStepTitle) }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KrishiTextField(strings.onboardingPrimaryCrop, state.primaryCrop, required = true) { v -> viewModel.update { it.copy(primaryCrop = v) } }
                KrishiTextField(strings.onboardingSecondaryCrop, state.secondaryCrop, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(secondaryCrop = v) } }
                KrishiTextField(strings.farmSetupCropVariety, state.cropVariety, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(cropVariety = v) } }
                KrishiTextField(strings.onboardingCropStage, state.cropStage, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(cropStage = v) } }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.preferencesStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    strings: AppStrings,
) {
    item { StepTitle(strings.onboardingPreferencesStepTitle) }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguageToggle(state.language) { v -> viewModel.update { it.copy(language = v) } }
                KrishiTextField(strings.onboardingFarmingExperience, state.farmingExperienceYearsText, optionalLabel = strings.onboardingOptionalLabel, keyboardType = KeyboardType.Number) { v -> viewModel.update { it.copy(farmingExperienceYearsText = v) } }
                KrishiTextField(strings.farmSetupSoilType, state.soilType, optionalLabel = strings.onboardingOptionalLabel) { v -> viewModel.update { it.copy(soilType = v) } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.onboardingVoiceAssistance, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.voiceAssistanceEnabled, onCheckedChange = { v -> viewModel.update { it.copy(voiceAssistanceEnabled = v) } })
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.confirmationStep(state: OnboardingUiState, strings: AppStrings) {
    item { StepTitle(strings.onboardingConfirmationStepTitle) }
    item {
        KnCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryRow(strings.onboardingConfirmationFarmer, "${state.name} • ${state.phone}")
                SummaryRow(strings.onboardingConfirmationLocation, listOf(state.village, state.taluka, state.district, state.state).filter { it.isNotBlank() }.joinToString(", "))
                SummaryRow(strings.onboardingConfirmationFarm, "${state.farmSizeAcresText} acres")
                SummaryRow(strings.onboardingConfirmationCrop, listOf(state.primaryCrop, state.secondaryCrop).filter { it.isNotBlank() }.joinToString(", "))
                SummaryRow(strings.onboardingConfirmationIrrigation, state.irrigationMethod.name)
                SummaryRow(strings.onboardingConfirmationLanguage, state.language.uppercase())
            }
        }
    }
}

@Composable
private fun StepTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(2f))
    }
}

@Composable
private fun LanguageToggle(selected: String, onSelect: (String) -> Unit) {
    val strings = LocalAppStrings.current
    val options = listOf(
        strings.settingsLanguageEnglish to "en",
        strings.settingsLanguageHindi to "hi",
        strings.settingsLanguageMarathi to "mr",
    )
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KrishiTheme.colors.surfaceAlt).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
