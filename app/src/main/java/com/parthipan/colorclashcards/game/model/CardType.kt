package com.parthipan.colorclashcards.game.model

/**
 * Types of cards in Color Clash Cards.
 */
enum class CardType {
    NUMBER,      // 0-9 cards
    SKIP,        // Next player loses their turn
    REVERSE,     // Reverses play direction
    DRAW_TWO,    // Next player draws 2 and loses turn
    WILD_COLOR,  // Player chooses the next color
    WILD_DRAW_FOUR;  // Player chooses color, next draws 4 and loses turn

    fun isAction(): Boolean = this in listOf(SKIP, REVERSE, DRAW_TWO)

    fun isWild(): Boolean = this in listOf(WILD_COLOR, WILD_DRAW_FOUR)

    fun isNumber(): Boolean = this == NUMBER

    /**
     * Get the point value for this card type.
     * Number cards use their face value (handled in Card class).
     * Action cards (Skip, Reverse, Draw Two): 20 points
     * Wild cards (Wild, Wild +4): 50 points
     */
    fun getBasePoints(): Int = when (this) {
        NUMBER -> 0  // Uses face value instead
        SKIP, REVERSE, DRAW_TWO -> 20
        WILD_COLOR, WILD_DRAW_FOUR -> 50
    }
}
