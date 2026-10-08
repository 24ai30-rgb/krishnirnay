package com.krishinirnay.navigation

import androidx.compose.material3.MaterialTheme
import com.krishinirnay.core.designsystem.motion.KrishiMotion
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.krishinirnay.feature.auth.register.RegisterScreen
import com.krishinirnay.feature.auth.welcome.WelcomeScreen
import com.krishinirnay.feature.onboarding.OnboardingScreen
import com.krishinirnay.feature.chatbot.ChatbotScreen
import com.krishinirnay.feature.crophealth.CropHealthScreen
import com.krishinirnay.feature.dashboard.DashboardScreen
import com.krishinirnay.feature.farmsetup.FarmSetupScreen
import com.krishinirnay.feature.insights.AiInsightsScreen
import com.krishinirnay.feature.monitoring.LiveMonitoringScreen
import com.krishinirnay.feature.offline.OfflineModeScreen
import com.krishinirnay.feature.pest.PestDetectionScreen
import com.krishinirnay.feature.profile.MyDocumentsScreen
import com.krishinirnay.feature.profile.ProfileScreen
import com.krishinirnay.feature.schemes.SchemesScreen
import com.krishinirnay.feature.simulation.SimulationScreen
import com.krishinirnay.feature.settings.SettingsScreen
import com.krishinirnay.feature.market.MarketScreen
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
    val authGateViewModel: AuthGateViewModel = hiltViewModel()

    NavHost(
        navController = outerNavController,
        startDestination = authGateViewModel.startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(KrishiMotion.firm()) { it * 3 / 10 } + fadeIn(tween(KrishiMotion.STANDARD))
        },
        exitTransition = {
            slideOutHorizontally(KrishiMotion.firm()) { -it / 10 } + fadeOut(tween(KrishiMotion.QUICK), targetAlpha = 0.6f)
        },
        popEnterTransition = {
            slideInHorizontally(KrishiMotion.firm()) { -it / 10 } + fadeIn(tween(KrishiMotion.STANDARD), initialAlpha = 0.6f)
        },
        popExitTransition = {
            slideOutHorizontally(KrishiMotion.firm()) { it * 3 / 10 } + fadeOut(tween(KrishiMotion.QUICK))
        },
    ) {

        // =========================================================
        // WELCOME (the actual cold-start landing screen — see
        // AuthGateViewModel's doc comment. Welcome/Login/Register form one
        // small pre-auth stack; every cross-link below pops back to Welcome
        // first so switching between Login and Register never piles up
        // duplicate screens, and a successful login/registration clears the
        // whole pre-auth stack at once.)
        // =========================================================

        composable(Destination.Welcome.route) {
            WelcomeScreen(
                onCreateAccount = {
                    outerNavController.navigate(Destination.Register.route) {
                        popUpTo(Destination.Welcome.route)
                    }
                },
                onLogIn = {
                    outerNavController.navigate(Destination.Login.route) {
                        popUpTo(Destination.Welcome.route)
                    }
                },
            )
        }

        // =========================================================
        // LOGIN
        // =========================================================

        composable(Destination.Login.route) {
            LoginScreen(
                onLoginSuccess = { needsOnboarding ->
                    val destination = if (needsOnboarding) Destination.Onboarding.route else Destination.Main.route
                    outerNavController.navigate(destination) {
                        popUpTo(Destination.Welcome.route) {
                            inclusive = true
                        }
                    }
                },
                onNavigateToRegister = {
                    outerNavController.navigate(Destination.Register.route) {
                        popUpTo(Destination.Welcome.route)
                    }
                },
            )
        }

        // =========================================================
        // REGISTER (new-user flow — see KrishiNavGraph's own doc comment)
        // =========================================================

        composable(Destination.Register.route) {
            RegisterScreen(
                onRegistered = {
                    // A brand-new account always needs Farm Setup — crop/
                    // state/district are collected there, never duplicated
                    // on the Register form itself.
                    outerNavController.navigate(Destination.Onboarding.route) {
                        popUpTo(Destination.Welcome.route) {
                            inclusive = true
                        }
                    }
                },
                onNavigateToLogin = {
                    outerNavController.navigate(Destination.Login.route) {
                        popUpTo(Destination.Welcome.route)
                    }
                },
            )
        }

        // =========================================================
        // ONBOARDING
        // =========================================================

        composable(Destination.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    outerNavController.navigate(
                        Destination.Main.route,
                    ) {
                        popUpTo(Destination.Onboarding.route) {
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
            PestDetectionScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
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
        // MARKET
        // =========================================================

        composable(Destination.Market.route) {
            MarketScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
                // Farm Setup edits crop, state and district — exactly what a
                // no-data/failed market price needs the farmer to change.
                onChangeCropOrLocation = {
                    outerNavController.navigate(
                        Destination.FarmSetup.route,
                    )
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
        // ALERTS (reached from Home's header bell — see MAIN section's
        // DashboardScreen call site)
        // =========================================================

        composable(Destination.Alerts.route) {
            AlertsScreen(
                onBack = {
                    outerNavController.popBackStack()
                },
            )
        }

        // =========================================================
        // FARM SETUP
        // =========================================================

        composable(Destination.FarmSetup.route) {
            FarmSetupScreen(
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
                onNavigateToSimulation = {
                    outerNavController.navigate(
                        Destination.Simulation.route,
                    )
                },
                onNavigateToProfile = {
                    // Settings is only ever pushed from the Profile tab (see onNavigateToSettings
                    // above), and Profile lives on innerNavController inside MainScaffold, not on
                    // outerNavController — so there is no outer route to navigate to directly.
                    // Popping back to Main restores that already-active Profile tab.
                    outerNavController.popBackStack()
                },
                onNavigateToFarmSetup = {
                    outerNavController.navigate(
                        Destination.FarmSetup.route,
                    )
                },
            )
        }

        // =========================================================
        // SENSOR SIMULATION (testing without physical hardware)
        // =========================================================

        composable(Destination.Simulation.route) {
            SimulationScreen(
                onBack = {
                    outerNavController.popBackStack()
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
        containerColor = MaterialTheme.colorScheme.background,
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
            // A bottom-tab switch is a lateral move, not a drill-down — a
            // quick crossfade (not the outer graph's slide) reads as "same
            // level, different view" instead of implying a stack push.
            enterTransition = { fadeIn(tween(KrishiMotion.STANDARD)) + scaleIn(tween(KrishiMotion.STANDARD), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(KrishiMotion.QUICK)) },
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

                    // Chatbot is now a bottom tab (the AI Assistant peer, not a
                    // drill-down), so the FAB switches tabs the same way
                    // BottomNavBar does rather than pushing on the outer stack.
                    onNavigateToChatbot = {
                        innerNavController.navigate(Destination.Chatbot.route) {
                            popUpTo(innerNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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

                    // Alerts is reached from Home's header bell now, not a
                    // bottom tab — see Destinations.kt's bottomTabDestinations
                    // note and the ALERTS section registered on the outer graph.
                    onNavigateToAlerts = {
                        outerNavController.navigate(
                            Destination.Alerts.route,
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

                    // =================================================
                    // PEST DETECTION
                    // =================================================

                    onNavigateToPestDetection = {
                        outerNavController.navigate(
                            Destination.PestDetection.route,
                        )
                    },
                    // Farm Setup edits crop, state and district — exactly the
                    // inputs the market query depends on. Same route the Profile
                    // tab already uses.
                    onNavigateToFarmSetup = {
                        outerNavController.navigate(
                            Destination.FarmSetup.route,
                        )
                    },
                    onViewMoreMarkets = {
                        outerNavController.navigate(
                            Destination.Market.route,
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
            // AI ASSISTANT (bottom tab — a peer screen, not a drill-down,
            // so it has no back arrow; ChatbotScreen's own topBar reflects that)
            // =====================================================

            composable(Destination.Chatbot.route) {
                ChatbotScreen()
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
                            Destination.FarmSetup.route,
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

                    onNavigateToSchemes = {
                        outerNavController.navigate(
                            Destination.Schemes.route,
                        )
                    },
                )
            }
        }
    }
}