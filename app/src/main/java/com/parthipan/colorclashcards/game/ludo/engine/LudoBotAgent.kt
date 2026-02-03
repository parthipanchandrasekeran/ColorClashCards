package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.*
import kotlin.random.Random

/**
 * AI agent for Ludo bot players.
 * Implements different difficulty levels with various strategies.
 */
object LudoBotAgent {

    /**
     * Choose which token to move based on difficulty and game state.
     *
     * @param state Current game state
     * @param movableTokens List of tokens that can move
     * @param difficulty Bot difficulty ("easy", "normal", "hard")
     * @return Token ID to move
     */
    fun chooseToken(
        state: LudoGameState,
        movableTokens: List<Token>,
        difficulty: String = "normal"
    ): Int {
        if (movableTokens.isEmpty()) return -1
        if (movableTokens.size == 1) return movableTokens.first().id

        return when (difficulty.lowercase()) {
            "easy" -> chooseRandomToken(movableTokens)
            "hard" -> chooseStrategicToken(state, movableTokens, aggressive = true)
            else -> chooseStrategicToken(state, movableTokens, aggressive = false)
        }
    }

    /**
     * Easy mode: Random selection.
     */
    private fun chooseRandomToken(movableTokens: List<Token>): Int {
        return movableTokens.random().id
    }

    /**
     * Normal/Hard mode: Strategic selection based on priorities.
     */
    private fun chooseStrategicToken(
        state: LudoGameState,
        movableTokens: List<Token>,
        aggressive: Boolean
    ): Int {
        val currentPlayer = state.currentPlayer
        val diceValue = state.diceValue ?: return movableTokens.first().id

        // Score each token based on strategic value
        val scoredTokens = movableTokens.map { token ->
            val score = calculateTokenScore(state, currentPlayer, token, diceValue, aggressive)
            token to score
        }

        // Return token with highest score
        return scoredTokens.maxByOrNull { it.second }?.first?.id ?: movableTokens.first().id
    }

    /**
     * Calculate a strategic score for moving a token.
     * Higher score = better move.
     */
    private fun calculateTokenScore(
        state: LudoGameState,
        player: LudoPlayer,
        token: Token,
        diceValue: Int,
        aggressive: Boolean
    ): Int {
        var score = 0

        when (token.state) {
            TokenState.HOME -> {
                // Exiting home is valuable, especially early game
                val tokensAtHome = player.tokens.count { it.state == TokenState.HOME }
                score += 50 + (tokensAtHome * 10) // Encourage getting tokens out
            }
            TokenState.ACTIVE -> {
                val newPosition = token.position + diceValue
                val currentAbsPos = LudoBoard.toAbsolutePosition(token.position, player.color)
                val newAbsPos = if (newPosition <= LudoBoard.RING_END) {
                    LudoBoard.toAbsolutePosition(newPosition, player.color)
                } else -1

                // Priority 1: Reaching finish
                if (newPosition >= LudoBoard.FINISH_POSITION) {
                    score += 200
                }

                // Priority 2: Entering home stretch (safe zone)
                if (newPosition > LudoBoard.RING_END && token.position <= LudoBoard.RING_END) {
                    score += 150
                }

                // Priority 3: Capturing opponent (if aggressive)
                if (aggressive && newAbsPos >= 0 && !LudoBoard.isSafeCell(newAbsPos)) {
                    val captureTarget = findCaptureTarget(state, player.id, newAbsPos)
                    if (captureTarget != null) {
                        // Prioritize capturing tokens that are closer to finish
                        score += 100 + (captureTarget.position / 2)
                    }
                }

                // Priority 4: Moving to a safe cell
                if (newAbsPos >= 0 && LudoBoard.isSafeCell(newAbsPos)) {
                    score += 40
                }

                // Priority 5: Escaping danger (if currently in danger)
                if (isTokenInDanger(state, player, token)) {
                    score += 60
                }

                // Priority 6: Progress toward finish
                score += (newPosition - token.position) * 2

                // Penalty: Leaving a safe cell for an unsafe one
                if (currentAbsPos >= 0 && LudoBoard.isSafeCell(currentAbsPos) &&
                    newAbsPos >= 0 && !LudoBoard.isSafeCell(newAbsPos)) {
                    score -= 20
                }

                // Penalty: Moving token that's already close to finish (unless it finishes)
                if (token.position > 40 && newPosition < LudoBoard.FINISH_POSITION) {
                    score -= 10
                }
            }
            TokenState.FINISHED -> {
                // Cannot move finished tokens
                score = Int.MIN_VALUE
            }
        }

        // Add small random factor to avoid predictable play
        score += Random.nextInt(0, 5)

        return score
    }

    /**
     * Find if there's an opponent token that can be captured at the given position.
     */
    private fun findCaptureTarget(
        state: LudoGameState,
        movingPlayerId: String,
        absolutePosition: Int
    ): Token? {
        for (player in state.players) {
            if (player.id == movingPlayerId) continue

            for (token in player.tokens) {
                if (token.state != TokenState.ACTIVE) continue
                if (token.position > LudoBoard.RING_END) continue

                val tokenAbsPos = LudoBoard.toAbsolutePosition(token.position, player.color)
                if (tokenAbsPos == absolutePosition) {
                    return token
                }
            }
        }
        return null
    }

    /**
     * Check if a token is in danger of being captured.
     */
    private fun isTokenInDanger(state: LudoGameState, player: LudoPlayer, token: Token): Boolean {
        if (token.state != TokenState.ACTIVE) return false
        if (token.position > LudoBoard.RING_END) return false // In home stretch, safe

        val tokenAbsPos = LudoBoard.toAbsolutePosition(token.position, player.color)
        if (LudoBoard.isSafeCell(tokenAbsPos)) return false // On safe cell

        // Check if any opponent can reach this position with a dice roll
        for (opponent in state.players) {
            if (opponent.id == player.id) continue

            for (oppToken in opponent.tokens) {
                if (oppToken.state != TokenState.ACTIVE) continue
                if (oppToken.position > LudoBoard.RING_END) continue

                // Check if opponent can reach our position with dice 1-6
                for (dice in 1..6) {
                    val oppNewPos = oppToken.position + dice
                    if (oppNewPos > LudoBoard.RING_END) continue

                    val oppNewAbsPos = LudoBoard.toAbsolutePosition(oppNewPos, opponent.color)
                    if (oppNewAbsPos == tokenAbsPos) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Get thinking delay for bot moves (in milliseconds).
     * Makes the game feel more natural.
     */
    fun getThinkingDelayMs(difficulty: String = "normal"): Long {
        return when (difficulty.lowercase()) {
            "easy" -> Random.nextLong(500, 1000)
            "hard" -> Random.nextLong(1000, 2000)
            else -> Random.nextLong(700, 1500)
        }
    }

    /**
     * Simulate a dice roll (for offline games).
     * Uses simple random for local play.
     */
    fun rollDice(): Int {
        return Random.nextInt(1, 7)
    }
}
