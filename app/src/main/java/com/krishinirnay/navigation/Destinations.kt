package com.krishinirnay.navigation

sealed class Destination(val route: String) {
    data object Welcome : Destination("welcome")
    data object Login : Destination("login")
    data object Register : Destination("register")
    data object Onboarding : Destination("onboarding")
    data object Main : Destination("main")

    // Bottom-tab destinations (inner NavHost)
    data object Dashboard : Destination("dashboard")
    data object Advisory : Destination("advisory")
    data object Weather : Destination("weather")
    data object Market : Destination("market")
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
    data object FarmSetup : Destination("farm_setup")
    data object Simulation : Destination("simulation")
}

/**
 * The 5 bottom tabs, in display order: Home, Advisory, Disease, AI Assistant,
 * Profile. Alerts moved off the bottom bar to a header entry point on Home —
 * it's an occasional destination, not one of the 5 places a farmer lives day
 * to day, and the AI Assistant earns the peer slot instead.
 */
val bottomTabDestinations = listOf(
    Destination.Dashboard,
    Destination.Advisory,
    Destination.CropHealth,
    Destination.Chatbot,
    Destination.Profile,
)