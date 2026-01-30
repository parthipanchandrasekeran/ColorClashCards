package com.parthipan.colorclashcards.game.engine

import com.parthipan.colorclashcards.game.model.*

/**
 * AI agent for bot players.
 * Makes strategic decisions about which card to play.
 */
object BotAgent {

    /**
     * Choose the best card to play from available options.
     * Priority:
     * 1. Action cards that help (skip, reverse, draw two)
     * 2. Cards matching current color
     * 3. Cards matching by number/type
     * 4. Wild cards (save for last)
     *
     * @param hand Bot's current hand
     * @param topCard Top card on discard pile
     * @param currentColor Current active color
     * @param difficulty Game difficulty (affects strategy)
     * @return The card to play, or null if should draw
     */
    fun chooseCard(
        hand: List<Card>,
        topCard: Card,
        currentColor: CardColor,
        difficulty: String = "normal"
    ): Card? {
        val playable = GameEngine.getPlayableCards(hand, topCard, currentColor)
        if (playable.isEmpty()) return null

        // Easy mode: just play randomly
        if (difficulty == "easy") {
            return playable.random()
        }

        // Normal mode: use strategy
        // 1. Prefer action cards (but not wild) to disrupt opponents
        val actionCards = playable.filter { it.type.isAction() }
        if (actionCards.isNotEmpty()) {
            // Prefer draw two > skip > reverse
            return actionCards.maxByOrNull { actionPriority(it.type) }
        }

        // 2. Prefer cards matching current color (to maintain control)
        val colorMatches = playable.filter { it.color == currentColor && !it.type.isWild() }
        if (colorMatches.isNotEmpty()) {
            // Play highest number to get rid of high-value cards
            return colorMatches.maxByOrNull { it.number ?: 0 }
        }

        // 3. Play number cards that match by number
        val numberMatches = playable.filter { it.type == CardType.NUMBER && !it.type.isWild() }
        if (numberMatches.isNotEmpty()) {
            return numberMatches.maxByOrNull { it.number ?: 0 }
        }

        // 4. Save wild cards for last
        val wildCards = playable.filter { it.type.isWild() }
        if (wildCards.isNotEmpty()) {
            // Prefer regular wild over wild draw four (save +4 for emergencies)
            return wildCards.minByOrNull { if (it.type == CardType.WILD_COLOR) 0 else 1 }
        }

        // Fallback: play any available card
        return playable.firstOrNull()
    }

    /**
     * Choose the best color when playing a wild card.
     * Picks the color the bot has the most cards of.
     *
     * @param hand Bot's current hand (after playing the wild)
     * @return The chosen color
     */
    fun chooseWildColor(hand: List<Card>): CardColor {
        if (hand.isEmpty()) {
            return CardColor.playableColors().random()
        }

        // Count cards of each color
        val colorCounts = CardColor.playableColors().associateWith { color ->
            hand.count { it.color == color }
        }

        // Return color with most cards, or random if tie
        return colorCounts.maxByOrNull { it.value }?.key
            ?: CardColor.playableColors().random()
    }

    /**
     * Decide whether to play a drawn card or keep it.
     * Bot always plays if possible (simple strategy).
     */
    fun shouldPlayDrawnCard(
        drawnCard: Card,
        topCard: Card,
        currentColor: CardColor
    ): Boolean {
        return drawnCard.canPlayOn(topCard, currentColor)
    }

    /**
     * Get priority score for action cards.
     */
    private fun actionPriority(type: CardType): Int {
        return when (type) {
            CardType.DRAW_TWO -> 3
            CardType.SKIP -> 2
            CardType.REVERSE -> 1
            else -> 0
        }
    }

    /**
     * Generate a random delay for bot thinking (makes game feel natural).
     */
    fun getThinkingDelayMs(): Long {
        return (500..1200).random().toLong()
    }
}
