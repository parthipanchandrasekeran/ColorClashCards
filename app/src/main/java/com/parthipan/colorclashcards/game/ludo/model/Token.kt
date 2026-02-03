package com.parthipan.colorclashcards.game.ludo.model

/**
 * Sealed class representing the distinct location types for a token.
 * Provides type-safe position handling without magic numbers.
 */
sealed class TokenPosition {
    /**
     * Token is at home base, waiting to be released with a 6.
     * @property slot Home slot index (0-3) within the home base
     */
    data class Home(val slot: Int) : TokenPosition()

    /**
     * Token is on the main ring track (shared path).
     * @property ringIndex Relative position on the ring (0-50)
     */
    data class OnRing(val ringIndex: Int) : TokenPosition()

    /**
     * Token is in the finish lane (colored path to center).
     * @property laneIndex Position in the lane (0-5, where 5 is closest to center)
     */
    data class InLane(val laneIndex: Int) : TokenPosition()

    /**
     * Token has reached the center and finished.
     */
    data object Finished : TokenPosition()

    /**
     * Convert to legacy position int for backward compatibility.
     */
    fun toLegacyPosition(): Int = when (this) {
        is Home -> -1
        is OnRing -> ringIndex
        is InLane -> 51 + laneIndex // Lane positions are 51-56
        is Finished -> 57
    }

    companion object {
        /**
         * Create TokenPosition from legacy position int and state.
         */
        fun fromLegacy(position: Int, state: TokenState, homeSlot: Int = 0): TokenPosition {
            return when (state) {
                TokenState.HOME -> Home(homeSlot)
                TokenState.FINISHED -> Finished
                TokenState.ACTIVE -> {
                    when {
                        position < 0 -> Home(homeSlot)
                        position <= 50 -> OnRing(position)
                        position <= 56 -> InLane(position - 51)
                        else -> Finished
                    }
                }
            }
        }
    }
}

/**
 * Represents a single token/piece in Ludo.
 * Each player has 4 tokens.
 *
 * @property id Unique identifier for this token (0-3 within a player)
 * @property state Current state of the token (HOME, ACTIVE, FINISHED)
 * @property position Position on the board track (0-56 for main track, -1 for home)
 *                    Position is relative to the player's starting point.
 *                    - -1: Token is at HOME
 *                    - 0: Starting position (just left home on ring)
 *                    - 1-50: Ring positions (main track)
 *                    - 51-56: Finish lane (colored path to center)
 *                    - 57: Finished (reached center)
 */
data class Token(
    val id: Int,
    val state: TokenState = TokenState.HOME,
    val position: Int = -1
) {
    /**
     * Get the type-safe token position.
     */
    val tokenPosition: TokenPosition
        get() = TokenPosition.fromLegacy(position, state, id)

    /**
     * Check if token is on the main ring (positions 0-50).
     */
    val isOnRing: Boolean
        get() = state == TokenState.ACTIVE && position in 0..50

    /**
     * Check if token is in the finish lane (positions 51-56).
     */
    val isInLane: Boolean
        get() = state == TokenState.ACTIVE && position in 51..56

    /**
     * Get the ring index if on ring, null otherwise.
     */
    val ringIndex: Int?
        get() = if (isOnRing) position else null

    /**
     * Get the lane index (0-5) if in lane, null otherwise.
     */
    val laneIndex: Int?
        get() = if (isInLane) position - 51 else null

    /**
     * Check if this token can be moved with the given dice value.
     */
    fun canMove(diceValue: Int): Boolean {
        return when (state) {
            TokenState.HOME -> diceValue == 6
            TokenState.ACTIVE -> {
                // Can move if the new position doesn't exceed the finish
                val newPosition = position + diceValue
                newPosition <= FINISH_POSITION
            }
            TokenState.FINISHED -> false
        }
    }

    /**
     * Check if this token is inherently safe from being captured.
     * Tokens are safe in home, finish, or in the finish lane.
     *
     * For ring safety (star/start cells), callers must use
     * [com.parthipan.colorclashcards.game.ludo.engine.LudoBoard.isSafeCell]
     * with the token's absolute position.
     */
    fun isSafe(): Boolean {
        return state == TokenState.HOME ||
               state == TokenState.FINISHED ||
               isInLane // Tokens in finish lane are always safe
    }

    companion object {
        const val FINISH_POSITION = 57
        const val LANE_START = 51
        const val RING_END = 50

        // Legacy alias
        @Deprecated("Use LANE_START", ReplaceWith("LANE_START"))
        const val HOME_STRETCH_START = LANE_START

        /**
         * Create initial tokens for a player (all at home).
         */
        fun createInitialTokens(): List<Token> {
            return (0..3).map { Token(id = it) }
        }

        /**
         * Create a token at a specific ring position.
         */
        fun atRing(id: Int, ringIndex: Int): Token {
            require(ringIndex in 0..RING_END) { "Ring index must be 0-50" }
            return Token(id = id, state = TokenState.ACTIVE, position = ringIndex)
        }

        /**
         * Create a token in the finish lane.
         */
        fun inLane(id: Int, laneIndex: Int): Token {
            require(laneIndex in 0..5) { "Lane index must be 0-5" }
            return Token(id = id, state = TokenState.ACTIVE, position = LANE_START + laneIndex)
        }

        /**
         * Create a finished token.
         */
        fun finished(id: Int): Token {
            return Token(id = id, state = TokenState.FINISHED, position = FINISH_POSITION)
        }
    }
}
