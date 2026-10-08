package com.krishinirnay.feature.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.components.ProfileCompletionCard
import com.krishinirnay.core.designsystem.strings.LocalAppStrings

@Composable
fun ProfileScreen(
    onNavigateToCrops: () -> Unit,
    onNavigateToFarm: () -> Unit,
    onNavigateToEquipment: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSchemes: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(strings.profileTitle, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ProfileHeaderCard(profile, strings.profileFarmSizeTemplate) }
            item {
                val completion = remember(profile) { profile.completion(strings) }
                ProfileCompletionCard(
                    percent = completion.percent,
                    missingLabels = completion.missingLabels,
                    completeMessage = strings.profileCompletionComplete,
                    percentTemplate = strings.profileCompletionTemplate,
                    missingLabel = strings.profileCompletionMissingLabel,
                )
            }
            item {
                KnCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    MenuRow(Icons.Rounded.Spa, Color(0xFF1E7D44), Color(0xFFE3F5E9), strings.profileMyCrops, onNavigateToCrops, subtitle = profile.crops.joinToString(", "))
                    MenuRow(Icons.Rounded.Landscape, Color(0xFFB07A2E), Color(0xFFF6EBDA), strings.profileMyFarm, onNavigateToFarm)
                    MenuRow(Icons.Rounded.Sensors, Color(0xFF2F80ED), Color(0xFFE4EFFD), strings.profileMyEquipment, onNavigateToEquipment)
                    MenuRow(Icons.Rounded.Description, Color(0xFF8E5FD1), Color(0xFFEEE6FA), strings.profileMyDocuments, onNavigateToDocuments)
                    MenuRow(Icons.Rounded.AccountBalance, Color(0xFFB0742E), Color(0xFFF6EEDA), strings.dashboardQaSchemes, onNavigateToSchemes)
                    MenuRow(Icons.Rounded.Settings, Color(0xFF5B665F), Color(0xFFF1F3EF), strings.settings, onNavigateToSettings)
                    MenuRow(Icons.Rounded.Language, Color(0xFF12A594), Color(0xFFDFF5F1), strings.settingsLanguage, onNavigateToSettings, showDivider = false)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(profile: FarmerProfile, farmSizeTemplate: String) {
    KnCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(profile.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${profile.location} · ${String.format(farmSizeTemplate, formatAcres(profile.farmSizeAcres))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    label: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    subtitle: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "menuRowPress")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Drops a trailing ".0" for whole-number acreage (e.g. 2f -> "2" not "2.0"). */
private fun formatAcres(acres: Float): String =
    if (acres == acres.toInt().toFloat()) acres.toInt().toString() else acres.toString()
