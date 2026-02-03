package com.parthipan.colorclashcards.game.ludo.model

import java.util.UUID

/**
 * Represents a player in the Ludo game.
 *
 * @property id Unique identifier for the player
 * @property name Display name of the player
 * @property color The player's color (determines board quadrant)
 * @property tokens List of 4 tokens belonging to this player
 * @property isBot Whether this player is controlled by AI
 * @property isOnline Whether this is an online player (for multiplayer)
 */
data class LudoPlayer(
    val id: String,
    val name: String,
    val color: LudoColor,
    val tokens: List<Token> = Token.createInitialTokens(),
    val isBot: Boolean = false,
    val isOnline: Boolean = false
) {
    /**
     * Check if this player has any movable tokens for the given dice value.
     */
    fun hasMovableToken(diceValue: Int): Boolean {
        return tokens.any { it.canMove(diceValue) }
    }

    /**
     * Get all tokens that can be moved with the given dice value.
     */
    fun getMovableTokens(diceValue: Int): List<Token> {
        return tokens.filter { it.canMove(diceValue) }
    }

    /**
     * Check if this player has won (all tokens finished).
     */
    fun hasWon(): Boolean {
        return tokens.all { it.state == TokenState.FINISHED }
    }

    /**
     * Get the number of finished tokens.
     */
    fun finishedTokenCount(): Int {
        return tokens.count { it.state == TokenState.FINISHED }
    }

    /**
     * Get the number of active tokens (on the board).
     */
    fun activeTokenCount(): Int {
        return tokens.count { it.state == TokenState.ACTIVE }
    }

    /**
     * Get the number of tokens still at home.
     */
    fun homeTokenCount(): Int {
        return tokens.count { it.state == TokenState.HOME }
    }

    /**
     * Update a specific token and return a new player instance.
     */
    fun updateToken(tokenId: Int, newToken: Token): LudoPlayer {
        return copy(
            tokens = tokens.map { if (it.id == tokenId) newToken else it }
        )
    }

    /**
     * Get a specific token by ID.
     */
    fun getToken(tokenId: Int): Token? {
        return tokens.find { it.id == tokenId }
    }

    companion object {
        /**
         * Create a human player.
         */
        fun human(name: String, color: LudoColor): LudoPlayer {
            return LudoPlayer(
                id = UUID.randomUUID().toString(),
                name = name,
                color = color,
                isBot = false
            )
        }

        /**
         * Create a bot player.
         */
        fun bot(name: String, color: LudoColor): LudoPlayer {
            return LudoPlayer(
                id = UUID.randomUUID().toString(),
                name = name,
                color = color,
                isBot = true
            )
        }

        /**
         * Create an online player.
         */
        fun online(id: String, name: String, color: LudoColor): LudoPlayer {
            return LudoPlayer(
                id = id,
                name = name,
                color = color,
                isBot = false,
                isOnline = true
            )
        }
    }
}
