package com.parthipan.colorclashcards.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class NavRoutes(val route: String) {
    object Splash : NavRoutes("splash")
    object Auth : NavRoutes("auth")
    object Home : NavRoutes("home")
    object HowToPlay : NavRoutes("how_to_play")
    object Settings : NavRoutes("settings")
    object Privacy : NavRoutes("privacy")
    object SoloSetup : NavRoutes("solo_setup")
    object OnlineLobby : NavRoutes("online_lobby")
    object RoomLobby : NavRoutes("room_lobby/{roomId}") {
        fun createRoute(roomId: String): String {
            return "room_lobby/$roomId"
        }
    }
    object Game : NavRoutes("game/{mode}/{botCount}/{difficulty}") {
        fun createRoute(mode: String, botCount: Int = 0, difficulty: String = "normal"): String {
            return "game/$mode/$botCount/$difficulty"
        }
    }
    object OnlineGame : NavRoutes("online_game/{roomId}/{isHost}") {
        fun createRoute(roomId: String, isHost: Boolean): String {
            return "online_game/$roomId/$isHost"
        }
    }
}
