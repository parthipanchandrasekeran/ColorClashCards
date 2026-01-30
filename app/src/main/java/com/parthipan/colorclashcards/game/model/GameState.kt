package com.parthipan.colorclashcards.game.model

/**
 * Represents the direction of play.
 */
enum class PlayDirection {
    CLOCKWISE,
    COUNTER_CLOCKWISE;

    fun reversed(): PlayDirection = when (this) {
        CLOCKWISE -> COUNTER_CLOCKWISE
        COUNTER_CLOCKWISE -> CLOCKWISE
    }
}

/**
 * Represents the current phase of a turn.
 */
enum class TurnPhase {
    PLAY_OR_DRAW,    // Player can play a card or draw (start of turn)
    DREW_CARD,       // Player drew a card - can play it or pass (no more draws allowed)
    MUST_DRAW,       // Player must draw (due to +2 or +4)
    CHOOSE_COLOR,    // Player must choose a color (after playing wild)
    TURN_ENDED       // Turn is complete, advance to next player
}

/**
 * Represents the overall game/match phase.
 */
enum class GamePhase {
    PLAYING,         // Round is in progress
    ROUND_OVER,      // Round ended, showing summary
    MATCH_OVER       // All 10 rounds complete
}

/**
 * Represents how the round ended.
 */
enum class RoundEndReason {
    NORMAL_WIN,      // A player played their last card
    TIMEOUT          // Sudden death timer expired
}

/**
 * Represents the complete game state.
 */
data class GameState(
    val players: List<Player>,
    val deck: List<Card>,
    val discardPile: List<Card>,
    val currentPlayerIndex: Int,
    val direction: PlayDirection = PlayDirection.CLOCKWISE,
    val currentColor: CardColor,
    val turnPhase: TurnPhase = TurnPhase.PLAY_OR_DRAW,
    val pendingDrawCount: Int = 0,
    val winner: Player? = null,
    val lastCardTimer: Long? = null,  // Timestamp when player got 1 card
    val lastPlayedCard: Card? = null,
    // Match/Round fields
    val currentRound: Int = 1,
    val gamePhase: GamePhase = GamePhase.PLAYING,
    val roundWinnerId: String? = null,
    val roundPoints: Int = 0,
    val roundEndReason: RoundEndReason = RoundEndReason.NORMAL_WIN,
    // Timer fields
    val turnSecondsRemaining: Int = DEFAULT_TURN_SECONDS,
    val roundSecondsElapsed: Int = 0,
    val suddenDeathActive: Boolean = false,
    val suddenDeathSecondsRemaining: Int = SUDDEN_DEATH_SECONDS
) {
    companion object {
        const val TOTAL_ROUNDS = 10
        const val DEFAULT_TURN_SECONDS = 30       // 30 seconds per turn
        const val ROUND_TIME_LIMIT_SECONDS = 300  // 5 minutes
        const val SUDDEN_DEATH_SECONDS = 60       // 1 minute (final minute)
    }
    /**
     * Get the current player.
     */
    val currentPlayer: Player
        get() = players[currentPlayerIndex]

    /**
     * Get the top card of the discard pile.
     */
    val topCard: Card?
        get() = discardPile.lastOrNull()

    /**
     * Check if round is over (a player won).
     */
    val isRoundOver: Boolean
        get() = gamePhase == GamePhase.ROUND_OVER

    /**
     * Check if entire match is over (all 10 rounds complete).
     */
    val isMatchOver: Boolean
        get() = gamePhase == GamePhase.MATCH_OVER

    /**
     * Check if game is over (legacy - round ended).
     */
    val isGameOver: Boolean
        get() = winner != null || gamePhase != GamePhase.PLAYING

    /**
     * Check if this is the final round.
     */
    val isFinalRound: Boolean
        get() = currentRound == TOTAL_ROUNDS

    /**
     * Get the round winner player object.
     */
    val roundWinner: Player?
        get() = roundWinnerId?.let { id -> players.find { it.id == id } }

    /**
     * Get the match winner (player with highest score after final round).
     */
    val matchWinner: Player?
        get() = if (isMatchOver) players.maxByOrNull { it.totalScore } else null

    /**
     * Check if sudden death should be active (round time exceeded limit).
     */
    val shouldActivateSuddenDeath: Boolean
        get() = !suddenDeathActive && roundSecondsElapsed >= ROUND_TIME_LIMIT_SECONDS

    /**
     * Format round elapsed time as mm:ss.
     */
    val roundTimeFormatted: String
        get() {
            val minutes = roundSecondsElapsed / 60
            val seconds = roundSecondsElapsed % 60
            return "%d:%02d".format(minutes, seconds)
        }

    /**
     * Get the index of the next player.
     */
    fun getNextPlayerIndex(): Int {
        val delta = if (direction == PlayDirection.CLOCKWISE) 1 else -1
        return (currentPlayerIndex + delta + players.size) % players.size
    }

    /**
     * Get the next player.
     */
    fun getNextPlayer(): Player = players[getNextPlayerIndex()]

    /**
     * Update a specific player in the state.
     */
    fun updatePlayer(player: Player): GameState {
        val index = players.indexOfFirst { it.id == player.id }
        if (index < 0) return this
        val newPlayers = players.toMutableList()
        newPlayers[index] = player
        return copy(players = newPlayers)
    }

    /**
     * Get player by ID.
     */
    fun getPlayer(playerId: String): Player? {
        return players.find { it.id == playerId }
    }
}
