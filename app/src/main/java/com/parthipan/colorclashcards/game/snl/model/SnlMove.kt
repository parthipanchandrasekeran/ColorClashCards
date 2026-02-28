package com.parthipan.colorclashcards.game.snl.model

/**
 * Represents a move in Snake & Ladder.
 *
 * @property playerId ID of the player who moved
 * @property diceValue The dice value rolled (1-6)
 * @property fromPos Position before dice roll
 * @property toPos Position after dice move (before snake/ladder)
 * @property finalPos Final position after snake/ladder resolution
 * @property moveType What happened during this move
 * @property timestamp When the move occurred
 */
data class SnlMove(
    val playerId: String,
    val diceValue: Int,
    val fromPos: Int,
    val toPos: Int,
    val finalPos: Int,
    val moveType: SnlMoveType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of moves in Snake & Ladder.
 */
enum class SnlMoveType {
    /** Player is off-board and must roll a 1 to enter the board. */
    NEED_ONE_TO_START,
    /** Normal move — no snake or ladder. */
    NORMAL,
    /** Landed on a snake head — slid down. */
    SNAKE_BITE,
    /** Landed on a ladder bottom — climbed up. */
    LADDER_CLIMB,
    /** Dice would take past 100 — stay in place. */
    OVERSHOOT,
    /** Reached exactly 100 — player wins. */
    WIN
}
