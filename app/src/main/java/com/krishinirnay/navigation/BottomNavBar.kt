package com.krishinirnay.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import com.krishinirnay.core.designsystem.motion.pressClickable
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.designsystem.theme.NavBarInk
import com.krishinirnay.core.designsystem.theme.NavBarMuted

/**
 * Floating pill bar: dark ink in light mode, raised surface in dark mode.
 * The selected tab grows a lime pill with its label; others show icon only,
 * so 5 tabs never overflow even with long Hindi/Marathi labels.
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = KrishiTheme.colors
    val barColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) NavBarInk else MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(barColor)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomTabDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val bg by animateColorAsState(if (selected) c.lime else Color.Transparent, tween(KrishiMotion.STANDARD), label = "tabBg")
            val fg by animateColorAsState(if (selected) c.onLime else NavBarMuted, tween(KrishiMotion.STANDARD), label = "tabFg")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .pressClickable { onNavigate(destination) }
                    .animateContentSize(KrishiMotion.firm())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(iconFor(destination), contentDescription = labelFor(destination, strings), tint = fg, modifier = Modifier.size(22.dp))
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = labelFor(destination, strings),
                        color = fg,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
            }
        }
    }
}

private fun iconFor(destination: Destination): ImageVector =
    when (destination) {

        Destination.Dashboard ->
            Icons.Rounded.Home

        Destination.Advisory ->
            Icons.Rounded.Spa

        Destination.CropHealth ->
            Icons.Rounded.CameraAlt

        Destination.Chatbot ->
            Icons.Rounded.AutoAwesome

        Destination.Profile ->
            Icons.Rounded.Person

        else ->
            Icons.Rounded.Home
    }

private fun labelFor(
    destination: Destination,
    strings: AppStrings,
): String =
    when (destination) {

        Destination.Dashboard ->
            strings.navHome

        Destination.Advisory ->
            strings.navAdvisory

        Destination.CropHealth ->
            strings.diseaseDetectionTitle

        Destination.Chatbot ->
            strings.navAssistant

        Destination.Profile ->
            strings.navProfile

        else ->
            ""
    }