package com.parthipan.colorclashcards.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class NavRoutes(val route: String) {
    object Splash : NavRoutes("splash")
    object Auth : NavRoutes("auth?returnRoute={returnRoute}") {
        /** Navigate to Auth with an optional return destination after sign-in. */
        fun createRoute(returnRoute: String? = null): String {
            return if (returnRoute != null) "auth?returnRoute=$returnRoute" else "auth"
        }
    }
    object GameHub : NavRoutes("game_hub")
    object Home : NavRoutes("home")
    object LudoHome : NavRoutes("ludo_home")
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
    object LudoGame : NavRoutes("ludo_game/{botCount}/{difficulty}/{color}") {
        fun createRoute(botCount: Int, difficulty: String, color: String = "RED"): String {
            return "ludo_game/$botCount/$difficulty/$color"
        }
    }
    object LudoLobbyEntry : NavRoutes("ludo_lobby_entry")
    object LudoRoomLobby : NavRoutes("ludo_room_lobby/{roomId}") {
        fun createRoute(roomId: String): String {
            return "ludo_room_lobby/$roomId"
        }
    }
    object LudoOnlineGame : NavRoutes("ludo_online_game/{roomId}/{isHost}") {
        fun createRoute(roomId: String, isHost: Boolean): String {
            return "ludo_online_game/$roomId/$isHost"
        }
    }
}
