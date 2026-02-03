package com.parthipan.colorclashcards.game.ludo.model

/**
 * Represents the complete state of a Ludo game.
 * Immutable data class for easy state management with MVVM.
 *
 * @property players List of players in the game (2-4)
 * @property currentTurnPlayerId ID of the player whose turn it is
 * @property diceValue Current dice value (1-6, or null if not rolled yet)
 * @property lastMove The most recent move made (for UI animations/history)
 * @property moveHistory Complete history of moves
 * @property winnerId ID of the winning player (null if game not finished)
 * @property gameStatus Current status of the game
 * @property consecutiveSixes Count of consecutive 6s rolled (3 = lose turn)
 * @property canRollDice Whether the current player can roll the dice
 * @property mustSelectToken Whether the current player must select a token to move
 */
data class LudoGameState(
    val players: List<LudoPlayer>,
    val currentTurnPlayerId: String,
    val diceValue: Int? = null,
    val lastMove: LudoMove? = null,
    val moveHistory: List<LudoMove> = emptyList(),
    val winnerId: String? = null,
    val gameStatus: GameStatus = GameStatus.WAITING,
    val consecutiveSixes: Int = 0,
    val canRollDice: Boolean = true,
    val mustSelectToken: Boolean = false,
    val finishOrder: List<String> = emptyList()
) {
    /**
     * Get the current player.
     */
    val currentPlayer: LudoPlayer
        get() = players.first { it.id == currentTurnPlayerId }

    /**
     * Get the current player's index.
     */
    val currentPlayerIndex: Int
        get() = players.indexOfFirst { it.id == currentTurnPlayerId }

    /**
     * Check if the game is over.
     */
    val isGameOver: Boolean
        get() = gameStatus == GameStatus.FINISHED || winnerId != null

    /**
     * Check if the game is in progress.
     */
    val isInProgress: Boolean
        get() = gameStatus == GameStatus.IN_PROGRESS

    /**
     * Get the winner player (if game is finished).
     */
    val winner: LudoPlayer?
        get() = winnerId?.let { id -> players.find { it.id == id } }

    /**
     * Get a player by ID.
     */
    fun getPlayer(playerId: String): LudoPlayer? {
        return players.find { it.id == playerId }
    }

    /**
     * Get a player by color.
     */
    fun getPlayerByColor(color: LudoColor): LudoPlayer? {
        return players.find { it.color == color }
    }

    /**
     * Update a player and return a new game state.
     */
    fun updatePlayer(updatedPlayer: LudoPlayer): LudoGameState {
        return copy(
            players = players.map { if (it.id == updatedPlayer.id) updatedPlayer else it }
        )
    }

    /**
     * Get the next player in turn order, skipping finished players.
     */
    fun getNextPlayer(): LudoPlayer {
        val currentIndex = players.indexOfFirst { it.id == currentTurnPlayerId }
        var nextIndex = (currentIndex + 1) % players.size
        repeat(players.size) {
            if (!players[nextIndex].hasWon()) return players[nextIndex]
            nextIndex = (nextIndex + 1) % players.size
        }
        return players[nextIndex] // fallback
    }

    /**
     * Number of players who have not yet finished all tokens.
     */
    val activePlayerCount: Int
        get() = players.count { !it.hasWon() }

    /**
     * Get a player's finishing rank (1-based), or null if not yet finished.
     */
    fun getPlayerRank(playerId: String): Int? {
        val index = finishOrder.indexOf(playerId)
        return if (index >= 0) index + 1 else null
    }

    /**
     * Check if any player has won.
     */
    fun checkForWinner(): LudoPlayer? {
        return players.find { it.hasWon() }
    }

    /**
     * Get all tokens on a specific board position.
     * Used for checking captures and collisions.
     *
     * @param position The board position to check
     * @param excludePlayerId Optional player ID to exclude from results
     * @return List of pairs (Player, Token) at that position
     */
    fun getTokensAtPosition(position: Int, excludePlayerId: String? = null): List<Pair<LudoPlayer, Token>> {
        return players
            .filter { excludePlayerId == null || it.id != excludePlayerId }
            .flatMap { player ->
                player.tokens
                    .filter { it.state == TokenState.ACTIVE && it.position == position }
                    .map { token -> player to token }
            }
    }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 4
        const val TOKENS_PER_PLAYER = 4
        const val MAX_CONSECUTIVE_SIXES = 3

        /**
         * Create initial game state for offline play.
         *
         * @param humanName Name of the human player
         * @param botCount Number of bot opponents (1-3)
         * @return Initial game state ready to start
         */
        fun createOfflineGame(humanName: String, botCount: Int): LudoGameState {
            require(botCount in 1..3) { "Bot count must be between 1 and 3" }

            val playerCount = botCount + 1
            val colors = LudoColor.forPlayerCount(playerCount)

            val humanPlayer = LudoPlayer.human(humanName, colors[0])
            val botPlayers = (1..botCount).map { index ->
                LudoPlayer.bot("Bot $index", colors[index])
            }

            val allPlayers = listOf(humanPlayer) + botPlayers

            return LudoGameState(
                players = allPlayers,
                currentTurnPlayerId = humanPlayer.id,
                gameStatus = GameStatus.IN_PROGRESS,
                canRollDice = true
            )
        }

        /**
         * Create initial game state for online multiplayer.
         *
         * @param players List of online players
         * @param startingPlayerId ID of the player who goes first
         * @return Initial game state ready to start
         */
        fun createOnlineGame(
            players: List<LudoPlayer>,
            startingPlayerId: String
        ): LudoGameState {
            require(players.size in MIN_PLAYERS..MAX_PLAYERS) {
                "Player count must be between $MIN_PLAYERS and $MAX_PLAYERS"
            }

            return LudoGameState(
                players = players,
                currentTurnPlayerId = startingPlayerId,
                gameStatus = GameStatus.IN_PROGRESS,
                canRollDice = true
            )
        }
    }
}
