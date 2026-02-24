package com.parthipan.colorclashcards.game.snl.engine

import kotlin.random.Random

/**
 * AI agent for Snake & Ladder bot players.
 * Trivial: just rolls the dice. No strategic decisions since there's only 1 token.
 */
object SnlBotAgent {

    /**
     * Simulate a dice roll.
     */
    fun rollDice(): Int = Random.nextInt(1, 7)

    /**
     * Get thinking delay to make the game feel natural.
     */
    fun getThinkingDelayMs(difficulty: String = "normal"): Long {
        return when (difficulty.lowercase()) {
            "easy" -> Random.nextLong(400, 800)
            "hard" -> Random.nextLong(800, 1500)
            else -> Random.nextLong(600, 1200)
        }
    }
}
