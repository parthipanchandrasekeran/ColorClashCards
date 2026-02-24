package com.parthipan.colorclashcards.game.snl.model

import java.util.UUID

/**
 * Represents a player in Snake & Ladder.
 * Each player has a single token (position 0 = off board, 1-100 = on board).
 *
 * @property id Unique identifier
 * @property name Display name
 * @property colorIndex Player color index (0-3), maps to standard Red/Blue/Green/Yellow
 * @property position Current position (0 = not started, 1-100 = board square)
 * @property isBot Whether this player is AI-controlled
 * @property isOnline Whether this is an online multiplayer player
 */
data class SnlPlayer(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val position: Int = 0,
    val isBot: Boolean = false,
    val isOnline: Boolean = false
) {
    /** Whether this player has reached square 100. */
    val hasWon: Boolean get() = position == 100

    /** Whether this player is on the board (has started). */
    val isOnBoard: Boolean get() = position > 0

    companion object {
        fun human(name: String, colorIndex: Int): SnlPlayer {
            return SnlPlayer(
                id = UUID.randomUUID().toString(),
                name = name,
                colorIndex = colorIndex
            )
        }

        fun bot(name: String, colorIndex: Int): SnlPlayer {
            return SnlPlayer(
                id = UUID.randomUUID().toString(),
                name = name,
                colorIndex = colorIndex,
                isBot = true
            )
        }

        fun online(id: String, name: String, colorIndex: Int): SnlPlayer {
            return SnlPlayer(
                id = id,
                name = name,
                colorIndex = colorIndex,
                isOnline = true
            )
        }
    }
}
