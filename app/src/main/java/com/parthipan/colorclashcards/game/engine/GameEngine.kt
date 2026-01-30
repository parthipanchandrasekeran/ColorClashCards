package com.parthipan.colorclashcards.game.engine

import com.parthipan.colorclashcards.game.model.*

/**
 * Core game engine with pure functions for Color Clash Cards.
 * All functions are stateless and return new state objects.
 */
object GameEngine {

    private const val INITIAL_HAND_SIZE = 7
    const val LAST_CARD_TIMEOUT_MS = 3000L
    const val LAST_CARD_PENALTY = 2

    /**
     * Calculate the total points from all cards in a player's hand.
     */
    fun calculateHandPoints(hand: List<Card>): Int {
        return hand.sumOf { it.getPoints() }
    }

    /**
     * Calculate round points (sum of all remaining cards in all other players' hands).
     */
    fun calculateRoundPoints(players: List<Player>, winnerId: String): Int {
        return players
            .filter { it.id != winnerId }
            .sumOf { calculateHandPoints(it.hand) }
    }

    /**
     * Start a new game with the given players.
     *
     * @param players List of players (2-4 players supported)
     * @return Initial game state
     */
    fun startGame(players: List<Player>): GameState {
        require(players.size in 2..4) { "Game requires 2-4 players" }

        var deck = DeckBuilder.createShuffledDeck()

        // Deal cards to each player
        val playersWithCards = players.map { player ->
            val hand = deck.take(INITIAL_HAND_SIZE)
            deck = deck.drop(INITIAL_HAND_SIZE)
            player.copy(hand = hand)
        }

        // Find a starting card (must be a number card)
        var startingCard: Card
        do {
            startingCard = deck.first()
            deck = deck.drop(1)
            // If it's not a number card, put it back and reshuffle
            if (startingCard.type != CardType.NUMBER) {
                deck = (deck + startingCard).shuffled()
            }
        } while (startingCard.type != CardType.NUMBER)

        return GameState(
            players = playersWithCards,
            deck = deck,
            discardPile = listOf(startingCard),
            currentPlayerIndex = 0,
            direction = PlayDirection.CLOCKWISE,
            currentColor = startingCard.color,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            lastPlayedCard = startingCard
        )
    }

    /**
     * Get all playable cards from a player's hand.
     *
     * @param hand The player's hand
     * @param topCard The top card of the discard pile
     * @param currentColor The current active color
     * @return List of cards that can be played
     */
    fun getPlayableCards(hand: List<Card>, topCard: Card, currentColor: CardColor): List<Card> {
        return hand.filter { it.canPlayOn(topCard, currentColor) }
    }

    /**
     * Play a card from the current player's hand.
     *
     * @param state Current game state
     * @param card The card to play
     * @param chosenColor Color chosen if playing a wild card (required for wild cards)
     * @return Updated game state, or null if the play is invalid
     */
    fun playCard(state: GameState, card: Card, chosenColor: CardColor? = null): GameState? {
        val player = state.currentPlayer
        val topCard = state.topCard ?: return null

        // Validate the card is in player's hand
        if (card !in player.hand) return null

        // Validate the card can be played
        if (!card.canPlayOn(topCard, state.currentColor)) return null

        // Validate wild card has a color chosen
        if (card.type.isWild() && chosenColor == null) return null
        if (card.type.isWild() && chosenColor == CardColor.WILD) return null

        // Remove card from player's hand
        val updatedPlayer = player.removeCard(card)

        // Add card to discard pile
        val newDiscardPile = state.discardPile + card

        // Determine the new current color
        val newColor = if (card.type.isWild()) chosenColor!! else card.color

        // Update state with played card
        var newState = state.copy(
            discardPile = newDiscardPile,
            currentColor = newColor,
            lastPlayedCard = card
        ).updatePlayer(updatedPlayer)

        // Check for round winner
        if (updatedPlayer.hasWon) {
            val roundPoints = calculateRoundPoints(newState.players, updatedPlayer.id)

            // Update winner's score
            val winnerWithPoints = updatedPlayer.addScore(roundPoints)
            var stateWithScore = newState.updatePlayer(winnerWithPoints)

            // Determine if match is over (final round)
            val isFinalRound = stateWithScore.currentRound >= GameState.TOTAL_ROUNDS

            return stateWithScore.copy(
                winner = winnerWithPoints,
                roundWinnerId = winnerWithPoints.id,
                roundPoints = roundPoints,
                gamePhase = if (isFinalRound) GamePhase.MATCH_OVER else GamePhase.ROUND_OVER
            )
        }

        // Set last card timer if player has 1 card
        if (updatedPlayer.cardCount == 1 && !updatedPlayer.hasCalledLastCard) {
            newState = newState.copy(lastCardTimer = System.currentTimeMillis())
        }

        // Apply action effects
        newState = applyActionEffects(newState, card)

        return newState
    }

    /**
     * Apply the effects of action cards.
     */
    private fun applyActionEffects(state: GameState, card: Card): GameState {
        return when (card.type) {
            CardType.SKIP -> {
                // Skip next player
                val skippedIndex = state.getNextPlayerIndex()
                advanceTurn(state.copy(
                    currentPlayerIndex = skippedIndex,
                    turnPhase = TurnPhase.TURN_ENDED
                ))
            }
            CardType.REVERSE -> {
                val newDirection = state.direction.reversed()
                // In 2-player game, reverse acts like skip (same player goes again)
                if (state.players.size == 2) {
                    state.copy(
                        direction = newDirection,
                        turnPhase = TurnPhase.PLAY_OR_DRAW
                    )
                } else {
                    advanceTurn(state.copy(
                        direction = newDirection,
                        turnPhase = TurnPhase.TURN_ENDED
                    ))
                }
            }
            CardType.DRAW_TWO -> {
                // Next player must draw 2 and lose turn
                val nextState = advanceTurn(state.copy(turnPhase = TurnPhase.TURN_ENDED))
                nextState.copy(
                    pendingDrawCount = 2,
                    turnPhase = TurnPhase.MUST_DRAW
                )
            }
            CardType.WILD_DRAW_FOUR -> {
                // Next player must draw 4 and lose turn
                val nextState = advanceTurn(state.copy(turnPhase = TurnPhase.TURN_ENDED))
                nextState.copy(
                    pendingDrawCount = 4,
                    turnPhase = TurnPhase.MUST_DRAW
                )
            }
            else -> {
                // Number cards and WILD_COLOR just advance turn
                advanceTurn(state.copy(turnPhase = TurnPhase.TURN_ENDED))
            }
        }
    }

    /**
     * Draw a card for the current player.
     *
     * @param state Current game state
     * @param count Number of cards to draw (default 1)
     * @return Updated game state
     */
    fun drawCard(state: GameState, count: Int = 1): GameState {
        var newState = state
        var deck = state.deck.toMutableList()
        var discardPile = state.discardPile.toMutableList()
        val drawnCards = mutableListOf<Card>()

        repeat(count) {
            // Reshuffle discard pile if deck is empty
            if (deck.isEmpty()) {
                if (discardPile.size <= 1) {
                    // Can't draw - no cards available
                    return@repeat
                }
                val topCard = discardPile.removeLast()
                deck = discardPile.shuffled().toMutableList()
                discardPile = mutableListOf(topCard)
            }

            if (deck.isNotEmpty()) {
                drawnCards.add(deck.removeFirst())
            }
        }

        // Add drawn cards to current player's hand
        val updatedPlayer = newState.currentPlayer.addCards(drawnCards)
        newState = newState.copy(
            deck = deck,
            discardPile = discardPile
        ).updatePlayer(updatedPlayer)

        // If this was a forced draw (from +2 or +4), end turn
        if (state.turnPhase == TurnPhase.MUST_DRAW) {
            newState = advanceTurn(newState.copy(
                pendingDrawCount = 0,
                turnPhase = TurnPhase.TURN_ENDED
            ))
        } else {
            // Normal draw - player can now play the drawn card if valid
            newState = newState.copy(turnPhase = TurnPhase.PLAY_OR_DRAW)
        }

        return newState
    }

    /**
     * Advance to the next player's turn.
     */
    fun advanceTurn(state: GameState): GameState {
        val nextIndex = state.getNextPlayerIndex()
        return state.copy(
            currentPlayerIndex = nextIndex,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            lastCardTimer = null
        )
    }

    /**
     * Call "Last Card!" for a player.
     *
     * @param state Current game state
     * @param playerId ID of the player calling
     * @return Updated game state
     */
    fun callLastCard(state: GameState, playerId: String): GameState {
        val player = state.getPlayer(playerId) ?: return state
        if (player.cardCount != 1) return state

        val updatedPlayer = player.callLastCard()
        return state.updatePlayer(updatedPlayer).copy(lastCardTimer = null)
    }

    /**
     * Check if a player failed to call "Last Card!" in time.
     *
     * @param state Current game state
     * @param playerId ID of the player to check
     * @param currentTime Current timestamp
     * @return Updated game state with penalty applied if applicable
     */
    fun checkLastCardPenalty(state: GameState, playerId: String, currentTime: Long): GameState {
        val player = state.getPlayer(playerId) ?: return state
        val timer = state.lastCardTimer ?: return state

        // Check if timer expired and player hasn't called
        if (player.cardCount == 1 &&
            !player.hasCalledLastCard &&
            currentTime - timer >= LAST_CARD_TIMEOUT_MS
        ) {
            // Apply penalty - draw 2 cards
            var newState = state
            repeat(LAST_CARD_PENALTY) {
                newState = drawCardForPlayer(newState, playerId)
            }
            return newState.copy(lastCardTimer = null)
        }

        return state
    }

    /**
     * Draw a card for a specific player (not current player).
     * Used for penalties.
     */
    private fun drawCardForPlayer(state: GameState, playerId: String): GameState {
        val player = state.getPlayer(playerId) ?: return state
        var deck = state.deck.toMutableList()
        var discardPile = state.discardPile.toMutableList()

        // Reshuffle if needed
        if (deck.isEmpty() && discardPile.size > 1) {
            val topCard = discardPile.removeLast()
            deck = discardPile.shuffled().toMutableList()
            discardPile = mutableListOf(topCard)
        }

        if (deck.isEmpty()) return state

        val drawnCard = deck.removeFirst()
        val updatedPlayer = player.addCards(listOf(drawnCard))

        return state.copy(
            deck = deck,
            discardPile = discardPile
        ).updatePlayer(updatedPlayer)
    }

    /**
     * Pass turn after drawing (when player cannot or chooses not to play).
     */
    fun passTurn(state: GameState): GameState {
        if (state.turnPhase != TurnPhase.PLAY_OR_DRAW) return state
        return advanceTurn(state.copy(turnPhase = TurnPhase.TURN_ENDED))
    }

    /**
     * Check if the current player can play any card.
     */
    fun canCurrentPlayerPlay(state: GameState): Boolean {
        val topCard = state.topCard ?: return false
        return getPlayableCards(
            state.currentPlayer.hand,
            topCard,
            state.currentColor
        ).isNotEmpty()
    }

    /**
     * Start the next round in the match.
     * Keeps player order and totalScores, resets everything else.
     *
     * @param state Current game state (should be in ROUND_OVER phase)
     * @return New game state for next round
     */
    fun startNextRound(state: GameState): GameState {
        require(state.gamePhase == GamePhase.ROUND_OVER) { "Can only start next round when current round is over" }
        require(state.currentRound < GameState.TOTAL_ROUNDS) { "Cannot start next round after final round" }

        // Keep players with their scores but reset hands
        val playersWithScores = state.players.map { player ->
            player.resetForNewRound()
        }

        // Create fresh deck and deal
        var deck = DeckBuilder.createShuffledDeck()

        // Deal cards to each player
        val playersWithCards = playersWithScores.map { player ->
            val hand = deck.take(INITIAL_HAND_SIZE)
            deck = deck.drop(INITIAL_HAND_SIZE)
            player.copy(hand = hand)
        }

        // Find a starting card (must be a number card)
        var startingCard: Card
        do {
            startingCard = deck.first()
            deck = deck.drop(1)
            if (startingCard.type != CardType.NUMBER) {
                deck = (deck + startingCard).shuffled()
            }
        } while (startingCard.type != CardType.NUMBER)

        return GameState(
            players = playersWithCards,
            deck = deck,
            discardPile = listOf(startingCard),
            currentPlayerIndex = 0,
            direction = PlayDirection.CLOCKWISE,
            currentColor = startingCard.color,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            lastPlayedCard = startingCard,
            currentRound = state.currentRound + 1,
            gamePhase = GamePhase.PLAYING,
            roundWinnerId = null,
            roundPoints = 0
        )
    }

    /**
     * Start a completely new match.
     * Resets all scores and starts from round 1.
     *
     * @param players List of players (scores will be reset)
     * @return New game state for round 1
     */
    fun startNewMatch(players: List<Player>): GameState {
        // Reset all player scores
        val resetPlayers = players.map { it.resetScore().resetForNewRound() }
        return startGame(resetPlayers)
    }
}
