package com.parthipan.colorclashcards.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themePreferences: ThemePreferences? = null
) {
    val animDuration = 350
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route,
        modifier = modifier,
        enterTransition = {
            // Forward: scale-in from 0.92 + fade
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(animDuration)
            ) + fadeIn(tween(animDuration))
        },
        exitTransition = {
            // Exit: scale-out to 1.05 + fade
            scaleOut(
                targetScale = 1.05f,
                animationSpec = tween(animDuration)
            ) + fadeOut(tween(animDuration))
        },
        popEnterTransition = {
            // Back: slide-right + fade
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) + fadeIn(tween(animDuration))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) + fadeOut(tween(animDuration))
        }
    ) {
        composable(
            NavRoutes.Splash.route,
            enterTransition = { fadeIn(tween(animDuration)) },
            exitTransition = { fadeOut(tween(animDuration)) }
        ) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(NavRoutes.Auth.createRoute()) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.Auth.route,
            enterTransition = { fadeIn(tween(animDuration)) },
            exitTransition = { fadeOut(tween(animDuration)) },
            popEnterTransition = { fadeIn(tween(animDuration)) },
            popExitTransition = { fadeOut(tween(animDuration)) },
            arguments = listOf(
                navArgument("returnRoute") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val returnRoute = backStackEntry.arguments?.getString("returnRoute") ?: ""
            AuthScreen(
                onAuthSuccess = {
                    val destination = if (returnRoute.isNotEmpty()) returnRoute else NavRoutes.GameHub.route
                    navController.navigate(destination) {
                        popUpTo(NavRoutes.Auth.route) { inclusive = true }
                    }
                },
                onPlayOffline = {
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
                },
                onSignIn = {
                    navController.navigate(NavRoutes.Auth.createRoute(returnRoute = NavRoutes.GameHub.route))
                }
            )
        }

        composable(NavRoutes.LudoHome.route) {
            LudoHomeScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onStartOfflineGame = { botCount, difficulty, color ->
                    navController.navigate(NavRoutes.LudoGame.createRoute(botCount, difficulty, color))
                },
                onPlayOnline = {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        navController.navigate(NavRoutes.LudoLobbyEntry.route)
                    } else {
                        navController.navigate(NavRoutes.Auth.createRoute(returnRoute = NavRoutes.LudoLobbyEntry.route))
                    }
                }
            )
        }

        composable(
            route = NavRoutes.LudoGame.route,
            arguments = listOf(
                navArgument("botCount") { type = NavType.IntType },
                navArgument("difficulty") { type = NavType.StringType },
                navArgument("color") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val botCount = backStackEntry.arguments?.getInt("botCount") ?: 1
            val difficulty = backStackEntry.arguments?.getString("difficulty") ?: "normal"
            val color = backStackEntry.arguments?.getString("color") ?: "RED"

            LudoOfflineGameScreen(
                botCount = botCount,
                difficulty = difficulty,
                color = color,
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
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        navController.navigate(NavRoutes.OnlineLobby.route)
                    } else {
                        navController.navigate(NavRoutes.Auth.createRoute(returnRoute = NavRoutes.OnlineLobby.route))
                    }
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
                    navController.navigate(NavRoutes.Auth.createRoute()) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSignIn = {
                    navController.navigate(NavRoutes.Auth.createRoute(returnRoute = NavRoutes.Settings.route))
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
