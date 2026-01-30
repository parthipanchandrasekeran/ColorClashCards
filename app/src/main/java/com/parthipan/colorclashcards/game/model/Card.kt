package com.parthipan.colorclashcards.game.model

import java.util.UUID

/**
 * Represents a card in Color Clash Cards.
 *
 * @property id Unique identifier for this card instance
 * @property color The card's color (WILD for wild cards)
 * @property type The type of card (NUMBER, SKIP, REVERSE, etc.)
 * @property number The number value (0-9) for NUMBER cards, null otherwise
 */
data class Card(
    val id: String = UUID.randomUUID().toString(),
    val color: CardColor,
    val type: CardType,
    val number: Int? = null
) {
    init {
        require(type != CardType.NUMBER || (number != null && number in 0..9)) {
            "NUMBER cards must have a number between 0 and 9"
        }
        require(type == CardType.NUMBER || number == null) {
            "Non-NUMBER cards must not have a number value"
        }
        require(!type.isWild() || color == CardColor.WILD) {
            "Wild cards must have WILD color"
        }
        require(type.isWild() || color != CardColor.WILD) {
            "Non-wild cards cannot have WILD color"
        }
    }

    /**
     * Check if this card can be played on top of another card.
     *
     * @param topCard The card currently on top of the discard pile
     * @param currentColor The current active color (may differ from topCard if wild was played)
     */
    fun canPlayOn(topCard: Card, currentColor: CardColor): Boolean {
        // Wild cards can always be played
        if (type.isWild()) return true

        // Match by color
        if (color == currentColor) return true

        // Match by number (for number cards)
        if (type == CardType.NUMBER && topCard.type == CardType.NUMBER && number == topCard.number) {
            return true
        }

        // Match by action type (e.g., SKIP on SKIP)
        if (type.isAction() && type == topCard.type) return true

        return false
    }

    /**
     * Get display name for the card.
     */
    fun displayName(): String {
        return when (type) {
            CardType.NUMBER -> "${color.name} $number"
            CardType.SKIP -> "${color.name} SKIP"
            CardType.REVERSE -> "${color.name} REVERSE"
            CardType.DRAW_TWO -> "${color.name} +2"
            CardType.WILD_COLOR -> "WILD"
            CardType.WILD_DRAW_FOUR -> "WILD +4"
        }
    }

    /**
     * Get the point value of this card for scoring.
     * Number cards (0-9): face value
     * Skip / Reverse / Draw Two: 20 points
     * Wild / Wild +4: 50 points
     */
    fun getPoints(): Int {
        return if (type == CardType.NUMBER) {
            number ?: 0
        } else {
            type.getBasePoints()
        }
    }

    companion object {
        /**
         * Create a number card.
         */
        fun number(color: CardColor, number: Int): Card {
            require(color != CardColor.WILD) { "Number cards cannot be WILD" }
            return Card(color = color, type = CardType.NUMBER, number = number)
        }

        /**
         * Create a skip card.
         */
        fun skip(color: CardColor): Card {
            require(color != CardColor.WILD) { "Skip cards cannot be WILD" }
            return Card(color = color, type = CardType.SKIP)
        }

        /**
         * Create a reverse card.
         */
        fun reverse(color: CardColor): Card {
            require(color != CardColor.WILD) { "Reverse cards cannot be WILD" }
            return Card(color = color, type = CardType.REVERSE)
        }

        /**
         * Create a draw two card.
         */
        fun drawTwo(color: CardColor): Card {
            require(color != CardColor.WILD) { "Draw Two cards cannot be WILD" }
            return Card(color = color, type = CardType.DRAW_TWO)
        }

        /**
         * Create a wild color card.
         */
        fun wildColor(): Card {
            return Card(color = CardColor.WILD, type = CardType.WILD_COLOR)
        }

        /**
         * Create a wild draw four card.
         */
        fun wildDrawFour(): Card {
            return Card(color = CardColor.WILD, type = CardType.WILD_DRAW_FOUR)
        }
    }
}
