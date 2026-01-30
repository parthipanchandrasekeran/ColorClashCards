package com.parthipan.colorclashcards.game.engine

import com.parthipan.colorclashcards.game.model.Card
import com.parthipan.colorclashcards.game.model.CardColor

/**
 * Utility for building a standard Color Clash Cards deck.
 */
object DeckBuilder {

    /**
     * Create a standard 108-card deck.
     *
     * Contents:
     * - For each color (RED, GREEN, BLUE, YELLOW):
     *   - One 0 card
     *   - Two of each 1-9 cards (18 cards)
     *   - Two SKIP cards
     *   - Two REVERSE cards
     *   - Two DRAW_TWO cards
     * - Four WILD_COLOR cards
     * - Four WILD_DRAW_FOUR cards
     *
     * Total: 4 colors × (1 + 18 + 6) + 8 = 4 × 25 + 8 = 108 cards
     */
    fun createStandardDeck(): List<Card> {
        val cards = mutableListOf<Card>()

        // For each playable color
        for (color in CardColor.playableColors()) {
            // One 0 card
            cards.add(Card.number(color, 0))

            // Two of each 1-9
            for (number in 1..9) {
                cards.add(Card.number(color, number))
                cards.add(Card.number(color, number))
            }

            // Two of each action card
            repeat(2) {
                cards.add(Card.skip(color))
                cards.add(Card.reverse(color))
                cards.add(Card.drawTwo(color))
            }
        }

        // Four wild cards of each type
        repeat(4) {
            cards.add(Card.wildColor())
            cards.add(Card.wildDrawFour())
        }

        return cards
    }

    /**
     * Shuffle a deck of cards.
     */
    fun shuffle(deck: List<Card>): List<Card> {
        return deck.shuffled()
    }

    /**
     * Create and shuffle a standard deck.
     */
    fun createShuffledDeck(): List<Card> {
        return shuffle(createStandardDeck())
    }
}
