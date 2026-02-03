package com.parthipan.colorclashcards.game.ludo.model

/**
 * Represents the overall status of a Ludo game.
 */
enum class GameStatus {
    /**
     * Game is waiting for players to join.
     * Used in online multiplayer lobbies.
     */
    WAITING,

    /**
     * Game is actively being played.
     */
    IN_PROGRESS,

    /**
     * Game has finished. A winner has been determined.
     */
    FINISHED
}
