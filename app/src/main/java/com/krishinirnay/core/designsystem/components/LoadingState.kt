package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.motion.shimmer

/** Skeleton placeholder shaped like a typical screen (hero, tiles, row) — reads as "content arriving", not "waiting". */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.large).shimmer())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(84.dp).clip(MaterialTheme.shapes.medium).shimmer()) }
        }
        Box(Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.medium).shimmer())
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
