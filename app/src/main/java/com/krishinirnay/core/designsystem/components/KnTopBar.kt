package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme

/**
 * Appears on every top-level (bottom-tab) screen — title, a tappable
 * sync-status icon (-> Offline Mode), an alerts bell (Home only), a "?"
 * icon (-> How-it-Works modal), a gear icon (-> Settings).
 *
 * The sync status is an icon, not a text chip — a text+dot pill ("Online"/
 * "Offline") ate enough width that on a phone with a larger system font
 * scale (a real, measured case: a farmer running this at 540dpi instead of
 * the 480dpi default, an accessibility setting this app should welcome, not
 * fight) the title itself had no room left and got clipped to 1-2 letters.
 * A same-size icon button, matching the other three, keeps every screen's
 * title readable regardless of the farmer's chosen display scale.
 */
@Composable
fun KnTopBar(
    title: String,
    isOnline: Boolean,
    onSyncChipClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Null on every screen except Home — Alerts is an occasional destination,
    // not something every bottom tab needs a bell for.
    onAlertsClick: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        TopBarIconButton(onClick = onSyncChipClick) {
            Box {
                Icon(
                    imageVector = if (isOnline) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff,
                    contentDescription = if (isOnline) strings.online else strings.offline,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (isOnline) KrishiTheme.colors.riskLow else KrishiTheme.colors.riskUnknown),
                )
            }
        }
        if (onAlertsClick != null) {
            TopBarIconButton(onClick = onAlertsClick) {
                Icon(Icons.Rounded.Notifications, contentDescription = "Alerts", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TopBarIconButton(onClick = onHelpClick) {
            Icon(Icons.Rounded.HelpOutline, contentDescription = "How KrishiNirnay works", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TopBarIconButton(onClick = onSettingsClick) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** 40dp circular surface button used by every top bar — reads as a tappable chip, not a bare glyph. */
@Composable
internal fun TopBarIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}
