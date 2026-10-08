package com.krishinirnay.feature.chatbot

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmDiagnostics
import com.krishinirnay.core.llm.local.LocalLlmStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatbotScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatbotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var micPermissionDenied by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micPermissionDenied = false
            viewModel.startListening()
        } else {
            micPermissionDenied = true
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // A bottom-tab peer, not a drill-down — no back arrow, matching
            // Dashboard/Advisory/Profile's own top bars.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.voiceAssistantTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(strings.chatbotSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(2.dp))
                    AiStatusLine(kind = uiState.aiProviderKind, status = uiState.aiStatus, strings = strings)
                }
                IconButton(onClick = viewModel::clearChat) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = strings.chatbotClearChat,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.messages, key = ChatMessage::id) { message ->
                    MessageBubble(
                        message = message,
                        strings = strings,
                        onListen = { viewModel.speak(message.text) },
                        onRetry = { viewModel.retryMessage(message.id) },
                    )
                }
            }

            if (micPermissionDenied) {
                Text(
                    text = strings.chatbotMicPermissionDenied,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            ConnectionDiagnosticsPanel(
                strings = strings,
                isChecking = uiState.isCheckingConnection,
                diagnostics = uiState.diagnostics,
                onCheckClick = viewModel::checkConnection,
            )

            QuickReplyChips(
                strings = strings,
                onChipClick = { label -> viewModel.sendMessage(overrideText = label) },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    placeholder = { Text(strings.chatbotPlaceholder) },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.weight(1f),
                )
                val micPulse = rememberInfiniteTransition(label = "micPulse")
                val micScale by micPulse.animateFloat(
                    initialValue = 1f,
                    targetValue = if (uiState.isListening) 1.15f else 1f,
                    animationSpec = infiniteRepeatable(tween(600), repeatMode = RepeatMode.Reverse),
                    label = "micScale",
                )
                IconButton(
                    enabled = !uiState.isGenerating,
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            viewModel.startListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .scale(if (uiState.isListening) micScale else 1f)
                        .background(
                            if (uiState.isListening) KrishiTheme.colors.riskHigh else KrishiTheme.colors.surfaceAlt,
                            RoundedCornerShape(23.dp),
                        ),
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = strings.chatbotSpeak,
                        tint = if (uiState.isListening) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { if (uiState.isGenerating) viewModel.cancelGeneration() else viewModel.sendMessage() },
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (uiState.isGenerating) KrishiTheme.colors.riskHigh else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(23.dp),
                        ),
                ) {
                    if (uiState.isGenerating) {
                        Icon(Icons.Rounded.Stop, contentDescription = strings.chatbotStop, tint = Color.White)
                    } else {
                        Icon(Icons.Rounded.Send, contentDescription = strings.chatbotSend, tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * The one line a farmer actually needs (Phase 5 Part 1): which of the four
 * honest states the AI is in right now. Never claims a provider is ready
 * just because the app itself is running — see AiProviderCoordinator.
 */
@Composable
private fun AiStatusLine(kind: AiProviderKind, status: LocalLlmStatus, strings: AppStrings) {
    val label = when {
        status == LocalLlmStatus.LOADING -> strings.settingsAiModeLoading
        status == LocalLlmStatus.GENERATING -> strings.settingsAiModeGenerating
        kind == AiProviderKind.ON_DEVICE && status == LocalLlmStatus.READY -> strings.aiStatusOnDeviceReady
        kind == AiProviderKind.SERVER && status == LocalLlmStatus.READY -> strings.aiStatusServerReady
        status == LocalLlmStatus.MODEL_MISSING || status == LocalLlmStatus.ERROR -> strings.settingsAiModeUnavailable
        else -> strings.aiStatusOffline
    }
    val color = if (status == LocalLlmStatus.READY) KrishiTheme.colors.riskLow else MaterialTheme.colorScheme.onSurfaceVariant
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}

/**
 * On-demand (never automatic — a deep check runs a real ~15-30s generation)
 * breakdown of exactly which part of the Local LLM chain is broken, so a
 * developer never has to guess between "server unreachable", "Ollama down",
 * "model not pulled", and "model installed but generation itself failed".
 */
@Composable
private fun ConnectionDiagnosticsPanel(
    strings: AppStrings,
    isChecking: Boolean,
    diagnostics: LocalLlmDiagnostics?,
    onCheckClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !isChecking, onClick = onCheckClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isChecking) strings.localAiDiagnosticsRunning else strings.localAiDiagnosticsRun,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        diagnostics?.let { result ->
            Column(modifier = Modifier.padding(top = 6.dp)) {
                DiagnosticRow(strings.localAiDiagnosticsServerReachable, result.serverReachable, strings)
                DiagnosticRow(strings.localAiDiagnosticsOllamaAvailable, result.ollamaAvailable, strings)
                DiagnosticRow(strings.localAiDiagnosticsEnglishModel, result.englishModelAvailable, strings)
                DiagnosticRow(strings.localAiDiagnosticsHindiModel, result.hindiModelAvailable, strings)
                DiagnosticRow(strings.localAiDiagnosticsMarathiModel, result.marathiModelAvailable, strings)
                DiagnosticRow(strings.localAiDiagnosticsChatEndpointAvailable, result.chatEndpointAvailable, strings)
                result.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: Boolean?, strings: AppStrings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            text = when (value) {
                true -> strings.localAiDiagnosticsYes
                false -> strings.localAiDiagnosticsNo
                null -> "—"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (value) {
                true -> KrishiTheme.colors.riskLow
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun QuickReplyChips(strings: AppStrings, onChipClick: (String) -> Unit) {
    val chips = listOf(
        strings.chatbotChipSoilMoisture,
        strings.chatbotChipWeather,
        strings.chatbotChipWhatToDo,
        strings.chatbotChipSchemes,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .clickable { onChipClick(label) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val timestampFormat by lazy { SimpleDateFormat("h:mm a", Locale.getDefault()) }

@Composable
private fun MessageBubble(
    message: ChatMessage,
    strings: AppStrings,
    onListen: () -> Unit,
    onRetry: () -> Unit,
) {
    val bubbleColor = when {
        message.isFromUser -> MaterialTheme.colorScheme.primaryContainer
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> KrishiTheme.colors.surfaceAlt
    }
    val textColor = when {
        message.isFromUser -> MaterialTheme.colorScheme.onPrimaryContainer
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (message.isFromUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    val clipboard = LocalClipboardManager.current
    var justCopied by remember(message.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(bubbleColor, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                if (message.isGenerating && message.text.isBlank()) {
                    Text(
                        text = strings.chatbotThinking,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            Row(
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = timestampFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Actions only make sense once the bubble has settled text —
                // never while still streaming, and never on the user's own bubble.
                if (!message.isFromUser && !message.isGenerating && message.text.isNotBlank()) {
                    if (message.isError) {
                        MessageAction(icon = Icons.Rounded.Refresh, label = strings.chatbotRetry, onClick = onRetry)
                    } else {
                        MessageAction(icon = Icons.Rounded.VolumeUp, label = strings.chatbotListen, onClick = onListen)
                        MessageAction(
                            icon = Icons.Rounded.ContentCopy,
                            label = if (justCopied) strings.chatbotCopied else strings.chatbotCopy,
                            onClick = {
                                clipboard.setText(AnnotatedString(message.text))
                                justCopied = true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
