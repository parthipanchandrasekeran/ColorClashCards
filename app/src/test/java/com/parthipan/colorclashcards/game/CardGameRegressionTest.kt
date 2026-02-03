package com.parthipan.colorclashcards.game

import com.parthipan.colorclashcards.game.engine.DeckBuilder
import com.parthipan.colorclashcards.game.engine.GameEngine
import com.parthipan.colorclashcards.game.model.*
import com.parthipan.colorclashcards.testutil.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive regression tests for Color Clash Cards game engine.
 *
 * Covers:
 * - Draw card behavior
 * - Skip / Reverse / +2 / +4 effects
 * - Win conditions
 * - Reshuffle logic
 * - Multiplayer turn order
 * - Prevention of illegal plays
 */
class CardGameRegressionTest {

    private lateinit var stateBuilder: CardGameStateBuilder

    @Before
    fun setup() {
        stateBuilder = CardGameStateBuilder()
    }

    // ============================================================
    // DRAW CARD BEHAVIOR
    // ============================================================

    @Test
    fun `draw card - adds exactly one card to player hand`() {
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1), TestCards.blue(2)))
            .withTopCard(TestCards.green(5))
            .withDeckTopCards(TestCards.yellow(7), TestCards.red(8))
            .build()

        val initialCount = state.currentPlayer.cardCount
        val newState = GameEngine.drawCard(state)

        assertEquals(initialCount + 1, newState.currentPlayer.cardCount)
    }

    @Test
    fun `draw card - transitions to DREW_CARD phase`() {
        val state = stateBuilder
            .withPlayers(2)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withTurnPhase(TurnPhase.PLAY_OR_DRAW)
            .build()

        val newState = GameEngine.drawCard(state)

        assertEquals(TurnPhase.DREW_CARD, newState.turnPhase)
    }

    @Test
    fun `draw card - cannot draw twice in same turn`() {
        val state = stateBuilder
            .withPlayers(2)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withTurnPhase(TurnPhase.DREW_CARD) // Already drew
            .build()

        val initialCount = state.currentPlayer.cardCount
        val newState = GameEngine.drawCard(state)

        // Should not draw - same hand size
        assertEquals(initialCount, newState.currentPlayer.cardCount)
    }

    @Test
    fun `draw card - drawn card is from top of deck`() {
        val expectedCard = TestCards.yellow(9)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withTopCard(TestCards.green(5))
            .withDeck(listOf(expectedCard, TestCards.blue(3)))
            .build()

        val newState = GameEngine.drawCard(state)
        val drawnCard = GameEngine.getLastDrawnCard(newState)

        assertEquals(expectedCard.color, drawnCard?.color)
        assertEquals(expectedCard.number, drawnCard?.number)
    }

    @Test
    fun `draw card - removes card from deck`() {
        val state = stateBuilder
            .withPlayers(2)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withDeck(listOf(TestCards.yellow(1), TestCards.yellow(2), TestCards.yellow(3)))
            .build()

        val initialDeckSize = state.deck.size
        val newState = GameEngine.drawCard(state)

        assertEquals(initialDeckSize - 1, newState.deck.size)
    }

    @Test
    fun `forced draw (+2) - draws 2 cards and ends turn`() {
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withTopCard(TestCards.redDrawTwo())
            .withCurrentPlayer(1)
            .withPendingDraw(2)
            .withDeckTopCards(TestCards.green(1), TestCards.green(2), TestCards.green(3))
            .build()

        val initialCount = state.currentPlayer.cardCount
        val newState = GameEngine.drawCard(state, 2)

        // Should have drawn 2 cards
        val player1After = newState.players[1]
        assertEquals(initialCount + 2, player1After.cardCount)

        // Turn should advance to player 0
        assertEquals(0, newState.currentPlayerIndex)
        assertEquals(TurnPhase.PLAY_OR_DRAW, newState.turnPhase)
    }

    @Test
    fun `forced draw (+4) - draws 4 cards and ends turn`() {
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withTopCard(TestCards.wildDrawFour())
            .withCurrentPlayer(1)
            .withPendingDraw(4)
            .withDeckTopCards(
                TestCards.green(1), TestCards.green(2),
                TestCards.green(3), TestCards.green(4), TestCards.green(5)
            )
            .build()

        val initialCount = state.currentPlayer.cardCount
        val newState = GameEngine.drawCard(state, 4)

        val player1After = newState.players[1]
        assertEquals(initialCount + 4, player1After.cardCount)
    }

    // ============================================================
    // SKIP CARD BEHAVIOR
    // ============================================================

    @Test
    fun `skip card - skips next player in 3 player game`() {
        val skipCard = TestCards.redSkip()
        val state = stateBuilder
            .withPlayers(3)
            .withPlayerHand(0, TestCards.hand(skipCard, TestCards.blue(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withPlayerHand(2, TestCards.hand(TestCards.yellow(3)))
            .withTopCard(TestCards.red(5))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, skipCard)

        assertNotNull(newState)
        // Should skip player 1, go to player 2
        assertEquals(2, newState!!.currentPlayerIndex)
    }

    @Test
    fun `skip card - skips next player in 4 player game`() {
        val skipCard = TestCards.blueSkip()
        val state = stateBuilder
            .withPlayers(4)
            .withPlayerHand(0, TestCards.hand(skipCard, TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withPlayerHand(2, TestCards.hand(TestCards.yellow(3)))
            .withPlayerHand(3, TestCards.hand(TestCards.red(4)))
            .withTopCard(TestCards.blue(5))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, skipCard)

        assertNotNull(newState)
        assertEquals(2, newState!!.currentPlayerIndex)
    }

    @Test
    fun `skip card - works at end of player list (wraps around)`() {
        val skipCard = TestCards.greenSkip()
        val state = stateBuilder
            .withPlayers(3)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withPlayerHand(2, TestCards.hand(skipCard, TestCards.yellow(3)))
            .withTopCard(TestCards.green(5))
            .withCurrentPlayer(2)
            .build()

        val newState = GameEngine.playCard(state, skipCard)

        assertNotNull(newState)
        // Should skip player 0, go to player 1
        assertEquals(1, newState!!.currentPlayerIndex)
    }

    // ============================================================
    // REVERSE CARD BEHAVIOR
    // ============================================================

    @Test
    fun `reverse card - changes direction clockwise to counter-clockwise`() {
        val reverseCard = TestCards.redReverse()
        val state = stateBuilder
            .withPlayers(3)
            .withDefaultHands()
            .withPlayerHand(0, TestCards.hand(reverseCard, TestCards.blue(1)))
            .withTopCard(TestCards.red(5))
            .withDirection(PlayDirection.CLOCKWISE)
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, reverseCard)

        assertNotNull(newState)
        assertEquals(PlayDirection.COUNTER_CLOCKWISE, newState!!.direction)
    }

    @Test
    fun `reverse card - changes direction counter-clockwise to clockwise`() {
        val reverseCard = TestCards.blueReverse()
        val state = stateBuilder
            .withPlayers(3)
            .withDefaultHands()
            .withPlayerHand(1, TestCards.hand(reverseCard, TestCards.red(1)))
            .withTopCard(TestCards.blue(5))
            .withDirection(PlayDirection.COUNTER_CLOCKWISE)
            .withCurrentPlayer(1)
            .build()

        val newState = GameEngine.playCard(state, reverseCard)

        assertNotNull(newState)
        assertEquals(PlayDirection.CLOCKWISE, newState!!.direction)
    }

    @Test
    fun `reverse card - in 2 player game acts like skip (same player again)`() {
        val reverseCard = TestCards.greenReverse()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(reverseCard, TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withTopCard(TestCards.green(5))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, reverseCard)

        assertNotNull(newState)
        // In 2-player, reverse means same player goes again
        assertEquals(0, newState!!.currentPlayerIndex)
    }

    @Test
    fun `reverse card - next player in reversed direction`() {
        val reverseCard = TestCards.yellowReverse()
        val state = stateBuilder
            .withPlayers(4)
            .withDefaultHands()
            .withPlayerHand(0, TestCards.hand(reverseCard, TestCards.red(1)))
            .withTopCard(TestCards.yellow(5))
            .withDirection(PlayDirection.CLOCKWISE)
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, reverseCard)

        assertNotNull(newState)
        // Direction reversed, so from player 0, next is player 3 (wrapping backwards)
        assertEquals(3, newState!!.currentPlayerIndex)
    }

    // ============================================================
    // DRAW TWO (+2) CARD BEHAVIOR
    // ============================================================

    @Test
    fun `draw two card - next player enters MUST_DRAW phase`() {
        val drawTwoCard = TestCards.redDrawTwo()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(drawTwoCard, TestCards.blue(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withTopCard(TestCards.red(5))
            .withDeckTopCards(TestCards.yellow(1), TestCards.yellow(2), TestCards.yellow(3))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, drawTwoCard)

        assertNotNull(newState)
        assertEquals(1, newState!!.currentPlayerIndex)
        assertEquals(TurnPhase.MUST_DRAW, newState.turnPhase)
        assertEquals(2, newState.pendingDrawCount)
    }

    @Test
    fun `draw two card - penalty resets after drawing`() {
        // First, play the +2
        val drawTwoCard = TestCards.blueDrawTwo()
        var state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(drawTwoCard, TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withTopCard(TestCards.blue(5))
            .withDeckTopCards(TestCards.yellow(1), TestCards.yellow(2), TestCards.yellow(3))
            .withCurrentPlayer(0)
            .build()

        state = GameEngine.playCard(state, drawTwoCard)!!

        // Now player 1 draws the penalty
        val afterDraw = GameEngine.drawCard(state, 2)

        assertEquals(0, afterDraw.pendingDrawCount)
        assertEquals(TurnPhase.PLAY_OR_DRAW, afterDraw.turnPhase)
    }

    // ============================================================
    // WILD DRAW FOUR (+4) CARD BEHAVIOR
    // ============================================================

    @Test
    fun `wild draw four - next player must draw 4`() {
        val wildDrawFour = TestCards.wildDrawFour()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(wildDrawFour, TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withTopCard(TestCards.blue(5))
            .withDeckTopCards(
                TestCards.yellow(1), TestCards.yellow(2),
                TestCards.yellow(3), TestCards.yellow(4), TestCards.yellow(5)
            )
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, wildDrawFour, CardColor.RED)

        assertNotNull(newState)
        assertEquals(1, newState!!.currentPlayerIndex)
        assertEquals(TurnPhase.MUST_DRAW, newState.turnPhase)
        assertEquals(4, newState.pendingDrawCount)
    }

    @Test
    fun `wild draw four - changes color to chosen color`() {
        val wildDrawFour = TestCards.wildDrawFour()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(wildDrawFour, TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withTopCard(TestCards.blue(5))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, wildDrawFour, CardColor.YELLOW)

        assertNotNull(newState)
        assertEquals(CardColor.YELLOW, newState!!.currentColor)
    }

    @Test
    fun `wild draw four - requires color choice`() {
        val wildDrawFour = TestCards.wildDrawFour()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(wildDrawFour, TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.green(2)))
            .withTopCard(TestCards.blue(5))
            .withCurrentPlayer(0)
            .build()

        // Without color choice, should fail
        val newState = GameEngine.playCard(state, wildDrawFour, null)

        assertNull(newState)
    }

    // ============================================================
    // WIN CONDITION
    // ============================================================

    @Test
    fun `win condition - player with no cards wins round`() {
        val lastCard = TestCards.red(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(lastCard)) // Only 1 card
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2), TestCards.green(3)))
            .withTopCard(TestCards.red(3))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertTrue(newState!!.isRoundOver)
        assertEquals(state.players[0].id, newState.winner?.id)
    }

    @Test
    fun `win condition - round winner gets points from opponents hands`() {
        // Player 0 has one card, opponents have high-value cards
        val lastCard = TestCards.red(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(lastCard))
            .withPlayerHand(1, TestCards.hand(
                TestCards.wildDrawFour(),  // 50 points
                TestCards.blueSkip()       // 20 points
            ))
            .withTopCard(TestCards.red(3))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        // Winner should get 70 points (50 + 20)
        assertEquals(70, newState!!.roundPoints)
    }

    @Test
    fun `win condition - match ends after 10 rounds`() {
        val lastCard = TestCards.red(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(lastCard))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withTopCard(TestCards.red(3))
            .withCurrentPlayer(0)
            .withRound(10) // Final round
            .build()

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertTrue(newState!!.isMatchOver)
        assertEquals(GamePhase.MATCH_OVER, newState.gamePhase)
    }

    @Test
    fun `win condition - round over but match continues before round 10`() {
        val lastCard = TestCards.red(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(lastCard))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withTopCard(TestCards.red(3))
            .withCurrentPlayer(0)
            .withRound(5) // Not final round
            .build()

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertTrue(newState!!.isRoundOver)
        assertFalse(newState.isMatchOver)
        assertEquals(GamePhase.ROUND_OVER, newState.gamePhase)
    }

    // ============================================================
    // RESHUFFLE LOGIC
    // ============================================================

    @Test
    fun `reshuffle - when deck empty, discard pile becomes new deck`() {
        val topCard = TestCards.green(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withDeck(emptyList()) // Empty deck
            .withDiscardPile(listOf(
                TestCards.yellow(1),
                TestCards.yellow(2),
                TestCards.yellow(3),
                topCard // Top card (last in list)
            ))
            .withCurrentColor(CardColor.GREEN)
            .build()

        val newState = GameEngine.drawCard(state)

        // Deck should now have cards (reshuffled from discard minus top card)
        assertTrue(newState.deck.isNotEmpty())
        // Discard pile should only have the original top card
        assertEquals(1, newState.discardPile.size)
        assertEquals(topCard.color, newState.topCard?.color)
    }

    @Test
    fun `reshuffle - top card stays on discard pile`() {
        val topCard = TestCards.green(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withDeck(emptyList())
            .withDiscardPile(listOf(
                TestCards.yellow(1),
                TestCards.yellow(2),
                topCard
            ))
            .build()

        val newState = GameEngine.drawCard(state)

        // Top card should still be on discard
        assertEquals(topCard.number, newState.topCard?.number)
        assertEquals(topCard.color, newState.topCard?.color)
    }

    @Test
    fun `reshuffle - does not happen if discard pile has only top card`() {
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.red(1)))
            .withPlayerHand(1, TestCards.hand(TestCards.blue(2)))
            .withDeck(emptyList())
            .withDiscardPile(listOf(TestCards.green(5))) // Only 1 card
            .withCurrentColor(CardColor.GREEN)
            .build()

        val initialHandSize = state.currentPlayer.cardCount
        val newState = GameEngine.drawCard(state)

        // Cannot draw - hand size unchanged (no cards available)
        assertEquals(initialHandSize, newState.currentPlayer.cardCount)
    }

    // ============================================================
    // MULTIPLAYER TURN ORDER
    // ============================================================

    @Test
    fun `turn order - clockwise advances player index`() {
        val state = stateBuilder
            .withPlayers(4)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withDirection(PlayDirection.CLOCKWISE)
            .withCurrentPlayer(0)
            .build()

        assertEquals(1, state.getNextPlayerIndex())
    }

    @Test
    fun `turn order - counter-clockwise decreases player index`() {
        val state = stateBuilder
            .withPlayers(4)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withDirection(PlayDirection.COUNTER_CLOCKWISE)
            .withCurrentPlayer(2)
            .build()

        assertEquals(1, state.getNextPlayerIndex())
    }

    @Test
    fun `turn order - clockwise wraps from last to first player`() {
        val state = stateBuilder
            .withPlayers(4)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withDirection(PlayDirection.CLOCKWISE)
            .withCurrentPlayer(3)
            .build()

        assertEquals(0, state.getNextPlayerIndex())
    }

    @Test
    fun `turn order - counter-clockwise wraps from first to last player`() {
        val state = stateBuilder
            .withPlayers(4)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withDirection(PlayDirection.COUNTER_CLOCKWISE)
            .withCurrentPlayer(0)
            .build()

        assertEquals(3, state.getNextPlayerIndex())
    }

    @Test
    fun `turn order - normal card play advances to next player`() {
        val numberCard = TestCards.red(3)
        val state = stateBuilder
            .withPlayers(3)
            .withDefaultHands()
            .withPlayerHand(0, TestCards.hand(numberCard, TestCards.blue(1)))
            .withTopCard(TestCards.red(5))
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.playCard(state, numberCard)

        assertNotNull(newState)
        assertEquals(1, newState!!.currentPlayerIndex)
    }

    @Test
    fun `turn order - pass after draw advances to next player`() {
        var state = stateBuilder
            .withPlayers(3)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withCurrentPlayer(0)
            .build()

        // Draw a card
        state = GameEngine.drawCard(state)
        assertEquals(TurnPhase.DREW_CARD, state.turnPhase)

        // Pass turn
        val newState = GameEngine.passTurn(state)

        assertEquals(1, newState.currentPlayerIndex)
        assertEquals(TurnPhase.PLAY_OR_DRAW, newState.turnPhase)
    }

    // ============================================================
    // PREVENTION OF ILLEGAL PLAYS
    // ============================================================

    @Test
    fun `illegal play - cannot play card not in hand`() {
        val cardNotInHand = TestCards.red(9)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(TestCards.blue(1), TestCards.green(2)))
            .withTopCard(TestCards.red(5))
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, cardNotInHand)

        assertNull(result)
    }

    @Test
    fun `illegal play - cannot play non-matching card`() {
        val nonMatchingCard = TestCards.blue(3)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(nonMatchingCard, TestCards.green(2)))
            .withTopCard(TestCards.red(5)) // Red 5 - blue 3 doesn't match
            .withCurrentColor(CardColor.RED)
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, nonMatchingCard)

        assertNull(result)
    }

    @Test
    fun `illegal play - wild card cannot use WILD as chosen color`() {
        val wildCard = TestCards.wild()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(wildCard, TestCards.red(1)))
            .withTopCard(TestCards.blue(5))
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, wildCard, CardColor.WILD)

        assertNull(result)
    }

    @Test
    fun `illegal play - cannot draw during DREW_CARD phase`() {
        val state = stateBuilder
            .withPlayers(2)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withTurnPhase(TurnPhase.DREW_CARD)
            .withCurrentPlayer(0)
            .build()

        val initialHandSize = state.currentPlayer.cardCount
        val newState = GameEngine.drawCard(state)

        // Hand size should not change
        assertEquals(initialHandSize, newState.currentPlayer.cardCount)
    }

    @Test
    fun `illegal play - cannot pass during PLAY_OR_DRAW phase`() {
        val state = stateBuilder
            .withPlayers(2)
            .withDefaultHands()
            .withTopCard(TestCards.red(5))
            .withTurnPhase(TurnPhase.PLAY_OR_DRAW)
            .withCurrentPlayer(0)
            .build()

        val newState = GameEngine.passTurn(state)

        // State should be unchanged (can't pass without drawing first)
        assertEquals(state.currentPlayerIndex, newState.currentPlayerIndex)
        assertEquals(TurnPhase.PLAY_OR_DRAW, newState.turnPhase)
    }

    @Test
    fun `legal play - matching color allows play`() {
        val matchingCard = TestCards.red(3)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(matchingCard, TestCards.blue(1)))
            .withTopCard(TestCards.red(5))
            .withCurrentColor(CardColor.RED)
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, matchingCard)

        assertNotNull(result)
    }

    @Test
    fun `legal play - matching number allows play`() {
        val matchingCard = TestCards.blue(5)
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(matchingCard, TestCards.green(1)))
            .withTopCard(TestCards.red(5))
            .withCurrentColor(CardColor.RED)
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, matchingCard)

        assertNotNull(result)
    }

    @Test
    fun `legal play - matching action type allows play`() {
        val matchingSkip = TestCards.blueSkip()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(matchingSkip, TestCards.green(1)))
            .withTopCard(TestCards.redSkip())
            .withCurrentColor(CardColor.RED)
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, matchingSkip)

        assertNotNull(result)
    }

    @Test
    fun `legal play - wild card always playable`() {
        val wildCard = TestCards.wild()
        val state = stateBuilder
            .withPlayers(2)
            .withPlayerHand(0, TestCards.hand(wildCard, TestCards.green(1)))
            .withTopCard(TestCards.red(5))
            .withCurrentColor(CardColor.RED)
            .withCurrentPlayer(0)
            .build()

        val result = GameEngine.playCard(state, wildCard, CardColor.BLUE)

        assertNotNull(result)
        assertEquals(CardColor.BLUE, result!!.currentColor)
    }

    // ============================================================
    // EDGE CASES
    // ============================================================

    @Test
    fun `edge case - zero card has point value of 0`() {
        val zeroCard = TestCards.red(0)
        assertEquals(0, zeroCard.getPoints())
    }

    @Test
    fun `edge case - action cards worth 20 points`() {
        assertEquals(20, TestCards.redSkip().getPoints())
        assertEquals(20, TestCards.blueReverse().getPoints())
        assertEquals(20, TestCards.greenDrawTwo().getPoints())
    }

    @Test
    fun `edge case - wild cards worth 50 points`() {
        assertEquals(50, TestCards.wild().getPoints())
        assertEquals(50, TestCards.wildDrawFour().getPoints())
    }

    @Test
    fun `edge case - timeout winner is player with lowest hand points`() {
        val player1 = Player.human("P1").copy(hand = listOf(
            TestCards.red(9), TestCards.wild()  // 9 + 50 = 59 points
        ))
        val player2 = Player.bot("P2").copy(hand = listOf(
            TestCards.blue(1), TestCards.green(2)  // 1 + 2 = 3 points (winner)
        ))
        val player3 = Player.bot("P3").copy(hand = listOf(
            TestCards.yellowSkip()  // 20 points
        ))

        val winner = GameEngine.determineTimeoutWinner(listOf(player1, player2, player3))

        assertEquals(player2.id, winner.id)
    }

    @Test
    fun `edge case - timeout tiebreaker is fewer cards`() {
        val player1 = Player.human("P1").copy(hand = listOf(
            TestCards.red(5), TestCards.blue(5)  // 10 points, 2 cards
        ))
        val player2 = Player.bot("P2").copy(hand = listOf(
            TestCards.green(1), TestCards.green(2), TestCards.green(3), TestCards.green(4)  // 10 points, 4 cards
        ))

        val winner = GameEngine.determineTimeoutWinner(listOf(player1, player2))

        // Same points, but player1 has fewer cards
        assertEquals(player1.id, winner.id)
    }
}
