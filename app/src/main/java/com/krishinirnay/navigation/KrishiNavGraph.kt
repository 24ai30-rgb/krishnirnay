package com.krishinirnay.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController    
import com.krishinirnay.feature.advisory.CropAdvisoryScreen
import com.krishinirnay.feature.alerts.AlertsScreen
import com.krishinirnay.feature.analytics.AnalyticsScreen
import com.krishinirnay.feature.auth.login.LoginScreen
import com.krishinirnay.feature.chatbot.ChatbotScreen
import com.krishinirnay.feature.crophealth.CropHealthScreen
import com.krishinirnay.feature.dashboard.DashboardScreen
import com.krishinirnay.feature.insights.AiInsightsScreen
import com.krishinirnay.feature.monitoring.LiveMonitoringScreen
import com.krishinirnay.feature.offline.OfflineModeScreen
import com.krishinirnay.feature.pest.PestDetectionScreen
import com.krishinirnay.feature.profile.MyDocumentsScreen
import com.krishinirnay.feature.profile.ProfileScreen
import com.krishinirnay.feature.schemes.SchemesScreen
import com.krishinirnay.feature.settings.SettingsScreen
import com.krishinirnay.feature.weather.WeatherScreen
import com.krishinirnay.feature.whatif.WhatIfScreen

/**
 * Two-level NavHost per the navigation plan:
 *
 * Outer host:
 * login <-> main
 * and drill-down destinations.
 *
 * Inner host:
 * bottom navigation destinations.
 */
@Composable
fun KrishiNavGraph(
    modifier: Modifier = Modifier,
) {
    AppStringsProvider {
        KrishiNavGraphContent(modifier)
    }
}

@Composable
private fun KrishiNavGraphContent(
    modifier: Modifier = Modifier,
) {
    val outerNavController = rememberNavController()

    NavHost(
        navController = outerNavController,
        startDestination = Destination.Login.route,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 4 },
            ) + fadeIn()
        },
        exitTransition = {
            fadeOut(targetAlpha = 0.4f)
        },
        popEnterTransition = {
            fadeIn(initialAlpha = 0.4f)
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 4 },
            ) + fadeOut()
        },
    ) {

        // =========================================================
        // LOGIN
        // =========================================================

        composable(Destination.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    outerNavController.navigate(
                        Destination.Main.route,
                    ) {
                        popUpTo(Destination.Login.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        // =========================================================
        // MAIN
        // =========================================================

        composable(Destination.Main.route) {
            MainScaffold(
                outerNavController = outerNavController,
            )
        }

        // =========================================================
        // MONITORING
        // =========================================================

        composable(Destination.Monitoring.route) {
            LiveMonitoringScreen(
                onNavigateToOfflineMode = {
                    outerNavController.navigate(
                        Destination.OfflineMode.route,
                    )
                },
                onNavigateToSettings = {
                    outerNavController.navigate(
                        Destination.Settings.route,
                    )
                },
            )
        }

        // =========================================================
        // PEST DETECTION
        // =========================================================

        composable(Destination.PestDetection.route) {
            PestDetectionScreen()
        }

        // =========================================================
        // WEATHER
        // =========================================================

        composable(Destination.Weather.route) {
            WeatherScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // ANALYTICS
        // =========================================================

        composable(Destination.Analytics.route) {
            AnalyticsScreen(
                onNavigateToOfflineMode = {
                    outerNavController.navigate(
                        Destination.OfflineMode.route,
                    )
                },
                onNavigateToSettings = {
                    outerNavController.navigate(
                        Destination.Settings.route,
                    )
                },
            )
        }

        // =========================================================
        // SCHEMES
        // =========================================================

        composable(Destination.Schemes.route) {
            SchemesScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // MY DOCUMENTS
        // =========================================================

        composable(Destination.MyDocuments.route) {
            MyDocumentsScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // INSIGHTS
        // =========================================================

        composable(Destination.Insights.route) {
            AiInsightsScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
                onNavigateToWhatIf = {
                    outerNavController.navigate(
                        Destination.WhatIf.route,
                    )
                },
            )
        }

        // =========================================================
        // WHAT IF
        // =========================================================

        composable(Destination.WhatIf.route) {
            WhatIfScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // OFFLINE MODE
        // =========================================================

        composable(Destination.OfflineMode.route) {
            OfflineModeScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // CHATBOT
        // =========================================================

        composable(Destination.Chatbot.route) {
            ChatbotScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // SETTINGS
        // =========================================================

        composable(Destination.Settings.route) {
            SettingsScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
                onLoggedOut = {
                    outerNavController.navigate(
                        Destination.Login.route,
                    ) {
                        popUpTo(0) {
                            inclusive = true
                        }
                    }
                },
            )
        }
    }
}

// =================================================================
// MAIN SCAFFOLD
// =================================================================

@Composable
private fun MainScaffold(
    outerNavController: NavHostController,
) {
    val innerNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by
                innerNavController.currentBackStackEntryAsState()

            BottomNavBar(
                currentRoute =
                    backStackEntry?.destination?.route,
                onNavigate = { destination ->
                    innerNavController.navigate(
                        destination.route,
                    ) {
                        popUpTo(
                            innerNavController.graph
                                .findStartDestination()
                                .id,
                        ) {
                            saveState = true
                        }

                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->

        NavHost(
            navController = innerNavController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {

            // =====================================================
            // DASHBOARD
            // =====================================================

            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onNavigateToInsights = {
                        outerNavController.navigate(
                            Destination.Insights.route,
                        )
                    },

                    onNavigateToChatbot = {
                        outerNavController.navigate(
                            Destination.Chatbot.route,
                        )
                    },

                    onNavigateToOfflineMode = {
                        outerNavController.navigate(
                            Destination.OfflineMode.route,
                        )
                    },

                    onNavigateToSettings = {
                        outerNavController.navigate(
                            Destination.Settings.route,
                        )
                    },

                    onNavigateToAdvisory = {
                        innerNavController.navigate(
                            Destination.Advisory.route,
                        )
                    },

                    onNavigateToWeather = {
                        outerNavController.navigate(
                            Destination.Weather.route,
                        )
                    },

                    onNavigateToMonitoring = {
                        outerNavController.navigate(
                            Destination.Monitoring.route,
                        )
                    },

                    onNavigateToSchemes = {
                        outerNavController.navigate(
                            Destination.Schemes.route,
                        )
                    },

                    // =================================================
                    // DISEASE DETECTION / CROP HEALTH
                    // =================================================

                    onNavigateToCropHealth = {
                        innerNavController.navigate(
                            Destination.CropHealth.route,
                        )
                    },
                )
            }

            // =====================================================
            // ADVISORY
            // =====================================================

            composable(Destination.Advisory.route) {
                CropAdvisoryScreen(
                    onNavigateToOfflineMode = {
                        outerNavController.navigate(
                            Destination.OfflineMode.route,
                        )
                    },
                    onNavigateToSettings = {
                        outerNavController.navigate(
                            Destination.Settings.route,
                        )
                    },
                )
            }

            // =====================================================
            // CROP HEALTH / DISEASE DETECTION
            // =====================================================

            composable(Destination.CropHealth.route) {
                CropHealthScreen(
                    onNavigateToOfflineMode = {
                        outerNavController.navigate(
                            Destination.OfflineMode.route,
                        )
                    },
                    onNavigateToSettings = {
                        outerNavController.navigate(
                            Destination.Settings.route,
                        )
                    },
                )
            }

            // =====================================================
            // ALERTS
            // =====================================================

            composable(Destination.Alerts.route) {
                AlertsScreen(
                    onNavigateToOfflineMode = {
                        outerNavController.navigate(
                            Destination.OfflineMode.route,
                        )
                    },
                    onNavigateToSettings = {
                        outerNavController.navigate(
                            Destination.Settings.route,
                        )
                    },
                )
            }

            // =====================================================
            // PROFILE
            // =====================================================

            composable(Destination.Profile.route) {
                ProfileScreen(
                    onNavigateToCrops = {
                        innerNavController.navigate(
                            Destination.Advisory.route,
                        )
                    },

                    onNavigateToFarm = {
                        outerNavController.navigate(
                            Destination.Analytics.route,
                        )
                    },

                    onNavigateToEquipment = {
                        outerNavController.navigate(
                            Destination.Monitoring.route,
                        )
                    },

                    onNavigateToDocuments = {
                        outerNavController.navigate(
                            Destination.MyDocuments.route,
                        )
                    },

                    onNavigateToSettings = {
                        outerNavController.navigate(
                            Destination.Settings.route,
                        )
                    },
                )
            }
        }
    }
}