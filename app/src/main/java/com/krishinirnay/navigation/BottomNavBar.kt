package com.krishinirnay.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.krishinirnay.core.designsystem.strings.AppStrings
import com.krishinirnay.core.designsystem.strings.LocalAppStrings

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
) {
    val strings = LocalAppStrings.current

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        bottomTabDestinations.forEach { destination ->

            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    onNavigate(destination)
                },

                icon = {
                    Icon(
                        imageVector = iconFor(destination),
                        contentDescription = null,
                    )
                },

                label = {
                    Text(
                        text = labelFor(destination, strings),

                        // IMPORTANT:
                        // Label ek hi line mein rahega
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                },

                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,

                    unselectedIconColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,

                    unselectedTextColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,

                    indicatorColor =
                        MaterialTheme.colorScheme.primaryContainer,
                ),
            )
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