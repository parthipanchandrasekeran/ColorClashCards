package com.parthipan.colorclashcards.game.model

/**
 * Card colors in Color Clash Cards.
 */
enum class CardColor {
    RED,
    GREEN,
    BLUE,
    YELLOW,
    WILD;  // For wild cards that have no specific color

    fun isWild(): Boolean = this == WILD

    companion object {
        fun playableColors(): List<CardColor> = listOf(RED, GREEN, BLUE, YELLOW)
    }
}
