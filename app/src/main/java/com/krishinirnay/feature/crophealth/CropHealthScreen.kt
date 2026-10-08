package com.krishinirnay.feature.crophealth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.designsystem.components.KnTopBar
import com.krishinirnay.core.designsystem.components.RiskBadge
import com.krishinirnay.core.designsystem.components.RiskBadgeSize
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.feature.howitworks.HowItWorksDialog

@Composable
fun CropHealthScreen(
    onNavigateToOfflineMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CropHealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    var showHowItWorks by remember { mutableStateOf(false) }

    val photoPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                viewModel.onImageSelected(uri)
            }
        }

    if (showHowItWorks) {
        HowItWorksDialog(
            onDismiss = { showHowItWorks = false },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KnTopBar(
                title = strings.diseaseDetectionTitle,
                isOnline = uiState.isOnline,
                onSyncChipClick = onNavigateToOfflineMode,
                onHelpClick = { showHowItWorks = true },
                onSettingsClick = onNavigateToSettings,
            )
        },
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ============================================================
            // SCAN AREA
            // ============================================================

            item {
                ProfessionalScanCard(
                    onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
            }

            // ============================================================
            // ANALYZING
            // ============================================================

            if (uiState.isScanning) {

                item {
                    ProfessionalLoadingCard()
                }

            // ============================================================
            // ERROR
            // ============================================================

            } else if (uiState.errorMessage != null) {

                item {
                    ProfessionalErrorCard(
                        message = uiState.errorMessage.orEmpty(),
                        technicalDetail = uiState.technicalErrorDetail,
                        onRetry = viewModel::retry,
                    )
                }

            // ============================================================
            // RESULT
            // ============================================================

            } else if (uiState.result != null) {

                val result = uiState.result!!

                item {
                    ProfessionalDiseaseResult(
                        previewUri = uiState.previewUri,
                        diseaseName = result.displayName,
                        confidence = result.confidence,
                        riskLevel = result.riskLevel,
                    )
                }

                item {
                    ProfessionalRecommendation(
                        diseaseName = result.displayName,
                        riskLevel = result.riskLevel,
                    )
                }

                item {
                    ProfessionalScanAgainButton(
                        onClick = {
                            photoPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}


// ========================================================================
// PROFESSIONAL SCAN CARD
// ========================================================================

@Composable
private fun ProfessionalScanCard(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {

        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(29.dp),
                )
            }

            Spacer(Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {

                Text(
                    text = "Scan a leaf",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.size(4.dp))

                Text(
                    text = "Upload a clear crop image",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}


// ========================================================================
// DISEASE RESULT
// ========================================================================

@Composable
private fun ProfessionalDiseaseResult(
    previewUri: Uri?,
    diseaseName: String,
    confidence: Float,
    riskLevel: RiskLevel,
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {

        Column(
            modifier = Modifier.padding(18.dp),
        ) {

            // ------------------------------------------------------------
            // HEADER
            // ------------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Column(
                    modifier = Modifier.weight(1f),
                ) {

                    Text(
                        text = "AI Analysis",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.size(3.dp))

                    Text(
                        text = "Disease detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {

                    Row(
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )

                        Spacer(Modifier.size(5.dp))

                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.size(18.dp))

            // ------------------------------------------------------------
            // IMAGE + DISEASE
            // ------------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {

                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {

                    if (previewUri != null) {

                        AsyncImage(
                            model = previewUri,
                            contentDescription = "Scanned crop leaf",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                    } else {

                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp),
                        )
                    }
                }

                Spacer(Modifier.size(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {

                    RiskBadge(
                        level = riskLevel,
                        size = RiskBadgeSize.Compact,
                    )

                    Spacer(Modifier.size(10.dp))

                    Text(
                        text = diseaseName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                    )

                    Spacer(Modifier.size(5.dp))

                    Text(
                        text = "Detected from crop image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(20.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(Modifier.size(16.dp))

            // ------------------------------------------------------------
            // CONFIDENCE
            // ------------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Text(
                    text = "Confidence",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.size(8.dp))

            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )

            Spacer(Modifier.size(7.dp))

            Text(
                text = confidenceMessage(confidence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


// ========================================================================
// CONFIDENCE MESSAGE
// ========================================================================

private fun confidenceMessage(
    confidence: Float,
): String {
    return when {
        confidence >= 0.85f ->
            "High confidence prediction"

        confidence >= 0.65f ->
            "Good confidence prediction"

        confidence >= 0.50f ->
            "Moderate confidence — consider another scan"

        else ->
            "Low confidence — capture a clearer leaf image"
    }
}


// ========================================================================
// RECOMMENDATION
// ========================================================================

@Composable
private fun ProfessionalRecommendation(
    diseaseName: String,
    riskLevel: RiskLevel,
) {

    val recommendation =
        when (riskLevel) {

            RiskLevel.HIGH ->
                "Isolate affected plants and inspect nearby leaves. Take crop-specific disease-control action as soon as possible."

            RiskLevel.MEDIUM ->
                "Monitor the affected area closely. Remove severely affected leaves and follow suitable crop-protection practices."

            RiskLevel.LOW ->
                "Continue regular monitoring and maintain healthy crop conditions. No immediate action is indicated."

            else ->
                "Monitor the crop closely and scan another clear image if symptoms become more visible."
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {

        Column(
            modifier = Modifier.padding(18.dp),
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.size(12.dp))

                Column {

                    Text(
                        text = "Recommended Action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = "Based on the AI assessment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(15.dp))

            Text(
                text = recommendation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.size(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.55f,
                ),
            ) {

                Row(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    Icon(
                        imageVector =
                            if (riskLevel == RiskLevel.HIGH)
                                Icons.Rounded.Warning
                            else
                                Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )

                    Spacer(Modifier.size(8.dp))

                    Text(
                        text = diseaseName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}


// ========================================================================
// SCAN AGAIN
// ========================================================================

@Composable
private fun ProfessionalScanAgainButton(
    onClick: () -> Unit,
) {

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
        ),
    ) {

        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.size(8.dp))

        Text(
            text = "Scan Another Leaf",
            fontWeight = FontWeight.SemiBold,
        )
    }
}


// ========================================================================
// LOADING
// ========================================================================

@Composable
private fun ProfessionalLoadingCard() {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {

        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            CircularProgressIndicator(
                modifier = Modifier.size(25.dp),
                strokeWidth = 3.dp,
            )

            Spacer(Modifier.size(14.dp))

            Column {

                Text(
                    text = "Analyzing crop...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.size(3.dp))

                Text(
                    text = "AI is checking the leaf for disease",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


// ========================================================================
// ERROR
// ========================================================================

@Composable
private fun ProfessionalErrorCard(
    message: String,
    technicalDetail: String? = null,
    onRetry: () -> Unit = {},
) {
    var showTechnicalDetail by remember(technicalDetail) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {

        Column(modifier = Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.Top) {

                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(23.dp),
                )

                Spacer(Modifier.size(11.dp))

                Column {

                    Text(
                        text = "Unable to analyze",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )

                    Spacer(Modifier.size(4.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.clickable(onClick = onRetry),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Retry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                // Developer-mode diagnostic — the real exception/IP detail,
                // never shown by default to a normal farmer (Part 13).
                if (technicalDetail != null) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.clickable { showTechnicalDetail = !showTechnicalDetail },
                    )
                }
            }
            if (showTechnicalDetail && technicalDetail != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = technicalDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}