package com.parthipan.colorclashcards.testutil

import com.parthipan.colorclashcards.game.ludo.model.*

/**
 * Shared test utilities for Ludo game testing.
 *
 * Provides:
 * - LudoGameState builders
 * - Deterministic dice rolls
 * - Board position helpers
 */

// ============================================================
// Ludo Game State Builder
// ============================================================

/**
 * Builder for creating controlled LudoGameState for tests.
 *
 * Usage:
 * ```kotlin
 * val state = LudoGameStateBuilder()
 *     .withPlayers(2)
 *     .withTokenAt(player = 0, token = 0, position = 10)
 *     .withCurrentPlayer(0)
 *     .withDiceValue(6)
 *     .build()
 * ```
 */
class LudoGameStateBuilder {
    private var players: MutableList<LudoPlayer> = mutableListOf()
    private var currentPlayerIndex: Int = 0
    private var diceValue: Int? = null
    private var gameStatus: LudoGameStatus = LudoGameStatus.IN_PROGRESS
    private var canRollDice: Boolean = true
    private var mustSelectToken: Boolean = false
    private var consecutiveSixes: Int = 0

    /**
     * Add players with default colors.
     */
    fun withPlayers(count: Int, includeBots: Boolean = true): LudoGameStateBuilder {
        require(count in 2..4) { "Ludo requires 2-4 players" }
        val colors = listOf(TokenColor.RED, TokenColor.BLUE, TokenColor.GREEN, TokenColor.YELLOW)
        players = (0 until count).map { index ->
            LudoPlayer(
                id = "player_$index",
                name = if (index == 0) "Player" else "Bot $index",
                color = colors[index],
                isBot = index > 0 && includeBots,
                tokens = createDefaultTokens(colors[index])
            )
        }.toMutableList()
        return this
    }

    /**
     * Set specific players.
     */
    fun withPlayers(vararg playerList: LudoPlayer): LudoGameStateBuilder {
        players = playerList.toMutableList()
        return this
    }

    /**
     * Move a specific token to a position.
     * @param player Player index (0-3)
     * @param token Token index (0-3)
     * @param position Board position (-1 = home, 0-56 = track, 57 = finished)
     */
    fun withTokenAt(player: Int, token: Int, position: Int): LudoGameStateBuilder {
        if (player < players.size) {
            val playerObj = players[player]
            val tokens = playerObj.tokens.toMutableList()
            if (token < tokens.size) {
                val state = when {
                    position == -1 -> TokenState.HOME
                    position == 57 -> TokenState.FINISHED
                    else -> TokenState.ACTIVE
                }
                tokens[token] = tokens[token].copy(position = position, state = state)
                players[player] = playerObj.copy(tokens = tokens)
            }
        }
        return this
    }

    /**
     * Move all tokens for a player out of home.
     */
    fun withAllTokensActive(player: Int, startPosition: Int = 0): LudoGameStateBuilder {
        if (player < players.size) {
            val playerObj = players[player]
            val tokens = playerObj.tokens.mapIndexed { index, token ->
                token.copy(
                    position = startPosition + index,
                    state = TokenState.ACTIVE
                )
            }
            players[player] = playerObj.copy(tokens = tokens)
        }
        return this
    }

    /**
     * Set a player to have won (all tokens finished).
     */
    fun withPlayerFinished(player: Int): LudoGameStateBuilder {
        if (player < players.size) {
            val playerObj = players[player]
            val tokens = playerObj.tokens.map { token ->
                token.copy(position = 57, state = TokenState.FINISHED)
            }
            players[player] = playerObj.copy(tokens = tokens)
        }
        return this
    }

    /**
     * Set current player index.
     */
    fun withCurrentPlayer(index: Int): LudoGameStateBuilder {
        currentPlayerIndex = index
        return this
    }

    /**
     * Set dice value (null = not rolled yet).
     */
    fun withDiceValue(value: Int?): LudoGameStateBuilder {
        diceValue = value
        if (value != null) {
            canRollDice = false
            mustSelectToken = true
        }
        return this
    }

    /**
     * Set game status.
     */
    fun withGameStatus(status: LudoGameStatus): LudoGameStateBuilder {
        gameStatus = status
        return this
    }

    /**
     * Set dice roll state.
     */
    fun withCanRollDice(can: Boolean): LudoGameStateBuilder {
        canRollDice = can
        return this
    }

    /**
     * Set token selection state.
     */
    fun withMustSelectToken(must: Boolean): LudoGameStateBuilder {
        mustSelectToken = must
        return this
    }

    /**
     * Set consecutive sixes count.
     */
    fun withConsecutiveSixes(count: Int): LudoGameStateBuilder {
        consecutiveSixes = count
        return this
    }

    /**
     * Build the LudoGameState.
     */
    fun build(): LudoGameState {
        require(players.size >= 2) { "Must have at least 2 players" }

        return LudoGameState(
            players = players,
            currentPlayerIndex = currentPlayerIndex,
            diceValue = diceValue,
            gameStatus = gameStatus,
            canRollDice = canRollDice,
            mustSelectToken = mustSelectToken,
            consecutiveSixes = consecutiveSixes
        )
    }

    private fun createDefaultTokens(color: TokenColor): List<Token> {
        return (0..3).map { index ->
            Token(
                id = index,
                color = color,
                state = TokenState.HOME,
                position = -1
            )
        }
    }
}

// ============================================================
// Ludo Models (Simplified for testing)
// ============================================================

// Note: These are simplified models for testing.
// Replace with actual imports from your Ludo module.

enum class TokenColor { RED, BLUE, GREEN, YELLOW }
enum class TokenState { HOME, ACTIVE, FINISHED }
enum class LudoGameStatus { IN_PROGRESS, FINISHED }

data class Token(
    val id: Int,
    val color: TokenColor,
    val state: TokenState,
    val position: Int // -1 = home, 0-56 = track, 57 = finished
)

data class LudoPlayer(
    val id: String,
    val name: String,
    val color: TokenColor,
    val isBot: Boolean,
    val tokens: List<Token>
) {
    val hasWon: Boolean
        get() = tokens.all { it.state == TokenState.FINISHED }

    val tokensAtHome: List<Token>
        get() = tokens.filter { it.state == TokenState.HOME }

    val activeTokens: List<Token>
        get() = tokens.filter { it.state == TokenState.ACTIVE }
}

data class LudoGameState(
    val players: List<LudoPlayer>,
    val currentPlayerIndex: Int,
    val diceValue: Int?,
    val gameStatus: LudoGameStatus,
    val canRollDice: Boolean,
    val mustSelectToken: Boolean,
    val consecutiveSixes: Int
) {
    val currentPlayer: LudoPlayer
        get() = players[currentPlayerIndex]

    val isGameOver: Boolean
        get() = gameStatus == LudoGameStatus.FINISHED

    fun getNextPlayerIndex(): Int {
        return (currentPlayerIndex + 1) % players.size
    }
}

// ============================================================
// Deterministic Dice for Ludo
// ============================================================

/**
 * Deterministic dice roller for reproducible Ludo tests.
 */
class DeterministicDice(private val sequence: List<Int>) {
    private var index = 0

    init {
        require(sequence.all { it in 1..6 }) { "All dice values must be 1-6" }
    }

    fun roll(): Int {
        val value = sequence[index % sequence.size]
        index++
        return value
    }

    fun reset() {
        index = 0
    }

    companion object {
        /**
         * Create dice that always rolls the same value.
         */
        fun always(value: Int): DeterministicDice {
            require(value in 1..6) { "Value must be 1-6" }
            return DeterministicDice(listOf(value))
        }

        /**
         * Create dice with specific sequence.
         */
        fun withSequence(vararg values: Int): DeterministicDice {
            return DeterministicDice(values.toList())
        }

        /**
         * Create dice that rolls 6 a specific number of times, then other values.
         */
        fun consecutiveSixes(count: Int, thenRoll: Int = 1): DeterministicDice {
            val sequence = List(count) { 6 } + listOf(thenRoll)
            return DeterministicDice(sequence)
        }
    }
}

// ============================================================
// Board Position Helpers
// ============================================================

/**
 * Helpers for Ludo board positions.
 */
object LudoBoardPositions {
    // Start positions for each color (where tokens enter the track)
    val START_POSITIONS = mapOf(
        TokenColor.RED to 0,
        TokenColor.BLUE to 13,
        TokenColor.GREEN to 26,
        TokenColor.YELLOW to 39
    )

    // Home stretch entry positions (last position before home stretch)
    val HOME_STRETCH_ENTRY = mapOf(
        TokenColor.RED to 51,
        TokenColor.BLUE to 12,
        TokenColor.GREEN to 25,
        TokenColor.YELLOW to 38
    )

    // Safe spots (cannot be captured)
    val SAFE_SPOTS = listOf(0, 8, 13, 21, 26, 34, 39, 47)

    /**
     * Calculate new position after moving.
     */
    fun calculateNewPosition(
        currentPosition: Int,
        diceValue: Int,
        color: TokenColor
    ): Int {
        if (currentPosition == -1) {
            // Exiting home - needs a 6
            return if (diceValue == 6) START_POSITIONS[color]!! else -1
        }

        val newPosition = currentPosition + diceValue

        // Check if entering home stretch
        val homeEntry = HOME_STRETCH_ENTRY[color]!!
        if (currentPosition <= homeEntry && newPosition > homeEntry) {
            // In home stretch (simplified)
            val stepsIntoHomeStretch = newPosition - homeEntry
            return if (stepsIntoHomeStretch <= 6) {
                50 + stepsIntoHomeStretch // Home stretch positions 51-57
            } else {
                // Overshoot - stay in place
                currentPosition
            }
        }

        // Normal movement (wrap around at 52)
        return newPosition % 52
    }

    /**
     * Check if position is safe spot.
     */
    fun isSafeSpot(position: Int): Boolean {
        return position in SAFE_SPOTS
    }

    /**
     * Check if token can exit home (requires 6).
     */
    fun canExitHome(diceValue: Int): Boolean {
        return diceValue == 6
    }
}

// ============================================================
// Ludo Test Assertions
// ============================================================

/**
 * Custom assertions for Ludo game testing.
 */
object LudoAssertions {

    fun assertCurrentPlayer(state: LudoGameState, expectedIndex: Int) {
        assert(state.currentPlayerIndex == expectedIndex) {
            "Expected player $expectedIndex, but is ${state.currentPlayerIndex}"
        }
    }

    fun assertDiceValue(state: LudoGameState, expectedValue: Int?) {
        assert(state.diceValue == expectedValue) {
            "Expected dice $expectedValue, but is ${state.diceValue}"
        }
    }

    fun assertTokenPosition(
        state: LudoGameState,
        playerIndex: Int,
        tokenIndex: Int,
        expectedPosition: Int
    ) {
        val actual = state.players[playerIndex].tokens[tokenIndex].position
        assert(actual == expectedPosition) {
            "Token $tokenIndex of player $playerIndex expected at $expectedPosition, but at $actual"
        }
    }

    fun assertTokenState(
        state: LudoGameState,
        playerIndex: Int,
        tokenIndex: Int,
        expectedState: TokenState
    ) {
        val actual = state.players[playerIndex].tokens[tokenIndex].state
        assert(actual == expectedState) {
            "Token $tokenIndex of player $playerIndex expected $expectedState, but is $actual"
        }
    }

    fun assertPlayerHasWon(state: LudoGameState, playerIndex: Int) {
        assert(state.players[playerIndex].hasWon) {
            "Expected player $playerIndex to have won"
        }
    }

    fun assertGameOver(state: LudoGameState) {
        assert(state.isGameOver) {
            "Expected game to be over"
        }
    }

    fun assertGameInProgress(state: LudoGameState) {
        assert(!state.isGameOver) {
            "Expected game to be in progress"
        }
    }

    fun assertCanRollDice(state: LudoGameState) {
        assert(state.canRollDice) {
            "Expected to be able to roll dice"
        }
    }

    fun assertMustSelectToken(state: LudoGameState) {
        assert(state.mustSelectToken) {
            "Expected to must select token"
        }
    }
}
