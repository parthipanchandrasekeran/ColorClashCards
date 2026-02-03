package com.parthipan.colorclashcards.game.ludo.model

/**
 * Represents a move made in the Ludo game.
 * Used for tracking game history and for online synchronization.
 *
 * @property playerId ID of the player who made the move
 * @property tokenId ID of the token that was moved (0-3)
 * @property diceValue The dice value that was rolled
 * @property fromPosition Starting position of the token
 * @property toPosition Ending position of the token
 * @property moveType Type of move that occurred
 * @property capturedTokenInfo Info about any token that was captured (if applicable)
 * @property timestamp When the move was made (milliseconds since epoch)
 */
data class LudoMove(
    val playerId: String,
    val tokenId: Int,
    val diceValue: Int,
    val fromPosition: Int,
    val toPosition: Int,
    val moveType: MoveType,
    val capturedTokenInfo: CapturedTokenInfo? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of moves that can occur in Ludo.
 */
enum class MoveType {
    /**
     * Token moved from home to starting position (requires 6).
     */
    EXIT_HOME,

    /**
     * Token moved along the track.
     */
    NORMAL,

    /**
     * Token entered the home stretch (colored final path).
     */
    ENTER_HOME_STRETCH,

    /**
     * Token reached the finish/center.
     */
    FINISH,

    /**
     * Token captured an opponent's token.
     */
    CAPTURE,

    /**
     * Player couldn't move any token (skipped turn).
     */
    SKIP
}

/**
 * Information about a captured token.
 *
 * @property playerId ID of the player whose token was captured
 * @property tokenId ID of the captured token
 * @property position Position where the capture occurred
 */
data class CapturedTokenInfo(
    val playerId: String,
    val tokenId: Int,
    val position: Int
)
