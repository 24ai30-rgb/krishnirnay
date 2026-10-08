package com.krishinirnay.feature.schemes

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.KnCard
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.schemes.MatchedScheme

@Composable
fun SchemesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchemesViewModel = hiltViewModel(),
) {
    val matchedSchemes by viewModel.matchedSchemes.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.schemesTitle, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = strings.schemesSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (matchedSchemes.isEmpty()) {
                item {
                    KnCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.schemesUnavailable,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(matchedSchemes, key = { it.scheme.id }) { matched ->
                SchemeCard(
                    matched,
                    strings.schemesViewDetails,
                    strings.schemesHideDetails,
                    strings.schemesEligibilityLabel,
                    strings.schemesWhyEligible,
                    strings.schemesLastVerified,
                )
            }
        }
    }
}

@Composable
private fun SchemeCard(
    matched: MatchedScheme,
    viewDetailsLabel: String,
    hideDetailsLabel: String,
    eligibilityLabel: String,
    whyEligibleLabel: String,
    lastVerifiedLabel: String,
) {
    val scheme = matched.scheme
    var expanded by remember { mutableStateOf(false) }
    KnCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE3F5E9)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = Color(0xFF1E7D44), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text(
                scheme.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(scheme.benefit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(scheme.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text("$whyEligibleLabel ${matched.reasons.joinToString(" ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (expanded) {
            Spacer(Modifier.size(12.dp))
            Text(eligibilityLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            Text(scheme.eligibility, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (scheme.requiredDocuments.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(scheme.requiredDocuments.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (scheme.applicationMethod.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(scheme.applicationMethod, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (scheme.officialSource.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(scheme.officialSource, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scheme.lastUpdated.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    "$lastVerifiedLabel: ${scheme.lastUpdated}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (expanded) hideDetailsLabel else viewDetailsLabel)
        }
    }
}
