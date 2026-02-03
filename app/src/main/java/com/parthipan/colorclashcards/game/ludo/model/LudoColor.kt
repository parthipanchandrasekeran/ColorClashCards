package com.parthipan.colorclashcards.game.ludo.model

/**
 * Represents the four player colors in Ludo.
 * Each color corresponds to a quadrant on the board.
 */
enum class LudoColor {
    RED,
    BLUE,
    GREEN,
    YELLOW;

    companion object {
        /**
         * Get the color for a given player index (0-3).
         */
        fun forPlayerIndex(index: Int): LudoColor {
            return entries[index % entries.size]
        }

        /**
         * Get all colors for a given number of players.
         * For 2 players: RED and YELLOW (opposite corners)
         * For 3 players: RED, BLUE, GREEN
         * For 4 players: All colors
         */
        fun forPlayerCount(count: Int): List<LudoColor> {
            return when (count) {
                2 -> listOf(RED, YELLOW)
                3 -> listOf(RED, BLUE, GREEN)
                else -> entries.toList()
            }
        }
    }
}
