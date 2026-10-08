package com.krishinirnay.feature.pest

import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.designsystem.motion.shimmer
import com.krishinirnay.core.designsystem.motion.pressClickable
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.AnimatedNumber
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.HeroCard
import java.util.Locale
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import java.io.File

@Composable
fun PestDetectionScreen(
    onBack: () -> Unit,
    viewModel: PestDetectionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val c = KrishiTheme.colors

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            selectedUri = uri
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = "Pest Detection", onBack = onBack) },
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------------- pick + detect ----------------
            item(key = "pick") {
                HeroCard(modifier = Modifier.enterStagger(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, tint = c.lime, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Find pests", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Pick a clear photo of the plant", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                    selectedUri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected plant photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                        )
                        Spacer(Modifier.size(14.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PestPillButton(
                            icon = Icons.Rounded.Image,
                            label = "Select Plant Image",
                            container = if (selectedUri == null) c.lime else Color.White.copy(alpha = 0.16f),
                            content = if (selectedUri == null) c.onLime else Color.White,
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedUri != null) {
                            PestPillButton(
                                icon = Icons.Rounded.Search,
                                label = "Detect Pest",
                                container = c.lime,
                                content = c.onLime,
                                enabled = !uiState.isLoading,
                                onClick = {
                                    val uri = selectedUri ?: return@PestPillButton

                                    val file = File(
                                        context.cacheDir,
                                        "pest_image.jpg",
                                    )

                                    context.contentResolver
                                        .openInputStream(uri)
                                        ?.use { input ->
                                            file.outputStream()
                                                .use { output ->
                                                    input.copyTo(output)
                                                }
                                        }

                                    viewModel.detectPest(file)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ---------------- state ----------------
            when {
                uiState.isLoading -> item(key = "loading") {
                    KnCard(modifier = Modifier.fillMaxWidth().enterStagger(1)) {
                        Text("Analyzing image...", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(12.dp))
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).shimmer())
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(50)).shimmer())
                    }
                }

                uiState.result != null -> item(key = "result") {
                    val result = uiState.result
                    val top = result?.top_detection
                    if (result != null && result.detected && top != null) {
                        KnCard(modifier = Modifier.fillMaxWidth().enterStagger(1)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(c.riskHighContainer),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.BugReport, contentDescription = null, tint = c.riskHigh, modifier = Modifier.size(22.dp)) }
                                Spacer(Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pest detected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        top.class_name.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                AnimatedNumber(
                                    target = top.confidence * 100f,
                                    format = { String.format(Locale.US, "%.0f%%", it) },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = c.riskHigh,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Detected: ${result.count}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        KnCard(modifier = Modifier.fillMaxWidth().enterStagger(1)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(c.riskLowContainer),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.riskLow, modifier = Modifier.size(22.dp)) }
                                Spacer(Modifier.size(12.dp))
                                Text("No pest detected.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                uiState.error != null -> item(key = "error") {
                    KnCard(modifier = Modifier.fillMaxWidth().enterStagger(1)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Error, contentDescription = null, tint = c.riskHigh, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("Error: ${uiState.error}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PestPillButton(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(50))
            .background(container)
            .pressClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
