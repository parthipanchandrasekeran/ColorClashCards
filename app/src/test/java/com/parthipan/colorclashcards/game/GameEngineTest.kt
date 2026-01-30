package com.parthipan.colorclashcards.game

import com.parthipan.colorclashcards.game.engine.DeckBuilder
import com.parthipan.colorclashcards.game.engine.GameEngine
import com.parthipan.colorclashcards.game.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Color Clash Cards game engine.
 */
class GameEngineTest {

    // ==================== Deck Tests ====================

    @Test
    fun `deck contains 108 cards`() {
        val deck = DeckBuilder.createStandardDeck()
        assertEquals(108, deck.size)
    }

    @Test
    fun `deck contains correct number of each card type`() {
        val deck = DeckBuilder.createStandardDeck()

        // Count number cards: 4 colors × (1 zero + 2×9 other numbers) = 4 × 19 = 76
        val numberCards = deck.filter { it.type == CardType.NUMBER }
        assertEquals(76, numberCards.size)

        // Count action cards: 4 colors × 2 each × 3 types = 24
        val skipCards = deck.filter { it.type == CardType.SKIP }
        val reverseCards = deck.filter { it.type == CardType.REVERSE }
        val drawTwoCards = deck.filter { it.type == CardType.DRAW_TWO }
        assertEquals(8, skipCards.size)
        assertEquals(8, reverseCards.size)
        assertEquals(8, drawTwoCards.size)

        // Count wild cards: 4 of each type = 8
        val wildColorCards = deck.filter { it.type == CardType.WILD_COLOR }
        val wildDrawFourCards = deck.filter { it.type == CardType.WILD_DRAW_FOUR }
        assertEquals(4, wildColorCards.size)
        assertEquals(4, wildDrawFourCards.size)
    }

    // ==================== Card Matching Tests ====================

    @Test
    fun `card can be played if colors match`() {
        val topCard = Card.number(CardColor.RED, 5)
        val playCard = Card.number(CardColor.RED, 3)

        assertTrue(playCard.canPlayOn(topCard, CardColor.RED))
    }

    @Test
    fun `card can be played if numbers match`() {
        val topCard = Card.number(CardColor.RED, 5)
        val playCard = Card.number(CardColor.BLUE, 5)

        assertTrue(playCard.canPlayOn(topCard, CardColor.RED))
    }

    @Test
    fun `card cannot be played if neither color nor number match`() {
        val topCard = Card.number(CardColor.RED, 5)
        val playCard = Card.number(CardColor.BLUE, 3)

        assertFalse(playCard.canPlayOn(topCard, CardColor.RED))
    }

    @Test
    fun `wild card can always be played`() {
        val topCard = Card.number(CardColor.RED, 5)
        val wildCard = Card.wildColor()

        assertTrue(wildCard.canPlayOn(topCard, CardColor.RED))
    }

    @Test
    fun `action cards match by type`() {
        val topSkip = Card.skip(CardColor.RED)
        val playSkip = Card.skip(CardColor.BLUE)

        assertTrue(playSkip.canPlayOn(topSkip, CardColor.RED))
    }

    // ==================== Game Start Tests ====================

    @Test
    fun `game starts with correct initial state`() {
        val players = listOf(
            Player.human("Player 1"),
            Player.bot("Bot 1")
        )

        val state = GameEngine.startGame(players)

        // Each player should have 7 cards
        assertEquals(7, state.players[0].cardCount)
        assertEquals(7, state.players[1].cardCount)

        // Discard pile should have 1 card
        assertEquals(1, state.discardPile.size)

        // Starting card should be a number card
        assertEquals(CardType.NUMBER, state.topCard?.type)

        // Current color should match starting card
        assertEquals(state.topCard?.color, state.currentColor)

        // First player should be index 0
        assertEquals(0, state.currentPlayerIndex)

        // Direction should be clockwise
        assertEquals(PlayDirection.CLOCKWISE, state.direction)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `game requires at least 2 players`() {
        val players = listOf(Player.human("Player 1"))
        GameEngine.startGame(players)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `game requires at most 4 players`() {
        val players = listOf(
            Player.human("Player 1"),
            Player.human("Player 2"),
            Player.human("Player 3"),
            Player.human("Player 4"),
            Player.human("Player 5")
        )
        GameEngine.startGame(players)
    }

    // ==================== Play Card Tests ====================

    @Test
    fun `playing valid card removes it from hand`() {
        val players = listOf(
            Player.human("Player 1"),
            Player.bot("Bot 1")
        )
        var state = GameEngine.startGame(players)

        // Find a playable card
        val playableCards = GameEngine.getPlayableCards(
            state.currentPlayer.hand,
            state.topCard!!,
            state.currentColor
        )

        if (playableCards.isNotEmpty()) {
            val cardToPlay = playableCards.first()
            val initialHandSize = state.currentPlayer.cardCount

            val chosenColor = if (cardToPlay.type.isWild()) CardColor.RED else null
            val newState = GameEngine.playCard(state, cardToPlay, chosenColor)

            assertNotNull(newState)
            // Find the player in new state (may have moved due to turn advance)
            val playerAfter = newState!!.players.find { it.id == state.currentPlayer.id }
            assertEquals(initialHandSize - 1, playerAfter?.cardCount)
        }
    }

    @Test
    fun `playing invalid card returns null`() {
        val players = listOf(
            Player.human("Player 1"),
            Player.bot("Bot 1")
        )
        val state = GameEngine.startGame(players)

        // Create a card that definitely can't be played
        val invalidCard = Card.number(
            color = if (state.currentColor == CardColor.RED) CardColor.BLUE else CardColor.RED,
            number = if (state.topCard?.number == 0) 9 else 0
        )

        // This should fail because the card isn't in the player's hand
        val result = GameEngine.playCard(state, invalidCard)
        assertNull(result)
    }

    @Test
    fun `wild card changes current color`() {
        // Create a controlled state
        val wildCard = Card.wildColor()
        val player1 = Player.human("Player 1").copy(hand = listOf(wildCard))
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 5)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW
        )

        val newState = GameEngine.playCard(state, wildCard, CardColor.GREEN)

        assertNotNull(newState)
        assertEquals(CardColor.GREEN, newState!!.currentColor)
    }

    // ==================== Draw Card Tests ====================

    @Test
    fun `drawing card adds it to player hand`() {
        val players = listOf(
            Player.human("Player 1"),
            Player.bot("Bot 1")
        )
        val state = GameEngine.startGame(players)
        val initialHandSize = state.currentPlayer.cardCount

        val newState = GameEngine.drawCard(state)
        val playerAfter = newState.currentPlayer

        assertEquals(initialHandSize + 1, playerAfter.cardCount)
    }

    // ==================== Action Card Tests ====================

    @Test
    fun `skip card skips next player`() {
        val skipCard = Card.skip(CardColor.RED)
        val player1 = Player.human("Player 1").copy(
            hand = listOf(skipCard, Card.number(CardColor.BLUE, 1))
        )
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 5)))
        val player3 = Player.bot("Bot 2").copy(hand = listOf(Card.number(CardColor.RED, 6)))

        val state = GameState(
            players = listOf(player1, player2, player3),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW
        )

        val newState = GameEngine.playCard(state, skipCard)

        assertNotNull(newState)
        // After skip, it should be player 3's turn (index 2), not player 2 (index 1)
        assertEquals(2, newState!!.currentPlayerIndex)
    }

    @Test
    fun `reverse card changes direction in 3+ player game`() {
        val reverseCard = Card.reverse(CardColor.RED)
        val player1 = Player.human("Player 1").copy(
            hand = listOf(reverseCard, Card.number(CardColor.BLUE, 1))
        )
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 5)))
        val player3 = Player.bot("Bot 2").copy(hand = listOf(Card.number(CardColor.RED, 6)))

        val state = GameState(
            players = listOf(player1, player2, player3),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            direction = PlayDirection.CLOCKWISE,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW
        )

        val newState = GameEngine.playCard(state, reverseCard)

        assertNotNull(newState)
        assertEquals(PlayDirection.COUNTER_CLOCKWISE, newState!!.direction)
    }

    @Test
    fun `reverse acts like skip in 2 player game`() {
        val reverseCard = Card.reverse(CardColor.RED)
        val player1 = Player.human("Player 1").copy(
            hand = listOf(reverseCard, Card.number(CardColor.BLUE, 1))
        )
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 5)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW
        )

        val newState = GameEngine.playCard(state, reverseCard)

        assertNotNull(newState)
        // In 2-player, reverse should make it player 1's turn again
        assertEquals(0, newState!!.currentPlayerIndex)
    }

    @Test
    fun `draw two makes next player draw and lose turn`() {
        val drawTwoCard = Card.drawTwo(CardColor.RED)
        val player1 = Player.human("Player 1").copy(
            hand = listOf(drawTwoCard, Card.number(CardColor.BLUE, 1))
        )
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 5)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW
        )

        val newState = GameEngine.playCard(state, drawTwoCard)

        assertNotNull(newState)
        assertEquals(2, newState!!.pendingDrawCount)
        assertEquals(TurnPhase.MUST_DRAW, newState.turnPhase)
    }

    // ==================== Win Condition Tests ====================

    @Test
    fun `player wins when hand is empty`() {
        val lastCard = Card.number(CardColor.RED, 5)
        val player1 = Player.human("Player 1").copy(
            hand = listOf(lastCard),
            hasCalledLastCard = true
        )
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 3)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW
        )

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertTrue(newState!!.isGameOver)
        assertEquals(player1.id, newState.winner?.id)
    }

    // ==================== Last Card Call Tests ====================

    @Test
    fun `player with one card needs to call last card`() {
        val player = Player.human("Player 1").copy(
            hand = listOf(Card.number(CardColor.RED, 5)),
            hasCalledLastCard = false
        )

        assertTrue(player.needsLastCardCall)
    }

    @Test
    fun `calling last card updates player state`() {
        val player1 = Player.human("Player 1").copy(
            hand = listOf(Card.number(CardColor.RED, 5)),
            hasCalledLastCard = false
        )
        val player2 = Player.bot("Bot 1").copy(hand = listOf(Card.number(CardColor.RED, 3)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            lastCardTimer = System.currentTimeMillis()
        )

        val newState = GameEngine.callLastCard(state, player1.id)
        val updatedPlayer = newState.getPlayer(player1.id)

        assertTrue(updatedPlayer!!.hasCalledLastCard)
        assertNull(newState.lastCardTimer)
    }

    // ==================== Playable Cards Tests ====================

    @Test
    fun `getPlayableCards returns correct cards`() {
        val hand = listOf(
            Card.number(CardColor.RED, 5),     // matches color
            Card.number(CardColor.BLUE, 3),   // matches number
            Card.number(CardColor.GREEN, 7),  // no match
            Card.wildColor()                   // wild always matches
        )
        val topCard = Card.number(CardColor.RED, 3)

        val playable = GameEngine.getPlayableCards(hand, topCard, CardColor.RED)

        assertEquals(3, playable.size)
        assertTrue(playable.any { it.color == CardColor.RED && it.number == 5 })
        assertTrue(playable.any { it.color == CardColor.BLUE && it.number == 3 })
        assertTrue(playable.any { it.type == CardType.WILD_COLOR })
        assertFalse(playable.any { it.color == CardColor.GREEN && it.number == 7 })
    }

    // ==================== Card Points Mapping Tests ====================

    @Test
    fun `number cards have face value points`() {
        for (i in 0..9) {
            val card = Card.number(CardColor.RED, i)
            assertEquals("Number $i should have $i points", i, card.getPoints())
        }
    }

    @Test
    fun `skip card has 20 points`() {
        val skipCard = Card.skip(CardColor.RED)
        assertEquals(20, skipCard.getPoints())
    }

    @Test
    fun `reverse card has 20 points`() {
        val reverseCard = Card.reverse(CardColor.BLUE)
        assertEquals(20, reverseCard.getPoints())
    }

    @Test
    fun `draw two card has 20 points`() {
        val drawTwoCard = Card.drawTwo(CardColor.GREEN)
        assertEquals(20, drawTwoCard.getPoints())
    }

    @Test
    fun `wild color card has 50 points`() {
        val wildCard = Card.wildColor()
        assertEquals(50, wildCard.getPoints())
    }

    @Test
    fun `wild draw four card has 50 points`() {
        val wildDrawFour = Card.wildDrawFour()
        assertEquals(50, wildDrawFour.getPoints())
    }

    // ==================== Round Points Calculation Tests ====================

    @Test
    fun `calculateHandPoints returns sum of all card points`() {
        val hand = listOf(
            Card.number(CardColor.RED, 5),      // 5 points
            Card.number(CardColor.BLUE, 3),    // 3 points
            Card.skip(CardColor.GREEN),         // 20 points
            Card.wildColor()                    // 50 points
        )

        val points = GameEngine.calculateHandPoints(hand)
        assertEquals(5 + 3 + 20 + 50, points)
        assertEquals(78, points)
    }

    @Test
    fun `calculateHandPoints returns 0 for empty hand`() {
        val points = GameEngine.calculateHandPoints(emptyList())
        assertEquals(0, points)
    }

    @Test
    fun `calculateRoundPoints sums all non-winner hands`() {
        val winnerId = "winner-id"
        val players = listOf(
            Player(id = winnerId, name = "Winner", hand = emptyList()),
            Player(id = "player2", name = "Player 2", hand = listOf(
                Card.number(CardColor.RED, 5),   // 5 points
                Card.number(CardColor.BLUE, 7)   // 7 points
            )),
            Player(id = "player3", name = "Player 3", hand = listOf(
                Card.skip(CardColor.GREEN),      // 20 points
                Card.wildColor()                 // 50 points
            ))
        )

        val roundPoints = GameEngine.calculateRoundPoints(players, winnerId)
        assertEquals(5 + 7 + 20 + 50, roundPoints)
        assertEquals(82, roundPoints)
    }

    @Test
    fun `calculateRoundPoints returns 0 when all hands empty`() {
        val winnerId = "winner-id"
        val players = listOf(
            Player(id = winnerId, name = "Winner", hand = emptyList()),
            Player(id = "player2", name = "Player 2", hand = emptyList())
        )

        val roundPoints = GameEngine.calculateRoundPoints(players, winnerId)
        assertEquals(0, roundPoints)
    }

    // ==================== Score Accumulation Tests ====================

    @Test
    fun `player addScore increases totalScore`() {
        val player = Player(name = "Test", totalScore = 0)
        val updatedPlayer = player.addScore(50)

        assertEquals(50, updatedPlayer.totalScore)
    }

    @Test
    fun `player addScore accumulates across multiple calls`() {
        val player = Player(name = "Test", totalScore = 0)
        val round1 = player.addScore(50)
        val round2 = round1.addScore(30)
        val round3 = round2.addScore(100)

        assertEquals(180, round3.totalScore)
    }

    @Test
    fun `player resetScore sets totalScore to 0`() {
        val player = Player(name = "Test", totalScore = 150)
        val resetPlayer = player.resetScore()

        assertEquals(0, resetPlayer.totalScore)
    }

    @Test
    fun `player resetForNewRound keeps totalScore but clears hand`() {
        val player = Player(
            name = "Test",
            totalScore = 100,
            hand = listOf(Card.number(CardColor.RED, 5)),
            hasCalledLastCard = true
        )
        val resetPlayer = player.resetForNewRound()

        assertEquals(100, resetPlayer.totalScore)
        assertTrue(resetPlayer.hand.isEmpty())
        assertFalse(resetPlayer.hasCalledLastCard)
    }

    // ==================== Round/Match Phase Tests ====================

    @Test
    fun `round win sets GamePhase to ROUND_OVER when not final round`() {
        val lastCard = Card.number(CardColor.RED, 5)
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(lastCard), hasCalledLastCard = true)
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.BLUE, 7),
            Card.skip(CardColor.GREEN)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            currentRound = 5,  // Not the final round
            gamePhase = GamePhase.PLAYING
        )

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertEquals(GamePhase.ROUND_OVER, newState!!.gamePhase)
        assertFalse(newState.isMatchOver)
        assertTrue(newState.isRoundOver)
    }

    @Test
    fun `round win sets GamePhase to MATCH_OVER on round 10`() {
        val lastCard = Card.number(CardColor.RED, 5)
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(lastCard), hasCalledLastCard = true)
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(Card.number(CardColor.BLUE, 7)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            currentRound = 10,  // Final round
            gamePhase = GamePhase.PLAYING
        )

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertEquals(GamePhase.MATCH_OVER, newState!!.gamePhase)
        assertTrue(newState.isMatchOver)
    }

    @Test
    fun `winning round calculates and adds points to winner`() {
        val lastCard = Card.number(CardColor.RED, 5)
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(lastCard), hasCalledLastCard = true, totalScore = 50)
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.BLUE, 7),   // 7 points
            Card.number(CardColor.GREEN, 3)   // 3 points
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            currentRound = 1,
            gamePhase = GamePhase.PLAYING
        )

        val newState = GameEngine.playCard(state, lastCard)

        assertNotNull(newState)
        assertEquals(10, newState!!.roundPoints)  // 7 + 3 = 10
        assertEquals("p1", newState.roundWinnerId)
        // Winner's score should increase: 50 + 10 = 60
        val winner = newState.players.find { it.id == "p1" }
        assertEquals(60, winner?.totalScore)
    }

    // ==================== Next Round Tests ====================

    @Test
    fun `startNextRound increments currentRound`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 30)

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "p1",
            roundPoints = 20
        )

        val newState = GameEngine.startNextRound(state)

        assertEquals(4, newState.currentRound)
        assertEquals(GamePhase.PLAYING, newState.gamePhase)
    }

    @Test
    fun `startNextRound preserves player scores`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 30)

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "p1",
            roundPoints = 20
        )

        val newState = GameEngine.startNextRound(state)

        val newPlayer1 = newState.players.find { it.id == "p1" }
        val newPlayer2 = newState.players.find { it.id == "p2" }

        assertEquals(50, newPlayer1?.totalScore)
        assertEquals(30, newPlayer2?.totalScore)
    }

    @Test
    fun `startNextRound resets hands and deals new cards`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50, hand = emptyList())
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 30, hand = listOf(Card.number(CardColor.BLUE, 5)))

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "p1",
            roundPoints = 20
        )

        val newState = GameEngine.startNextRound(state)

        // Each player should have 7 cards
        assertEquals(7, newState.players[0].cardCount)
        assertEquals(7, newState.players[1].cardCount)
    }

    @Test
    fun `startNextRound clears round winner info`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 30)

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "p1",
            roundPoints = 20
        )

        val newState = GameEngine.startNextRound(state)

        assertNull(newState.roundWinnerId)
        assertEquals(0, newState.roundPoints)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startNextRound fails if not in ROUND_OVER phase`() {
        val player1 = Player(id = "p1", name = "Player 1")
        val player2 = Player(id = "p2", name = "Player 2")

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.PLAYING  // Wrong phase
        )

        GameEngine.startNextRound(state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startNextRound fails after final round`() {
        val player1 = Player(id = "p1", name = "Player 1")
        val player2 = Player(id = "p2", name = "Player 2")

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 10,  // Final round
            gamePhase = GamePhase.ROUND_OVER
        )

        GameEngine.startNextRound(state)
    }

    // ==================== New Match Tests ====================

    @Test
    fun `startNewMatch resets scores and round counter`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 150)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 80)

        val newState = GameEngine.startNewMatch(listOf(player1, player2))

        assertEquals(1, newState.currentRound)
        assertEquals(GamePhase.PLAYING, newState.gamePhase)
        assertTrue(newState.players.all { it.totalScore == 0 })
    }

    @Test
    fun `TOTAL_ROUNDS constant is 10`() {
        assertEquals(10, GameState.TOTAL_ROUNDS)
    }

    @Test
    fun `isFinalRound returns true on round 10`() {
        val state = GameState(
            players = listOf(Player(name = "P1"), Player(name = "P2")),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 10
        )

        assertTrue(state.isFinalRound)
    }

    @Test
    fun `isFinalRound returns false before round 10`() {
        val state = GameState(
            players = listOf(Player(name = "P1"), Player(name = "P2")),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 9
        )

        assertFalse(state.isFinalRound)
    }

    @Test
    fun `matchWinner returns player with highest score after match ends`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 150)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 200)
        val player3 = Player(id = "p3", name = "Player 3", totalScore = 80)

        val state = GameState(
            players = listOf(player1, player2, player3),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 10,
            gamePhase = GamePhase.MATCH_OVER
        )

        assertEquals("p2", state.matchWinner?.id)
        assertEquals(200, state.matchWinner?.totalScore)
    }

    @Test
    fun `matchWinner returns null when match is not over`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 150)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 200)

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 5,
            gamePhase = GamePhase.PLAYING
        )

        assertNull(state.matchWinner)
    }

    // ==================== Timer Tests ====================

    @Test
    fun `turn timeout triggers auto-draw and advances turn`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING,
            turnSecondsRemaining = 0
        )

        val newState = GameEngine.handleTurnTimeout(state)

        // Player 1 should have drawn a card (now 2 cards)
        val player1After = newState.players.find { it.id == "p1" }
        assertEquals(2, player1After?.cardCount)

        // Turn should have advanced to player 2
        assertEquals(1, newState.currentPlayerIndex)
        assertEquals("p2", newState.currentPlayer.id)

        // Turn timer should be reset
        assertEquals(GameState.DEFAULT_TURN_SECONDS, newState.turnSecondsRemaining)
    }

    @Test
    fun `sudden death activates after 300 seconds`() {
        val state = GameState(
            players = listOf(Player(name = "P1"), Player(name = "P2")),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            gamePhase = GamePhase.PLAYING,
            roundSecondsElapsed = 299,
            suddenDeathActive = false
        )

        assertFalse(state.shouldActivateSuddenDeath)

        val stateAt300 = state.copy(roundSecondsElapsed = 300)
        assertTrue(stateAt300.shouldActivateSuddenDeath)

        val stateAt301 = state.copy(roundSecondsElapsed = 301)
        assertTrue(stateAt301.shouldActivateSuddenDeath)
    }

    @Test
    fun `sudden death does not reactivate if already active`() {
        val state = GameState(
            players = listOf(Player(name = "P1"), Player(name = "P2")),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            gamePhase = GamePhase.PLAYING,
            roundSecondsElapsed = 350,
            suddenDeathActive = true,  // Already active
            suddenDeathSecondsRemaining = 10
        )

        assertFalse(state.shouldActivateSuddenDeath)
    }

    @Test
    fun `timeout winner is player with lowest hand points`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.RED, 9),   // 9 points
            Card.number(CardColor.BLUE, 8)   // 8 points = 17 total
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.GREEN, 2)  // 2 points
        ))
        val player3 = Player(id = "p3", name = "Player 3", hand = listOf(
            Card.number(CardColor.YELLOW, 5),  // 5 points
            Card.skip(CardColor.RED)           // 20 points = 25 total
        ))

        val winner = GameEngine.determineTimeoutWinner(listOf(player1, player2, player3))

        assertEquals("p2", winner.id)  // Player 2 has lowest points (2)
    }

    @Test
    fun `timeout winner tie-breaker 1 is fewer cards`() {
        // Both have 20 points, but player1 has fewer cards
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.skip(CardColor.RED)           // 20 points, 1 card
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.GREEN, 5),   // 5 points
            Card.number(CardColor.BLUE, 5),    // 5 points
            Card.number(CardColor.RED, 5),     // 5 points
            Card.number(CardColor.YELLOW, 5)   // 5 points = 20 total, 4 cards
        ))

        val winner = GameEngine.determineTimeoutWinner(listOf(player1, player2))

        assertEquals("p1", winner.id)  // Player 1 has fewer cards
    }

    @Test
    fun `timeout winner tie-breaker 2 is earlier player order`() {
        // Both have same points and same card count
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.RED, 5)      // 5 points, 1 card
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.BLUE, 5)     // 5 points, 1 card
        ))

        val winner = GameEngine.determineTimeoutWinner(listOf(player1, player2))

        assertEquals("p1", winner.id)  // Player 1 is earlier in order
    }

    @Test
    fun `round timeout calculates points and moves to RoundOver`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50, hand = listOf(
            Card.number(CardColor.RED, 3)      // 3 points (lowest)
        ))
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 30, hand = listOf(
            Card.number(CardColor.BLUE, 9),    // 9 points
            Card.skip(CardColor.GREEN)         // 20 points = 29 total
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            gamePhase = GamePhase.PLAYING,
            currentRound = 5,
            suddenDeathActive = true,
            suddenDeathSecondsRemaining = 0
        )

        val newState = GameEngine.handleRoundTimeout(state)

        // Player 1 should win (lowest hand points)
        assertEquals("p1", newState.roundWinnerId)

        // Round points should be sum of other players' hands (29)
        assertEquals(29, newState.roundPoints)

        // Winner's score should increase: 50 + 29 = 79
        val winner = newState.players.find { it.id == "p1" }
        assertEquals(79, winner?.totalScore)

        // Round end reason should be timeout
        assertEquals(RoundEndReason.TIMEOUT, newState.roundEndReason)

        // Phase should be RoundOver
        assertEquals(GamePhase.ROUND_OVER, newState.gamePhase)
    }

    @Test
    fun `round timeout on final round moves to MatchOver`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.RED, 1)  // 1 point (lowest)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.BLUE, 9)  // 9 points
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            gamePhase = GamePhase.PLAYING,
            currentRound = 10,  // Final round
            suddenDeathActive = true,
            suddenDeathSecondsRemaining = 0
        )

        val newState = GameEngine.handleRoundTimeout(state)

        assertEquals(GamePhase.MATCH_OVER, newState.gamePhase)
        assertEquals(RoundEndReason.TIMEOUT, newState.roundEndReason)
    }

    @Test
    fun `startNextRound resets all timers`() {
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50)
        val player2 = Player(id = "p2", name = "Player 2", totalScore = 30)

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "p1",
            roundPoints = 20,
            // Timer state from previous round
            turnSecondsRemaining = 5,
            roundSecondsElapsed = 250,
            suddenDeathActive = true,
            suddenDeathSecondsRemaining = 30
        )

        val newState = GameEngine.startNextRound(state)

        // All timers should be reset
        assertEquals(GameState.DEFAULT_TURN_SECONDS, newState.turnSecondsRemaining)
        assertEquals(0, newState.roundSecondsElapsed)
        assertFalse(newState.suddenDeathActive)
        assertEquals(GameState.SUDDEN_DEATH_SECONDS, newState.suddenDeathSecondsRemaining)
    }

    @Test
    fun `advanceTurn resets turn timer`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.TURN_ENDED,
            turnSecondsRemaining = 3  // Low timer
        )

        val newState = GameEngine.advanceTurn(state)

        assertEquals(GameState.DEFAULT_TURN_SECONDS, newState.turnSecondsRemaining)
        assertEquals(1, newState.currentPlayerIndex)
    }

    @Test
    fun `timer constants have correct values`() {
        assertEquals(15, GameState.DEFAULT_TURN_SECONDS)
        assertEquals(300, GameState.ROUND_TIME_LIMIT_SECONDS)
        assertEquals(60, GameState.SUDDEN_DEATH_SECONDS)
    }

    @Test
    fun `roundTimeFormatted returns correct mm ss format`() {
        val state0 = GameState(
            players = listOf(Player(name = "P1"), Player(name = "P2")),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            roundSecondsElapsed = 0
        )
        assertEquals("0:00", state0.roundTimeFormatted)

        val state65 = state0.copy(roundSecondsElapsed = 65)
        assertEquals("1:05", state65.roundTimeFormatted)

        val state300 = state0.copy(roundSecondsElapsed = 300)
        assertEquals("5:00", state300.roundTimeFormatted)
    }

    // ==================== Single Draw Rule Tests ====================

    @Test
    fun `player with no playable cards draws once and enters DREW_CARD phase`() {
        // Set up player with no playable cards (all different color and number from top card)
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7),   // Cannot play on GREEN 3
            Card.number(CardColor.BLUE, 8)    // Cannot play on GREEN 3
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(Card.number(CardColor.YELLOW, 9)),  // Card to draw
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING
        )

        // Verify no playable cards
        val playableCards = GameEngine.getPlayableCards(
            state.currentPlayer.hand,
            state.topCard!!,
            state.currentColor
        )
        assertTrue(playableCards.isEmpty())

        // Draw a card
        val afterDraw = GameEngine.drawCard(state)

        // Player should have 3 cards now (drew 1)
        val player1After = afterDraw.players.find { it.id == "p1" }
        assertEquals(3, player1After?.cardCount)

        // Should be in DREW_CARD phase
        assertEquals(TurnPhase.DREW_CARD, afterDraw.turnPhase)

        // Still player 1's turn (turn not advanced yet)
        assertEquals(0, afterDraw.currentPlayerIndex)
    }

    @Test
    fun `player cannot draw twice in same turn`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(
                Card.number(CardColor.YELLOW, 9),
                Card.number(CardColor.YELLOW, 8)
            ),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING
        )

        // First draw
        val afterFirstDraw = GameEngine.drawCard(state)
        assertEquals(2, afterFirstDraw.players.find { it.id == "p1" }?.cardCount)
        assertEquals(TurnPhase.DREW_CARD, afterFirstDraw.turnPhase)

        // Try to draw again - should be prevented
        val afterSecondDraw = GameEngine.drawCard(afterFirstDraw)

        // Should still have 2 cards (no additional draw)
        assertEquals(2, afterSecondDraw.players.find { it.id == "p1" }?.cardCount)
        assertEquals(TurnPhase.DREW_CARD, afterSecondDraw.turnPhase)
    }

    @Test
    fun `player draws unplayable card and passes turn`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        // The card to draw is not playable on GREEN 3
        val unplayableDrawnCard = Card.number(CardColor.YELLOW, 9)

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(unplayableDrawnCard),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING
        )

        // Draw the card
        val afterDraw = GameEngine.drawCard(state)
        assertEquals(TurnPhase.DREW_CARD, afterDraw.turnPhase)

        // Verify the drawn card is not playable
        assertFalse(GameEngine.canPlayDrawnCard(afterDraw))

        // Pass the turn (since drawn card is not playable)
        val afterPass = GameEngine.passTurn(afterDraw)

        // Turn should advance to player 2
        assertEquals(1, afterPass.currentPlayerIndex)
        assertEquals("p2", afterPass.currentPlayer.id)
        assertEquals(TurnPhase.PLAY_OR_DRAW, afterPass.turnPhase)
    }

    @Test
    fun `player draws playable card and can play it`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)  // Not playable on GREEN 3
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        // The card to draw IS playable on GREEN 3 (same color)
        val playableDrawnCard = Card.number(CardColor.GREEN, 9)

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(playableDrawnCard),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING
        )

        // Draw the card
        val afterDraw = GameEngine.drawCard(state)
        assertEquals(TurnPhase.DREW_CARD, afterDraw.turnPhase)

        // Verify the drawn card IS playable
        assertTrue(GameEngine.canPlayDrawnCard(afterDraw))

        // Get the drawn card
        val drawnCard = GameEngine.getLastDrawnCard(afterDraw)
        assertNotNull(drawnCard)
        assertEquals(CardColor.GREEN, drawnCard!!.color)
        assertEquals(9, drawnCard.number)

        // Play the drawn card
        val afterPlay = GameEngine.playCard(afterDraw, drawnCard)

        // Card should be played successfully
        assertNotNull(afterPlay)

        // Player 1 should be back to 1 card
        val player1After = afterPlay!!.players.find { it.id == "p1" }
        assertEquals(1, player1After?.cardCount)

        // Turn should advance to player 2
        assertEquals(1, afterPlay.currentPlayerIndex)
    }

    @Test
    fun `passTurn only works in DREW_CARD phase`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,  // Not DREW_CARD
            gamePhase = GamePhase.PLAYING
        )

        // Try to pass without drawing first
        val afterPass = GameEngine.passTurn(state)

        // Should still be player 1's turn (pass rejected)
        assertEquals(0, afterPass.currentPlayerIndex)
        assertEquals(TurnPhase.PLAY_OR_DRAW, afterPass.turnPhase)
    }

    @Test
    fun `turn timeout draws one card and advances turn`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(Card.number(CardColor.YELLOW, 9)),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING,
            turnSecondsRemaining = 0
        )

        // Handle turn timeout
        val afterTimeout = GameEngine.handleTurnTimeout(state)

        // Player 1 should have drawn exactly 1 card (now 2 total)
        val player1After = afterTimeout.players.find { it.id == "p1" }
        assertEquals(2, player1After?.cardCount)

        // Turn should have advanced to player 2
        assertEquals(1, afterTimeout.currentPlayerIndex)
        assertEquals("p2", afterTimeout.currentPlayer.id)

        // Turn timer should be reset
        assertEquals(GameState.DEFAULT_TURN_SECONDS, afterTimeout.turnSecondsRemaining)

        // Phase should be PLAY_OR_DRAW for the new player
        assertEquals(TurnPhase.PLAY_OR_DRAW, afterTimeout.turnPhase)
    }

    @Test
    fun `turn timeout does not auto-play the drawn card`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        // The card to draw IS playable, but timeout should NOT auto-play it
        val playableDrawnCard = Card.number(CardColor.GREEN, 9)

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(playableDrawnCard),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING,
            turnSecondsRemaining = 0
        )

        // Handle turn timeout
        val afterTimeout = GameEngine.handleTurnTimeout(state)

        // Player 1 should have the drawn card in hand (not played)
        val player1After = afterTimeout.players.find { it.id == "p1" }
        assertEquals(2, player1After?.cardCount)
        assertTrue(player1After!!.hand.any { it.color == CardColor.GREEN && it.number == 9 })

        // The discard pile should NOT have the drawn card
        assertFalse(afterTimeout.discardPile.any { it.color == CardColor.GREEN && it.number == 9 })

        // Turn should have advanced (drawn card NOT auto-played)
        assertEquals(1, afterTimeout.currentPlayerIndex)
    }

    @Test
    fun `MUST_DRAW phase draws penalty cards and ends turn immediately`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(
                Card.number(CardColor.YELLOW, 1),
                Card.number(CardColor.YELLOW, 2)
            ),
            discardPile = listOf(Card.drawTwo(CardColor.RED)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            turnPhase = TurnPhase.MUST_DRAW,  // From +2 card
            pendingDrawCount = 2,
            gamePhase = GamePhase.PLAYING
        )

        // Draw the penalty cards
        val afterDraw = GameEngine.drawCard(state, 2)

        // Player 1 should have 3 cards (drew 2)
        val player1After = afterDraw.players.find { it.id == "p1" }
        assertEquals(3, player1After?.cardCount)

        // Turn should have advanced automatically to player 2
        assertEquals(1, afterDraw.currentPlayerIndex)
        assertEquals(TurnPhase.PLAY_OR_DRAW, afterDraw.turnPhase)
        assertEquals(0, afterDraw.pendingDrawCount)
    }

    @Test
    fun `getLastDrawnCard returns the most recently added card`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.BLUE, 7)
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val cardToDraw = Card.number(CardColor.YELLOW, 9)

        val state = GameState(
            players = listOf(player1, player2),
            deck = listOf(cardToDraw),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,
            gamePhase = GamePhase.PLAYING
        )

        val afterDraw = GameEngine.drawCard(state)
        val drawnCard = GameEngine.getLastDrawnCard(afterDraw)

        assertNotNull(drawnCard)
        assertEquals(CardColor.YELLOW, drawnCard!!.color)
        assertEquals(9, drawnCard.number)
    }

    @Test
    fun `canPlayDrawnCard returns false when not in DREW_CARD phase`() {
        val player1 = Player(id = "p1", name = "Player 1", hand = listOf(
            Card.number(CardColor.GREEN, 9)  // This card is playable
        ))
        val player2 = Player(id = "p2", name = "Player 2", hand = listOf(
            Card.number(CardColor.RED, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = DeckBuilder.createShuffledDeck(),
            discardPile = listOf(Card.number(CardColor.GREEN, 3)),
            currentPlayerIndex = 0,
            currentColor = CardColor.GREEN,
            turnPhase = TurnPhase.PLAY_OR_DRAW,  // Not DREW_CARD
            gamePhase = GamePhase.PLAYING
        )

        // Should return false because we're not in DREW_CARD phase
        assertFalse(GameEngine.canPlayDrawnCard(state))
    }

    // ==================== Round Initialization Tests ====================

    @Test
    fun `startGame deals 7 cards to every player`() {
        val players = listOf(
            Player.human("Human"),
            Player.bot("Bot 1"),
            Player.bot("Bot 2")
        )

        val state = GameEngine.startGame(players)

        // Every player should have exactly 7 cards
        state.players.forEach { player ->
            assertEquals(7, player.cardCount)
        }
    }

    @Test
    fun `startGame deals cards to bots with matching player IDs`() {
        val humanPlayer = Player.human("Human")
        val bot1 = Player.bot("Bot 1")
        val bot2 = Player.bot("Bot 2")
        val players = listOf(humanPlayer, bot1, bot2)

        val state = GameEngine.startGame(players)

        // Each player in state should have same ID as original and have cards
        assertEquals(humanPlayer.id, state.players[0].id)
        assertEquals(bot1.id, state.players[1].id)
        assertEquals(bot2.id, state.players[2].id)

        // All players should have hands
        assertTrue(state.players[0].hand.isNotEmpty())
        assertTrue(state.players[1].hand.isNotEmpty())
        assertTrue(state.players[2].hand.isNotEmpty())
    }

    @Test
    fun `startNextRound deals 7 cards to every player and preserves scores`() {
        // Set up initial state with scores
        val player1 = Player(id = "p1", name = "Player 1", totalScore = 50, hand = emptyList())
        val player2 = Player(id = "p2", name = "Bot 1", isBot = true, totalScore = 30, hand = listOf(
            Card.number(CardColor.BLUE, 5)
        ))

        val state = GameState(
            players = listOf(player1, player2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 3,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "p1",
            roundPoints = 20
        )

        val newState = GameEngine.startNextRound(state)

        // Every player should have exactly 7 cards
        newState.players.forEach { player ->
            assertEquals(7, player.cardCount)
        }

        // Scores should be preserved
        val newPlayer1 = newState.players.find { it.id == "p1" }
        val newPlayer2 = newState.players.find { it.id == "p2" }
        assertEquals(50, newPlayer1?.totalScore)
        assertEquals(30, newPlayer2?.totalScore)

        // Round should be incremented
        assertEquals(4, newState.currentRound)
    }

    @Test
    fun `startGame initializes starting card as a number card`() {
        val players = listOf(
            Player.human("Human"),
            Player.bot("Bot 1")
        )

        // Run multiple times to verify starting card is always a number
        repeat(10) {
            val state = GameEngine.startGame(players)
            assertEquals(CardType.NUMBER, state.topCard?.type)
        }
    }

    @Test
    fun `startGame sets currentColor from starting card`() {
        val players = listOf(
            Player.human("Human"),
            Player.bot("Bot 1")
        )

        val state = GameEngine.startGame(players)

        // Current color should match the starting card's color
        assertEquals(state.topCard?.color, state.currentColor)
    }

    @Test
    fun `startGame sets first player as current player`() {
        val players = listOf(
            Player.human("Human"),
            Player.bot("Bot 1")
        )

        val state = GameEngine.startGame(players)

        // First player should be at index 0
        assertEquals(0, state.currentPlayerIndex)
        assertEquals("Human", state.currentPlayer.name)
    }

    @Test
    fun `startGame sets phase to PLAYING and turnPhase to PLAY_OR_DRAW`() {
        val players = listOf(
            Player.human("Human"),
            Player.bot("Bot 1")
        )

        val state = GameEngine.startGame(players)

        assertEquals(GamePhase.PLAYING, state.gamePhase)
        assertEquals(TurnPhase.PLAY_OR_DRAW, state.turnPhase)
    }

    @Test
    fun `startNextRound resets bot hands and deals new cards`() {
        // Set up bots with different remaining cards
        val bot1 = Player(id = "b1", name = "Bot 1", isBot = true, totalScore = 20, hand = listOf(
            Card.number(CardColor.RED, 1),
            Card.number(CardColor.RED, 2)
        ))
        val bot2 = Player(id = "b2", name = "Bot 2", isBot = true, totalScore = 40, hand = listOf(
            Card.wildColor(),
            Card.skip(CardColor.BLUE),
            Card.number(CardColor.GREEN, 9)
        ))
        val human = Player(id = "h1", name = "Human", totalScore = 60, hand = emptyList())

        val state = GameState(
            players = listOf(human, bot1, bot2),
            deck = emptyList(),
            discardPile = listOf(Card.number(CardColor.RED, 5)),
            currentPlayerIndex = 0,
            currentColor = CardColor.RED,
            currentRound = 5,
            gamePhase = GamePhase.ROUND_OVER,
            roundWinnerId = "h1",
            roundPoints = 50
        )

        val newState = GameEngine.startNextRound(state)

        // All players including bots should have exactly 7 cards
        val newBot1 = newState.players.find { it.id == "b1" }
        val newBot2 = newState.players.find { it.id == "b2" }
        val newHuman = newState.players.find { it.id == "h1" }

        assertEquals(7, newBot1?.cardCount)
        assertEquals(7, newBot2?.cardCount)
        assertEquals(7, newHuman?.cardCount)

        // Bot IDs should match
        assertEquals("b1", newBot1?.id)
        assertEquals("b2", newBot2?.id)
        assertEquals("h1", newHuman?.id)

        // Scores preserved
        assertEquals(20, newBot1?.totalScore)
        assertEquals(40, newBot2?.totalScore)
        assertEquals(60, newHuman?.totalScore)
    }
}
