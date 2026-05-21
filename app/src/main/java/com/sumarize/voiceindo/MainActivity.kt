package com.sumarize.voiceindo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sumarize.voiceindo.ui.screen.DetailScreen
import com.sumarize.voiceindo.ui.screen.HistoryScreen
import com.sumarize.voiceindo.ui.screen.HomeScreen
import com.sumarize.voiceindo.ui.screen.ModeSelectionScreen
import com.sumarize.voiceindo.ui.screen.SetupScreen
import com.sumarize.voiceindo.ui.screen.SettingsScreen
import com.sumarize.voiceindo.ui.theme.SumarizeVoiceIndoTheme
import com.sumarize.voiceindo.viewmodel.HistoryViewModel
import com.sumarize.voiceindo.viewmodel.MainViewModel
import com.sumarize.voiceindo.viewmodel.MainViewModelFactory
import com.sumarize.voiceindo.viewmodel.SetupViewModel
import com.sumarize.voiceindo.viewmodel.SetupViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SumarizeVoiceIndoTheme {
                val navController = rememberNavController()
                val app = application as SumarizeApp

                val setupVm: SetupViewModel = viewModel(
                    factory = SetupViewModelFactory(applicationContext)
                )
                val setupState by setupVm.state.collectAsState()

                val startDestination = when {
                    !setupState.modeChosen -> "mode_select"
                    !setupState.isReady -> "setup"
                    else -> "home"
                }

                // Satu instance MainViewModel dibagi ke home + settings
                // agar GemmaLLM tidak di-load dua kali dan menyebabkan crash OOM
                val mainVm: MainViewModel = viewModel(
                    factory = MainViewModelFactory(applicationContext, app.database)
                )

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("mode_select") {
                        ModeSelectionScreen(
                            viewModel = setupVm,
                            onModePicked = {
                                navController.navigate("setup") {
                                    popUpTo("mode_select") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("setup") {
                        SetupScreen(
                            viewModel = setupVm,
                            onSetupComplete = {
                                navController.navigate("home") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            viewModel = mainVm,
                            onNavigateToHistory = { navController.navigate("history") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = mainVm,
                            onNavigateToDownload = {
                                navController.navigate("setup")
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("history") {
                        val historyVm: HistoryViewModel = viewModel(
                            factory = HistoryViewModel.Factory(app.database.summaryDao())
                        )
                        HistoryScreen(
                            viewModel = historyVm,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenDetail = { id -> navController.navigate("detail/$id") }
                        )
                    }
                    composable("detail/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        val historyVm: HistoryViewModel = viewModel(
                            factory = HistoryViewModel.Factory(app.database.summaryDao())
                        )
                        DetailScreen(
                            summaryId = id,
                            viewModel = historyVm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
