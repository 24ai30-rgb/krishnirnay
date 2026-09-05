package com.krishinirnay.navigation

sealed class Destination(val route: String) {
    data object Login : Destination("login")
    data object Main : Destination("main")

    // Bottom-tab destinations (inner NavHost)
    data object Dashboard : Destination("dashboard")
    data object Advisory : Destination("advisory")
    data object Weather : Destination("weather")
    data object Alerts : Destination("alerts")
    data object Profile : Destination("profile")

    // Drill-down destinations (outer NavHost, pushed on top, back arrow, bottom bar hidden)
    data object Monitoring : Destination("monitoring")
    data object PestDetection : Destination("pest_detection")
    data object CropHealth : Destination("crop_health")
    data object Analytics : Destination("analytics")
    data object Schemes : Destination("schemes")
    data object Insights : Destination("insights")
    data object WhatIf : Destination("what_if")
    data object OfflineMode : Destination("offline_mode")
    data object Chatbot : Destination("chatbot")
    data object Settings : Destination("settings")
    data object MyDocuments : Destination("my_documents")
}

/** The 5 bottom tabs, in display order — matches the Kisan Sahayak mockup's IA. */
val bottomTabDestinations = listOf(
    Destination.Dashboard,
    Destination.Advisory,
    Destination.CropHealth,
    Destination.Alerts,
    Destination.Profile,
)