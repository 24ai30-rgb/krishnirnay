package com.krishinirnay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.ModelDownloadState

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onNavigateToSimulation: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFarmSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.settings, onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // PROFILE
            SectionLabel(strings.settingsSectionProfile)
            KnCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                SettingsNavRow(Icons.Rounded.Person, strings.settingsFarmerProfile, onNavigateToProfile)
                SettingsNavRow(Icons.Rounded.Landscape, strings.settingsFarmInformation, onNavigateToFarmSetup, showDivider = false)
            }

            // LANGUAGE & VOICE
            Spacer(Modifier.size(20.dp))
            SectionLabel(strings.settingsSectionLanguageVoice)
            KnCard {
                Text(strings.settingsLanguage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(8.dp))
                SegmentedToggle(
                    options = listOf(
                        strings.settingsLanguageEnglish to "en",
                        strings.settingsLanguageHindi to "hi",
                        strings.settingsLanguageMarathi to "mr",
                    ),
                    selected = uiState.language,
                    onSelect = viewModel::setLanguage,
                )
                Spacer(Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.settingsVoiceAssistant, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(strings.settingsVoiceAssistantDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.voiceAssistanceEnabled, onCheckedChange = viewModel::setVoiceAssistanceEnabled)
                }
            }

            // AI
            Spacer(Modifier.size(20.dp))
            SectionLabel(strings.settingsAiMode)
            KnCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (uiState.aiStatus == LocalLlmStatus.READY) androidx.compose.ui.graphics.Color(0xFF1E7D44) else MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        aiStatusLabel(uiState.aiProviderKind, uiState.aiStatus, strings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.settingsCloudFallback, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(strings.settingsCloudFallbackDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.cloudFallbackEnabled, onCheckedChange = viewModel::setCloudFallbackEnabled)
                }
            }

            // ON-DEVICE AI MODEL (Phase 5 Part 3) — English-only copy for now
            // (not yet threaded through LocalAppStrings like the rest of this
            // screen); this section is new, so localizing it is a follow-up,
            // not a regression.
            Spacer(Modifier.size(20.dp))
            SectionLabel("Offline AI Model")
            KnCard {
                OnDeviceModelSection(
                    state = uiState.onDeviceModelState,
                    onDownload = viewModel::downloadOnDeviceModel,
                    onDelete = viewModel::deleteOnDeviceModel,
                )
            }

            // AI DIAGNOSTICS (Phase 5 Part 14)
            Spacer(Modifier.size(20.dp))
            SectionLabel(strings.aiDiagnosticsTitle)
            KnCard {
                Text(
                    text = if (uiState.isCheckingAiConnection) strings.localAiDiagnosticsRunning else strings.aiDiagnosticsRunHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isCheckingAiConnection, onClick = viewModel::checkAiConnection),
                )
                uiState.aiDiagnostics?.let { d ->
                    Spacer(Modifier.size(12.dp))
                    DiagnosticStatusRow(strings.aiDiagnosticsProvider, if (uiState.aiProviderKind != AiProviderKind.NONE) DiagnosticState.OK else DiagnosticState.FAIL)
                    DiagnosticStatusRow(strings.aiDiagnosticsServer, if (d.serverReachable) DiagnosticState.OK else DiagnosticState.FAIL)
                    DiagnosticStatusRow(strings.localAiDiagnosticsOllamaAvailable, d.ollamaAvailable.toDiagnosticState())
                    DiagnosticStatusRow(strings.localAiDiagnosticsEnglishModel, d.englishModelAvailable.toDiagnosticState())
                    DiagnosticStatusRow(strings.localAiDiagnosticsHindiModel, d.hindiModelAvailable.toDiagnosticState())
                    DiagnosticStatusRow(strings.localAiDiagnosticsMarathiModel, d.marathiModelAvailable.toDiagnosticState())
                    DiagnosticStatusRow(strings.localAiDiagnosticsChatEndpointAvailable, d.chatEndpointAvailable.toDiagnosticState())
                }
            }

            // ADVANCED
            Spacer(Modifier.size(20.dp))
            SectionLabel(strings.settingsSectionAdvanced)
            KnCard {
                Text(strings.settingsDataSource, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(4.dp))
                Text(
                    text = strings.settingsDataSourceDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                SegmentedToggle(
                    options = listOf(strings.settingsModeMock to AppMode.MOCK, strings.settingsModeLive to AppMode.LIVE),
                    selected = uiState.appMode,
                    onSelect = viewModel::setAppMode,
                )
            }

            if (uiState.appMode == AppMode.MOCK) {
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = onNavigateToSimulation,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.settingsOpenSimulation)
                }
            }

            Spacer(Modifier.size(28.dp))

            OutlinedButton(
                onClick = { viewModel.logout(); onLoggedOut() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(strings.logOut)
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * Never a fake "AI online" status — see LocalLlmRepository. Phase 5's four
 * required states: On-device AI Ready / Local Server AI Ready / Offline AI /
 * AI Unavailable — mirrors ChatbotScreen's AiStatusLine so the same question
 * ("is the AI actually usable right now?") always gets the same answer
 * wherever a farmer looks.
 */
private fun aiStatusLabel(kind: AiProviderKind, status: LocalLlmStatus, strings: AppStrings): String = when {
    status == LocalLlmStatus.LOADING -> strings.settingsAiModeLoading
    status == LocalLlmStatus.GENERATING -> strings.settingsAiModeGenerating
    kind == AiProviderKind.ON_DEVICE && status == LocalLlmStatus.READY -> strings.aiStatusOnDeviceReady
    kind == AiProviderKind.SERVER && status == LocalLlmStatus.READY -> strings.aiStatusServerReady
    // "Server running but model not pulled" needs a different fix from "no
    // local AI at all", so the two are never collapsed into one message.
    status == LocalLlmStatus.MODEL_MISSING -> strings.settingsAiModeModelMissing
    status == LocalLlmStatus.ERROR -> strings.settingsAiModeUnavailable
    else -> strings.aiStatusOffline
}

/** The three states Part 14 asks for — never a raw error dump shown to a farmer. */
private enum class DiagnosticState { OK, LIMITED, FAIL }

private fun Boolean?.toDiagnosticState(): DiagnosticState = when (this) {
    true -> DiagnosticState.OK
    false -> DiagnosticState.FAIL
    null -> DiagnosticState.LIMITED
}

@Composable
private fun DiagnosticStatusRow(label: String, state: DiagnosticState) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        val (glyph, color) = when (state) {
            DiagnosticState.OK -> "✓" to KrishiTheme.colors.riskLow
            DiagnosticState.LIMITED -> "⚠" to KrishiTheme.colors.riskMedium
            DiagnosticState.FAIL -> "✕" to MaterialTheme.colorScheme.error
        }
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/**
 * Lets a farmer explicitly download the on-device model (see
 * OnDeviceModelManager/MediaPipeOnDeviceLlmProvider) so Local AI keeps
 * working even with the PC/FastAPI/Ollama completely off — never triggered
 * automatically, and every state (including a real failure reason, e.g.
 * "not enough storage") is shown honestly rather than a spinner that never
 * resolves.
 */
@Composable
private fun OnDeviceModelSection(
    state: ModelDownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(
        "Run AI directly on this phone — no PC or internet connection required once downloaded.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(12.dp))
    when (state) {
        is ModelDownloadState.NotDownloaded -> {
            OutlinedButton(onClick = onDownload, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Download offline AI model (~500 MB)")
            }
        }
        is ModelDownloadState.Downloading -> {
            val progress = if (state.totalBytes > 0) state.downloadedBytes.toFloat() / state.totalBytes else 0f
            Text(
                "Downloading… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is ModelDownloadState.Downloaded -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Offline AI model ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KrishiTheme.colors.riskLow,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Remove",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        }
        is ModelDownloadState.Failed -> {
            Text(state.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onDownload, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Retry download")
            }
        }
    }
}

@Composable
private fun <T> SegmentedToggle(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KrishiTheme.colors.surfaceAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
