package com.parthipan.colorclashcards.testutil

import com.parthipan.colorclashcards.game.model.*
import kotlin.random.Random

/**
 * Shared test utilities for both Color Clash Cards and Ludo games.
 *
 * Provides:
 * - GameState builders for controlled test scenarios
 * - Deterministic RNG for reproducible tests
 * - Fake clock for timer-based logic
 * - Common test fixtures
 */

// ============================================================
// Deterministic Random Number Generator
// ============================================================

/**
 * Deterministic RNG for reproducible test results.
 *
 * Usage:
 * ```kotlin
 * val rng = DeterministicRandom(seed = 42)
 * val value = rng.nextInt(6) // Always produces same sequence
 * ```
 */
class DeterministicRandom(seed: Long = 12345L) {
    private val random = Random(seed)

    fun nextInt(): Int = random.nextInt()
    fun nextInt(until: Int): Int = random.nextInt(until)
    fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)
    fun nextFloat(): Float = random.nextFloat()
    fun nextDouble(): Double = random.nextDouble()
    fun nextBoolean(): Boolean = random.nextBoolean()

    /**
     * Shuffle a list deterministically.
     */
    fun <T> shuffle(list: List<T>): List<T> {
        return list.shuffled(random)
    }

    /**
     * Pick a random element from list.
     */
    fun <T> pick(list: List<T>): T {
        require(list.isNotEmpty()) { "Cannot pick from empty list" }
        return list[nextInt(list.size)]
    }

    /**
     * Generate a sequence of dice rolls (1-6).
     */
    fun diceSequence(count: Int): List<Int> {
        return (1..count).map { nextInt(1, 7) }
    }

    companion object {
        /**
         * Create RNG with known sequence for specific test scenarios.
         */
        fun withSequence(vararg values: Int): SequenceRandom {
            return SequenceRandom(values.toList())
        }
    }
}

/**
 * RNG that returns predefined sequence of values.
 * Useful for testing specific scenarios.
 */
class SequenceRandom(private val sequence: List<Int>) {
    private var index = 0

    fun nextInt(): Int {
        val value = sequence[index % sequence.size]
        index++
        return value
    }

    fun nextInt(until: Int): Int = nextInt() % until
    fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)

    fun reset() {
        index = 0
    }
}

// ============================================================
// Fake Clock for Timer Testing
// ============================================================

/**
 * Fake clock for testing time-dependent logic.
 *
 * Usage:
 * ```kotlin
 * val clock = FakeClock(initialTime = 0L)
 * clock.advanceBy(5000) // Advance 5 seconds
 * assertEquals(5000, clock.currentTimeMillis())
 * ```
 */
class FakeClock(initialTime: Long = 0L) {
    private var _currentTime: Long = initialTime

    fun currentTimeMillis(): Long = _currentTime

    fun advanceBy(millis: Long) {
        require(millis >= 0) { "Cannot go back in time" }
        _currentTime += millis
    }

    fun advanceBySeconds(seconds: Int) {
        advanceBy(seconds * 1000L)
    }

    fun setTime(millis: Long) {
        _currentTime = millis
    }

    fun reset() {
        _currentTime = 0L
    }
}

// ============================================================
// Color Clash Card Game State Builder
// ============================================================

/**
 * Builder for creating controlled GameState for tests.
 *
 * Usage:
 * ```kotlin
 * val state = CardGameStateBuilder()
 *     .withPlayers(2)
 *     .withPlayerHand(0, listOf(Card.skip(CardColor.RED)))
 *     .withTopCard(Card.number(CardColor.RED, 5))
 *     .withCurrentPlayer(0)
 *     .build()
 * ```
 */
class CardGameStateBuilder {
    private var players: MutableList<Player> = mutableListOf()
    private var deck: List<Card>? = null  // null = not set, empty list = explicitly empty
    private var discardPile: List<Card> = emptyList()
    private var currentPlayerIndex: Int = 0
    private var direction: PlayDirection = PlayDirection.CLOCKWISE
    private var currentColor: CardColor = CardColor.RED
    private var turnPhase: TurnPhase = TurnPhase.PLAY_OR_DRAW
    private var pendingDrawCount: Int = 0
    private var currentRound: Int = 1
    private var gamePhase: GamePhase = GamePhase.PLAYING

    /**
     * Add default players (human + bots).
     */
    fun withPlayers(count: Int): CardGameStateBuilder {
        require(count in 2..4) { "Must have 2-4 players" }
        players = mutableListOf(Player.human("Player"))
        repeat(count - 1) { i ->
            players.add(Player.bot("Bot ${i + 1}"))
        }
        return this
    }

    /**
     * Set specific players.
     */
    fun withPlayers(vararg playerList: Player): CardGameStateBuilder {
        players = playerList.toMutableList()
        return this
    }

    /**
     * Set a player's hand.
     */
    fun withPlayerHand(playerIndex: Int, hand: List<Card>): CardGameStateBuilder {
        if (playerIndex < players.size) {
            players[playerIndex] = players[playerIndex].copy(hand = hand)
        }
        return this
    }

    /**
     * Give all players default hands of 7 cards.
     */
    fun withDefaultHands(): CardGameStateBuilder {
        val colors = CardColor.playableColors()
        players = players.mapIndexed { index, player ->
            val hand = (0..6).map { cardIndex ->
                Card.number(colors[cardIndex % 4], cardIndex)
            }
            player.copy(hand = hand)
        }.toMutableList()
        return this
    }

    /**
     * Set the deck.
     */
    fun withDeck(cards: List<Card>): CardGameStateBuilder {
        deck = cards
        return this
    }

    /**
     * Create a deck with specific cards at the top.
     */
    fun withDeckTopCards(vararg topCards: Card): CardGameStateBuilder {
        deck = topCards.toList() + createFillerDeck(50)
        return this
    }

    /**
     * Set the discard pile (top card is last in list).
     */
    fun withDiscardPile(cards: List<Card>): CardGameStateBuilder {
        discardPile = cards
        return this
    }

    /**
     * Set the top card of discard pile.
     */
    fun withTopCard(card: Card): CardGameStateBuilder {
        discardPile = listOf(card)
        currentColor = if (card.type.isWild()) CardColor.RED else card.color
        return this
    }

    /**
     * Set current player index.
     */
    fun withCurrentPlayer(index: Int): CardGameStateBuilder {
        currentPlayerIndex = index
        return this
    }

    /**
     * Set play direction.
     */
    fun withDirection(dir: PlayDirection): CardGameStateBuilder {
        direction = dir
        return this
    }

    /**
     * Set current color.
     */
    fun withCurrentColor(color: CardColor): CardGameStateBuilder {
        currentColor = color
        return this
    }

    /**
     * Set turn phase.
     */
    fun withTurnPhase(phase: TurnPhase): CardGameStateBuilder {
        turnPhase = phase
        return this
    }

    /**
     * Set pending draw count (for +2/+4 effects).
     */
    fun withPendingDraw(count: Int): CardGameStateBuilder {
        pendingDrawCount = count
        turnPhase = TurnPhase.MUST_DRAW
        return this
    }

    /**
     * Set current round.
     */
    fun withRound(round: Int): CardGameStateBuilder {
        currentRound = round
        return this
    }

    /**
     * Set game phase.
     */
    fun withGamePhase(phase: GamePhase): CardGameStateBuilder {
        gamePhase = phase
        return this
    }

    /**
     * Build the GameState.
     */
    fun build(): GameState {
        require(players.size >= 2) { "Must have at least 2 players" }

        // Ensure we have a deck (only create filler if not explicitly set)
        val finalDeck = deck ?: createFillerDeck(50)

        // Ensure we have a discard pile
        val finalDiscardPile = if (discardPile.isEmpty()) {
            listOf(Card.number(CardColor.RED, 5))
        } else {
            discardPile
        }

        return GameState(
            players = players,
            deck = finalDeck,
            discardPile = finalDiscardPile,
            currentPlayerIndex = currentPlayerIndex,
            direction = direction,
            currentColor = currentColor,
            turnPhase = turnPhase,
            pendingDrawCount = pendingDrawCount,
            currentRound = currentRound,
            gamePhase = gamePhase,
            lastPlayedCard = discardPile.lastOrNull()
        )
    }

    private fun createFillerDeck(size: Int): List<Card> {
        val colors = CardColor.playableColors()
        return (0 until size).map { i ->
            Card.number(colors[i % 4], i % 10)
        }
    }
}

// ============================================================
// Card Factory Helpers
// ============================================================

/**
 * Create cards easily for tests.
 */
object TestCards {
    // Number cards
    fun red(number: Int) = Card.number(CardColor.RED, number)
    fun blue(number: Int) = Card.number(CardColor.BLUE, number)
    fun green(number: Int) = Card.number(CardColor.GREEN, number)
    fun yellow(number: Int) = Card.number(CardColor.YELLOW, number)

    // Action cards
    fun redSkip() = Card.skip(CardColor.RED)
    fun blueSkip() = Card.skip(CardColor.BLUE)
    fun greenSkip() = Card.skip(CardColor.GREEN)
    fun yellowSkip() = Card.skip(CardColor.YELLOW)

    fun redReverse() = Card.reverse(CardColor.RED)
    fun blueReverse() = Card.reverse(CardColor.BLUE)
    fun greenReverse() = Card.reverse(CardColor.GREEN)
    fun yellowReverse() = Card.reverse(CardColor.YELLOW)

    fun redDrawTwo() = Card.drawTwo(CardColor.RED)
    fun blueDrawTwo() = Card.drawTwo(CardColor.BLUE)
    fun greenDrawTwo() = Card.drawTwo(CardColor.GREEN)
    fun yellowDrawTwo() = Card.drawTwo(CardColor.YELLOW)

    // Wild cards
    fun wild() = Card.wildColor()
    fun wildDrawFour() = Card.wildDrawFour()

    /**
     * Create a hand of specific cards.
     */
    fun hand(vararg cards: Card): List<Card> = cards.toList()

    /**
     * Create a simple hand with numbers.
     */
    fun numberedHand(color: CardColor, vararg numbers: Int): List<Card> {
        return numbers.map { Card.number(color, it) }
    }
}

// ============================================================
// Test Assertions
// ============================================================

/**
 * Custom assertions for game state testing.
 */
object GameAssertions {

    fun assertPlayerHasCards(state: GameState, playerIndex: Int, expectedCount: Int) {
        val actual = state.players[playerIndex].cardCount
        assert(actual == expectedCount) {
            "Player $playerIndex expected $expectedCount cards, but has $actual"
        }
    }

    fun assertCurrentPlayer(state: GameState, expectedIndex: Int) {
        assert(state.currentPlayerIndex == expectedIndex) {
            "Expected current player $expectedIndex, but is ${state.currentPlayerIndex}"
        }
    }

    fun assertCurrentColor(state: GameState, expectedColor: CardColor) {
        assert(state.currentColor == expectedColor) {
            "Expected color $expectedColor, but is ${state.currentColor}"
        }
    }

    fun assertDirection(state: GameState, expectedDirection: PlayDirection) {
        assert(state.direction == expectedDirection) {
            "Expected direction $expectedDirection, but is ${state.direction}"
        }
    }

    fun assertTurnPhase(state: GameState, expectedPhase: TurnPhase) {
        assert(state.turnPhase == expectedPhase) {
            "Expected turn phase $expectedPhase, but is ${state.turnPhase}"
        }
    }

    fun assertGamePhase(state: GameState, expectedPhase: GamePhase) {
        assert(state.gamePhase == expectedPhase) {
            "Expected game phase $expectedPhase, but is ${state.gamePhase}"
        }
    }

    fun assertPlayerHasCard(state: GameState, playerIndex: Int, card: Card) {
        val hasCard = state.players[playerIndex].hand.any { it.id == card.id }
        assert(hasCard) {
            "Player $playerIndex does not have card ${card.displayName()}"
        }
    }

    fun assertPlayerDoesNotHaveCard(state: GameState, playerIndex: Int, card: Card) {
        val hasCard = state.players[playerIndex].hand.any { it.id == card.id }
        assert(!hasCard) {
            "Player $playerIndex should not have card ${card.displayName()}"
        }
    }

    fun assertTopCard(state: GameState, expectedCard: Card) {
        assert(state.topCard?.id == expectedCard.id) {
            "Expected top card ${expectedCard.displayName()}, but is ${state.topCard?.displayName()}"
        }
    }

    fun assertWinner(state: GameState, expectedPlayerId: String) {
        assert(state.winner?.id == expectedPlayerId) {
            "Expected winner $expectedPlayerId, but is ${state.winner?.id}"
        }
    }

    fun assertNoWinner(state: GameState) {
        assert(state.winner == null) {
            "Expected no winner, but ${state.winner?.name} won"
        }
    }
}

// ============================================================
// CardColor Extension
// ============================================================

fun CardColor.Companion.playableColors(): List<CardColor> {
    return listOf(CardColor.RED, CardColor.BLUE, CardColor.GREEN, CardColor.YELLOW)
}
