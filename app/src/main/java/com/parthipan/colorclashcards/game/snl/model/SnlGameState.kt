package com.parthipan.colorclashcards.game.snl.model

import com.parthipan.colorclashcards.game.ludo.model.GameStatus

/**
 * Complete immutable state of a Snake & Ladder game.
 *
 * @property players List of 2-4 players
 * @property currentTurnPlayerId ID of the player whose turn it is
 * @property diceValue Current dice roll result (null if not rolled yet)
 * @property lastMove The most recent move
 * @property winnerId ID of the winning player (null if not finished)
 * @property gameStatus Current game lifecycle status
 * @property boardLayout Classic or randomized board
 * @property snakes Map of snake head position to tail position
 * @property ladders Map of ladder bottom to top position
 * @property consecutiveSixes Count of consecutive 6s (3 = lose turn)
 * @property canRollDice Whether current player can roll
 * @property finishOrder Ordered list of player IDs who finished
 */
data class SnlGameState(
    val players: List<SnlPlayer>,
    val currentTurnPlayerId: String,
    val diceValue: Int? = null,
    val lastMove: SnlMove? = null,
    val winnerId: String? = null,
    val gameStatus: GameStatus = GameStatus.WAITING,
    val boardLayout: SnlBoardLayout = SnlBoardLayout.CLASSIC,
    val snakes: Map<Int, Int> = emptyMap(),
    val ladders: Map<Int, Int> = emptyMap(),
    val consecutiveSixes: Int = 0,
    val canRollDice: Boolean = true,
    val finishOrder: List<String> = emptyList()
) {
    val currentPlayer: SnlPlayer
        get() = players.first { it.id == currentTurnPlayerId }

    val currentPlayerIndex: Int
        get() = players.indexOfFirst { it.id == currentTurnPlayerId }

    val isGameOver: Boolean
        get() = gameStatus == GameStatus.FINISHED || winnerId != null

    val isInProgress: Boolean
        get() = gameStatus == GameStatus.IN_PROGRESS

    val winner: SnlPlayer?
        get() = winnerId?.let { id -> players.find { it.id == id } }

    fun getPlayer(playerId: String): SnlPlayer? =
        players.find { it.id == playerId }

    fun updatePlayer(updatedPlayer: SnlPlayer): SnlGameState =
        copy(players = players.map { if (it.id == updatedPlayer.id) updatedPlayer else it })

    fun getNextPlayer(): SnlPlayer {
        val currentIndex = players.indexOfFirst { it.id == currentTurnPlayerId }
        var nextIndex = (currentIndex + 1) % players.size
        repeat(players.size) {
            if (!players[nextIndex].hasWon) return players[nextIndex]
            nextIndex = (nextIndex + 1) % players.size
        }
        return players[nextIndex]
    }

    fun getPlayerRank(playerId: String): Int? {
        val index = finishOrder.indexOf(playerId)
        return if (index >= 0) index + 1 else null
    }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 4
        const val MAX_CONSECUTIVE_SIXES = 3
        const val WINNING_SQUARE = 100

        /**
         * Create an offline game vs bots.
         */
        fun createOfflineGame(
            humanName: String,
            botCount: Int,
            humanColorIndex: Int = 0,
            layout: SnlBoardLayout = SnlBoardLayout.CLASSIC
        ): SnlGameState {
            require(botCount in 1..3) { "Bot count must be 1-3" }

            val humanPlayer = SnlPlayer.human(humanName, humanColorIndex)
            val usedIndices = mutableSetOf(humanColorIndex)
            val botPlayers = (1..botCount).map { index ->
                val colorIdx = (0..3).first { it !in usedIndices }.also { usedIndices.add(it) }
                SnlPlayer.bot("Bot $index", colorIdx)
            }

            val allPlayers = listOf(humanPlayer) + botPlayers
            val (snakes, ladders) = when (layout) {
                SnlBoardLayout.CLASSIC -> {
                    com.parthipan.colorclashcards.game.snl.engine.SnlBoard.CLASSIC_SNAKES to
                        com.parthipan.colorclashcards.game.snl.engine.SnlBoard.CLASSIC_LADDERS
                }
                SnlBoardLayout.RANDOMIZED -> {
                    com.parthipan.colorclashcards.game.snl.engine.SnlBoard.generateRandomLayout()
                }
            }

            return SnlGameState(
                players = allPlayers,
                currentTurnPlayerId = humanPlayer.id,
                gameStatus = GameStatus.IN_PROGRESS,
                boardLayout = layout,
                snakes = snakes,
                ladders = ladders,
                canRollDice = true
            )
        }

        /**
         * Create an online multiplayer game.
         */
        fun createOnlineGame(
            players: List<SnlPlayer>,
            startingPlayerId: String,
            layout: SnlBoardLayout = SnlBoardLayout.CLASSIC,
            snakes: Map<Int, Int>,
            ladders: Map<Int, Int>
        ): SnlGameState {
            require(players.size in MIN_PLAYERS..MAX_PLAYERS)

            return SnlGameState(
                players = players,
                currentTurnPlayerId = startingPlayerId,
                gameStatus = GameStatus.IN_PROGRESS,
                boardLayout = layout,
                snakes = snakes,
                ladders = ladders,
                canRollDice = true
            )
        }
    }
}
