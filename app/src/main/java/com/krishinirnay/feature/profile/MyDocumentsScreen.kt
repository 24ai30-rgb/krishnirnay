package com.krishinirnay.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.krishinirnay.core.designsystem.components.DrillDownTopBar
import com.krishinirnay.core.designsystem.components.EmptyState
import com.krishinirnay.core.designsystem.strings.LocalAppStrings

@Composable
fun MyDocumentsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DrillDownTopBar(title = strings.profileMyDocuments, onBack = onBack) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Rounded.Description, message = strings.profileNoDocuments)
        }
    }
}
