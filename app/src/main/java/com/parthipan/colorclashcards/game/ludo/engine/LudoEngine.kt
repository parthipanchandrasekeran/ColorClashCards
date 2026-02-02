package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.*

/**
 * Core game engine for Ludo.
 * Contains pure functions for game logic - no side effects.
 * All functions take a state and return a new state.
 */
object LudoEngine {

    /**
     * Roll the dice and return possible moves.
     *
     * @param state Current game state
     * @param diceValue The rolled dice value (1-6)
     * @return Updated game state with dice value and movable tokens info
     */
    fun rollDice(state: LudoGameState, diceValue: Int): LudoGameState {
        require(diceValue in 1..6) { "Dice value must be between 1 and 6" }
        require(state.canRollDice) { "Cannot roll dice in current state" }

        val currentPlayer = state.currentPlayer
        val movableTokens = getMovableTokens(currentPlayer, diceValue)

        // Handle consecutive sixes
        var consecutiveSixes = state.consecutiveSixes
        var skipTurn = false

        if (diceValue == 6) {
            consecutiveSixes++
            if (consecutiveSixes >= LudoGameState.MAX_CONSECUTIVE_SIXES) {
                // Three sixes in a row - lose turn
                skipTurn = true
                consecutiveSixes = 0
            }
        } else {
            consecutiveSixes = 0
        }

        // If no movable tokens or skipping turn, advance to next player
        if (movableTokens.isEmpty() || skipTurn) {
            return advanceToNextPlayer(state.copy(
                diceValue = diceValue,
                consecutiveSixes = 0
            ))
        }

        return state.copy(
            diceValue = diceValue,
            canRollDice = false,
            mustSelectToken = true,
            consecutiveSixes = consecutiveSixes
        )
    }

    /**
     * Get list of tokens that can be moved with the given dice value.
     *
     * @param player The player whose tokens to check
     * @param diceValue The dice value rolled
     * @return List of tokens that can legally move
     */
    fun getMovableTokens(player: LudoPlayer, diceValue: Int): List<Token> {
        return player.tokens.filter { token ->
            canTokenMove(token, diceValue)
        }
    }

    /**
     * Check if a specific token can move with the given dice value.
     *
     * @param token The token to check
     * @param diceValue The dice value
     * @return True if the token can legally move
     */
    fun canTokenMove(token: Token, diceValue: Int): Boolean {
        return when (token.state) {
            TokenState.HOME -> diceValue == 6
            TokenState.ACTIVE -> {
                val newPosition = token.position + diceValue
                newPosition <= LudoBoard.FINISH_POSITION
            }
            TokenState.FINISHED -> false
        }
    }

    /**
     * Move a token and return the updated game state.
     *
     * @param state Current game state
     * @param tokenId ID of the token to move (0-3)
     * @return Result containing new state and move details, or error
     */
    fun moveToken(state: LudoGameState, tokenId: Int): MoveResult {
        val diceValue = state.diceValue
            ?: return MoveResult.Error("No dice value. Roll the dice first.")

        if (!state.mustSelectToken) {
            return MoveResult.Error("Cannot move token in current state.")
        }

        val currentPlayer = state.currentPlayer
        val token = currentPlayer.getToken(tokenId)
            ?: return MoveResult.Error("Token not found.")

        if (!canTokenMove(token, diceValue)) {
            return MoveResult.Error("This token cannot move with dice value $diceValue.")
        }

        // Calculate new position and state
        val moveDetails = calculateMove(token, diceValue, currentPlayer.color)

        // Update the token
        val updatedToken = Token(
            id = tokenId,
            state = moveDetails.newTokenState,
            position = moveDetails.newPosition
        )

        var updatedPlayer = currentPlayer.updateToken(tokenId, updatedToken)
        var updatedPlayers = state.players.map {
            if (it.id == currentPlayer.id) updatedPlayer else it
        }

        // Handle capture
        var capturedInfo: CapturedTokenInfo? = null
        if (moveDetails.canCapture) {
            val captureResult = checkAndPerformCapture(
                state.copy(players = updatedPlayers),
                currentPlayer.id,
                moveDetails.absolutePosition
            )
            if (captureResult != null) {
                updatedPlayers = captureResult.first
                capturedInfo = captureResult.second
            }
        }

        // Create move record
        val move = LudoMove(
            playerId = currentPlayer.id,
            tokenId = tokenId,
            diceValue = diceValue,
            fromPosition = token.position,
            toPosition = moveDetails.newPosition,
            moveType = moveDetails.moveType,
            capturedTokenInfo = capturedInfo
        )

        // Check for winner
        val playerAfterMove = updatedPlayers.find { it.id == currentPlayer.id }!!
        val hasWon = playerAfterMove.hasWon()

        // Determine if player gets another turn
        // Bonus turn for: rolling 6, capturing opponent, or getting a token home
        val bonusTurn = !hasWon && (diceValue == 6 || capturedInfo != null || moveDetails.moveType == MoveType.FINISH)

        // Build new state
        var newState = state.copy(
            players = updatedPlayers,
            lastMove = move,
            moveHistory = state.moveHistory + move,
            diceValue = null,
            mustSelectToken = false
        )

        if (hasWon) {
            newState = newState.copy(
                winnerId = currentPlayer.id,
                gameStatus = GameStatus.FINISHED,
                canRollDice = false
            )
        } else if (bonusTurn) {
            // Player gets another turn
            newState = newState.copy(
                canRollDice = true,
                consecutiveSixes = if (diceValue == 6) state.consecutiveSixes else 0
            )
        } else {
            // Advance to next player
            newState = advanceToNextPlayer(newState)
        }

        return MoveResult.Success(
            newState = newState,
            move = move,
            bonusTurn = bonusTurn,
            hasWon = hasWon
        )
    }

    /**
     * Calculate the details of a token move.
     *
     * Movement Rules:
     * 1. HOME → RING: Only with dice=6, spawn at startIndexByColor[color] (relative position 0)
     * 2. RING movement: Move forward along ring, wrapping around
     * 3. RING → LANE: At laneEntryIndexByColor[color], enter finish lane instead of continuing
     * 4. LANE movement: Move forward in lane (positions 51-56)
     * 5. LANE → FINISH: Exact roll to position 57 required
     */
    private fun calculateMove(token: Token, diceValue: Int, color: LudoColor): MoveDetails {
        return when (token.state) {
            TokenState.HOME -> {
                // Moving out of home - token spawns at color's start position on ring
                // Relative position 0 maps to absolute START_INDEX_BY_COLOR[color]
                val absolutePos = LudoBoard.toAbsolutePosition(0, color)
                MoveDetails(
                    newPosition = 0,
                    newTokenState = TokenState.ACTIVE,
                    moveType = MoveType.EXIT_HOME,
                    absolutePosition = absolutePos,
                    canCapture = !LudoBoard.isSafeCell(absolutePos)
                )
            }
            TokenState.ACTIVE -> {
                val newPosition = token.position + diceValue

                when {
                    // Check for exact finish
                    newPosition == LudoBoard.FINISH_POSITION -> {
                        MoveDetails(
                            newPosition = LudoBoard.FINISH_POSITION,
                            newTokenState = TokenState.FINISHED,
                            moveType = MoveType.FINISH,
                            absolutePosition = -1,
                            canCapture = false
                        )
                    }
                    // Overshoot finish - shouldn't happen if canTokenMove is correct
                    newPosition > LudoBoard.FINISH_POSITION -> {
                        MoveDetails(
                            newPosition = token.position,
                            newTokenState = TokenState.ACTIVE,
                            moveType = MoveType.SKIP,
                            absolutePosition = -1,
                            canCapture = false
                        )
                    }
                    // Already in lane or entering lane
                    newPosition > LudoBoard.RING_END -> {
                        // Moving within or into finish lane (positions 51-56)
                        val enteringLane = token.position <= LudoBoard.RING_END
                        MoveDetails(
                            newPosition = newPosition,
                            newTokenState = TokenState.ACTIVE,
                            moveType = if (enteringLane) MoveType.ENTER_HOME_STRETCH else MoveType.NORMAL,
                            absolutePosition = -1, // No capture possible in lane
                            canCapture = false
                        )
                    }
                    else -> {
                        // Normal move on main ring (positions 0-50)
                        val absolutePos = LudoBoard.toAbsolutePosition(newPosition, color)
                        MoveDetails(
                            newPosition = newPosition,
                            newTokenState = TokenState.ACTIVE,
                            moveType = MoveType.NORMAL,
                            absolutePosition = absolutePos,
                            canCapture = !LudoBoard.isSafeCell(absolutePos)
                        )
                    }
                }
            }
            TokenState.FINISHED -> {
                // Should not happen - finished tokens can't move
                MoveDetails(
                    newPosition = token.position,
                    newTokenState = TokenState.FINISHED,
                    moveType = MoveType.SKIP,
                    absolutePosition = -1,
                    canCapture = false
                )
            }
        }
    }

    /**
     * Check for and perform a capture at the given absolute position.
     *
     * @param state Current game state
     * @param movingPlayerId ID of the player who just moved
     * @param absolutePosition Absolute position on the board
     * @return Pair of updated players list and capture info, or null if no capture
     */
    private fun checkAndPerformCapture(
        state: LudoGameState,
        movingPlayerId: String,
        absolutePosition: Int
    ): Pair<List<LudoPlayer>, CapturedTokenInfo>? {
        if (absolutePosition < 0) return null
        if (LudoBoard.isSafeCell(absolutePosition)) return null

        for (player in state.players) {
            if (player.id == movingPlayerId) continue

            for (token in player.tokens) {
                if (token.state != TokenState.ACTIVE) continue
                if (token.position > LudoBoard.RING_END) continue // In finish lane

                val tokenAbsolutePos = LudoBoard.toAbsolutePosition(token.position, player.color)
                if (tokenAbsolutePos == absolutePosition) {
                    // Found a token to capture - send it back to home
                    val capturedToken = Token(
                        id = token.id,
                        state = TokenState.HOME,
                        position = LudoBoard.HOME_POSITION
                    )

                    val updatedPlayer = player.updateToken(token.id, capturedToken)
                    val updatedPlayers = state.players.map {
                        if (it.id == player.id) updatedPlayer else it
                    }

                    val captureInfo = CapturedTokenInfo(
                        playerId = player.id,
                        tokenId = token.id,
                        position = token.position
                    )

                    return Pair(updatedPlayers, captureInfo)
                }
            }
        }

        return null
    }

    /**
     * Advance the turn to the next player.
     */
    fun advanceToNextPlayer(state: LudoGameState): LudoGameState {
        val nextPlayer = state.getNextPlayer()
        return state.copy(
            currentTurnPlayerId = nextPlayer.id,
            diceValue = null,
            canRollDice = true,
            mustSelectToken = false,
            consecutiveSixes = 0
        )
    }

    /**
     * Start a new game with the given players.
     */
    fun startGame(players: List<LudoPlayer>): LudoGameState {
        require(players.size in 2..4) { "Ludo requires 2-4 players" }

        return LudoGameState(
            players = players,
            currentTurnPlayerId = players.first().id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true
        )
    }

    /**
     * Create a new offline game against bots.
     */
    fun createOfflineGame(humanName: String, botCount: Int): LudoGameState {
        require(botCount in 1..3) { "Bot count must be between 1 and 3" }

        val colors = LudoColor.forPlayerCount(botCount + 1)
        val humanPlayer = LudoPlayer.human(humanName, colors[0])
        val botPlayers = (1..botCount).map { index ->
            LudoPlayer.bot("Bot $index", colors[index])
        }

        return startGame(listOf(humanPlayer) + botPlayers)
    }

    /**
     * Validate that a move is legal.
     */
    fun validateMove(state: LudoGameState, playerId: String, tokenId: Int): ValidationResult {
        if (state.gameStatus != GameStatus.IN_PROGRESS) {
            return ValidationResult.Invalid("Game is not in progress.")
        }

        if (state.currentTurnPlayerId != playerId) {
            return ValidationResult.Invalid("It's not your turn.")
        }

        if (!state.mustSelectToken) {
            return ValidationResult.Invalid("Roll the dice first.")
        }

        val diceValue = state.diceValue
            ?: return ValidationResult.Invalid("No dice value.")

        val player = state.getPlayer(playerId)
            ?: return ValidationResult.Invalid("Player not found.")

        val token = player.getToken(tokenId)
            ?: return ValidationResult.Invalid("Token not found.")

        if (!canTokenMove(token, diceValue)) {
            return ValidationResult.Invalid("This token cannot move.")
        }

        return ValidationResult.Valid
    }
}

/**
 * Internal class for move calculation details.
 */
private data class MoveDetails(
    val newPosition: Int,
    val newTokenState: TokenState,
    val moveType: MoveType,
    val absolutePosition: Int,
    val canCapture: Boolean
)

/**
 * Result of a move operation.
 */
sealed class MoveResult {
    data class Success(
        val newState: LudoGameState,
        val move: LudoMove,
        val bonusTurn: Boolean,
        val hasWon: Boolean
    ) : MoveResult()

    data class Error(val message: String) : MoveResult()
}

/**
 * Result of move validation.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}
