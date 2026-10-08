package com.krishinirnay.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One consistent button family for the whole app — every screen previously
 * reached for raw M3 `Button`/`TextButton` independently, which is why
 * button height/shape drifted screen to screen. [Primary] for the one main
 * action on a screen, [Secondary] for a supporting action that still needs
 * real visual weight (retry, view more), [Text] for the lowest-emphasis
 * action (e.g. "Skip").
 *
 * Fixed 52dp height on Primary/Secondary — a real touch target, not just
 * "big enough" — matches this app's large-touch-target accessibility
 * principle (see Type.kt's bodyLarge floor). Press feedback comes from
 * Material's own state-layer/ripple — never re-implemented here.
 */
enum class KnButtonStyle { Primary, Secondary, Text }

@Composable
fun KnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KnButtonStyle = KnButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val content: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp),
                strokeWidth = 2.dp,
                color = if (style == KnButtonStyle.Primary) Color.White else MaterialTheme.colorScheme.primary,
            )
        } else {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }

    when (style) {
        KnButtonStyle.Primary -> M3Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) { content() }

        KnButtonStyle.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.height(52.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) { content() }

        KnButtonStyle.Text -> TextButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) { content() }
    }
}
