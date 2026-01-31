package com.parthipan.colorclashcards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.parthipan.colorclashcards.data.preferences.ThemePreferences
import com.parthipan.colorclashcards.ui.auth.AuthScreen
import com.parthipan.colorclashcards.ui.game.GameScreen
import com.parthipan.colorclashcards.ui.game.OnlineGameScreen
import com.parthipan.colorclashcards.ui.gamehub.GameHubScreen
import com.parthipan.colorclashcards.ui.home.HomeScreen
import com.parthipan.colorclashcards.ui.howtoplay.HowToPlayScreen
import com.parthipan.colorclashcards.ui.ludo.LudoHomeScreen
import com.parthipan.colorclashcards.ui.ludo.LudoLobbyEntryScreen
import com.parthipan.colorclashcards.ui.ludo.LudoOfflineGameScreen
import com.parthipan.colorclashcards.ui.ludo.LudoOnlineGameScreen
import com.parthipan.colorclashcards.ui.ludo.LudoRoomLobbyScreen
import com.parthipan.colorclashcards.ui.online.OnlineLobbyEntryScreen
import com.parthipan.colorclashcards.ui.online.RoomLobbyScreen
import com.parthipan.colorclashcards.ui.privacy.PrivacyScreen
import com.parthipan.colorclashcards.ui.settings.SettingsScreen
import com.parthipan.colorclashcards.ui.solo.SoloSetupScreen
import com.parthipan.colorclashcards.ui.splash.SplashScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themePreferences: ThemePreferences? = null
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route,
        modifier = modifier
    ) {
        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(NavRoutes.Auth.route) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(NavRoutes.GameHub.route) {
                        popUpTo(NavRoutes.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.GameHub.route) {
            GameHubScreen(
                onNavigateToColorClash = {
                    navController.navigate(NavRoutes.Home.route)
                },
                onNavigateToLudo = {
                    navController.navigate(NavRoutes.LudoHome.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                }
            )
        }

        composable(NavRoutes.LudoHome.route) {
            LudoHomeScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onStartOfflineGame = { botCount, difficulty ->
                    navController.navigate(NavRoutes.LudoGame.createRoute(botCount, difficulty))
                },
                onPlayOnline = {
                    navController.navigate(NavRoutes.LudoLobbyEntry.route)
                }
            )
        }

        composable(
            route = NavRoutes.LudoGame.route,
            arguments = listOf(
                navArgument("botCount") { type = NavType.IntType },
                navArgument("difficulty") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val botCount = backStackEntry.arguments?.getInt("botCount") ?: 1
            val difficulty = backStackEntry.arguments?.getString("difficulty") ?: "normal"

            LudoOfflineGameScreen(
                botCount = botCount,
                difficulty = difficulty,
                onBackClick = {
                    navController.popBackStack(NavRoutes.LudoHome.route, inclusive = false)
                }
            )
        }

        composable(NavRoutes.LudoLobbyEntry.route) {
            LudoLobbyEntryScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onRoomCreated = { roomId ->
                    navController.navigate(NavRoutes.LudoRoomLobby.createRoute(roomId))
                },
                onRoomJoined = { roomId ->
                    navController.navigate(NavRoutes.LudoRoomLobby.createRoute(roomId))
                }
            )
        }

        composable(
            route = NavRoutes.LudoRoomLobby.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            LudoRoomLobbyScreen(
                roomId = roomId,
                onBackClick = {
                    navController.popBackStack()
                },
                onGameStart = { startedRoomId, isHost ->
                    navController.navigate(
                        NavRoutes.LudoOnlineGame.createRoute(startedRoomId, isHost)
                    ) {
                        popUpTo(NavRoutes.LudoHome.route)
                    }
                }
            )
        }

        composable(
            route = NavRoutes.LudoOnlineGame.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
            LudoOnlineGameScreen(
                roomId = roomId,
                isHost = isHost,
                onBackClick = {
                    navController.popBackStack(NavRoutes.LudoHome.route, inclusive = false)
                }
            )
        }

        composable(NavRoutes.Home.route) {
            HomeScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToPlayOnline = {
                    navController.navigate(NavRoutes.OnlineLobby.route)
                },
                onNavigateToPlayVsComputer = {
                    navController.navigate(NavRoutes.SoloSetup.route)
                },
                onNavigateToHowToPlay = {
                    navController.navigate(NavRoutes.HowToPlay.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                },
                onRejoinGame = { roomId, isHost ->
                    navController.navigate(NavRoutes.OnlineGame.createRoute(roomId, isHost))
                }
            )
        }

        composable(NavRoutes.SoloSetup.route) {
            SoloSetupScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onStartGame = { botCount, difficulty ->
                    navController.navigate(
                        NavRoutes.Game.createRoute(
                            mode = "offline",
                            botCount = botCount,
                            difficulty = difficulty
                        )
                    )
                }
            )
        }

        composable(NavRoutes.OnlineLobby.route) {
            OnlineLobbyEntryScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onRoomCreated = { roomId ->
                    navController.navigate(NavRoutes.RoomLobby.createRoute(roomId))
                },
                onRoomJoined = { roomId ->
                    navController.navigate(NavRoutes.RoomLobby.createRoute(roomId))
                }
            )
        }

        composable(
            route = NavRoutes.RoomLobby.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            RoomLobbyScreen(
                roomId = roomId,
                onBackClick = {
                    navController.popBackStack()
                },
                onGameStart = { startedRoomId, isHost ->
                    navController.navigate(
                        NavRoutes.OnlineGame.createRoute(startedRoomId, isHost)
                    ) {
                        popUpTo(NavRoutes.Home.route)
                    }
                }
            )
        }

        composable(
            route = NavRoutes.OnlineGame.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
            OnlineGameScreen(
                roomId = roomId,
                isHost = isHost,
                onBackClick = {
                    navController.popBackStack(NavRoutes.Home.route, inclusive = false)
                }
            )
        }

        composable(
            route = NavRoutes.Game.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("botCount") { type = NavType.IntType },
                navArgument("difficulty") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "offline"
            val botCount = backStackEntry.arguments?.getInt("botCount") ?: 1
            val difficulty = backStackEntry.arguments?.getString("difficulty") ?: "easy"

            GameScreen(
                mode = mode,
                botCount = botCount,
                difficulty = difficulty,
                onBackClick = {
                    navController.popBackStack(NavRoutes.Home.route, inclusive = false)
                }
            )
        }

        composable(NavRoutes.HowToPlay.route) {
            HowToPlayScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onSignOut = {
                    navController.navigate(NavRoutes.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToPrivacy = {
                    navController.navigate(NavRoutes.Privacy.route)
                },
                themePreferences = themePreferences
            )
        }

        composable(NavRoutes.Privacy.route) {
            PrivacyScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
