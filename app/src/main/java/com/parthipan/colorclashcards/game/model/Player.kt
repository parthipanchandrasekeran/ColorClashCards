package com.parthipan.colorclashcards.game.model

import java.util.UUID

/**
 * Represents a player in the game.
 *
 * @property id Unique identifier
 * @property name Display name
 * @property isBot Whether this player is controlled by AI
 * @property hand Cards currently held by the player
 * @property hasCalledLastCard Whether player called "Last Card!" when having 1 card
 * @property totalScore Accumulated score across rounds in a match
 */
data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isBot: Boolean = false,
    val hand: List<Card> = emptyList(),
    val hasCalledLastCard: Boolean = false,
    val totalScore: Int = 0
) {
    /**
     * Number of cards in hand.
     */
    val cardCount: Int get() = hand.size

    /**
     * Check if player has won (no cards left).
     */
    val hasWon: Boolean get() = hand.isEmpty()

    /**
     * Check if player needs to call "Last Card!".
     */
    val needsLastCardCall: Boolean get() = hand.size == 1 && !hasCalledLastCard

    /**
     * Add cards to hand.
     */
    fun addCards(cards: List<Card>): Player {
        return copy(hand = hand + cards, hasCalledLastCard = false)
    }

    /**
     * Remove a card from hand.
     */
    fun removeCard(card: Card): Player {
        val newHand = hand.toMutableList()
        val index = newHand.indexOfFirst { it.id == card.id }
        if (index >= 0) {
            newHand.removeAt(index)
        }
        return copy(hand = newHand)
    }

    /**
     * Mark that player has called "Last Card!".
     */
    fun callLastCard(): Player {
        return copy(hasCalledLastCard = true)
    }

    /**
     * Reset last card call status.
     */
    fun resetLastCardCall(): Player {
        return copy(hasCalledLastCard = false)
    }

    /**
     * Add points to total score.
     */
    fun addScore(points: Int): Player {
        return copy(totalScore = totalScore + points)
    }

    /**
     * Reset score to zero (for new match).
     */
    fun resetScore(): Player {
        return copy(totalScore = 0)
    }

    /**
     * Reset hand and last card call for new round (keep score).
     */
    fun resetForNewRound(): Player {
        return copy(hand = emptyList(), hasCalledLastCard = false)
    }

    companion object {
        fun human(name: String): Player = Player(name = name, isBot = false)
        fun bot(name: String): Player = Player(name = name, isBot = true)
    }
}
