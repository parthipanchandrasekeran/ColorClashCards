package com.parthipan.colorclashcards.game.snl.engine

import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.snl.model.*

/**
 * Core game engine for Snake & Ladder.
 * Pure functions: takes state, returns new state. No side effects.
 *
 * Key simplification vs Ludo: No token selection step.
 * rollDice() fully resolves the turn since each player has exactly 1 token.
 */
object SnlEngine {

    /**
     * Roll the dice and fully resolve the current player's turn.
     *
     * This single function handles:
     * 1. Dice roll
     * 2. Movement (or overshoot)
     * 3. Snake/ladder resolution
     * 4. Win detection
     * 5. Bonus turn (on 6) or advance to next player
     * 6. Three consecutive sixes penalty
     *
     * @param state Current game state
     * @param diceValue The rolled dice value (1-6)
     * @return RollResult containing new state and move details
     */
    fun rollDice(state: SnlGameState, diceValue: Int): RollResult {
        require(diceValue in 1..6) { "Dice value must be 1-6" }
        require(state.canRollDice) { "Cannot roll dice in current state" }

        if (state.isGameOver) return RollResult(state, null, bonusTurn = false)

        val currentPlayer = state.currentPlayer

        // Skip finished players
        if (currentPlayer.hasWon) {
            val advanced = advanceToNextPlayer(state)
            return RollResult(advanced, null, bonusTurn = false)
        }

        // Handle consecutive sixes
        var consecutiveSixes = state.consecutiveSixes
        var skipTurn = false

        if (diceValue == 6) {
            consecutiveSixes++
            if (consecutiveSixes >= SnlGameState.MAX_CONSECUTIVE_SIXES) {
                skipTurn = true
                consecutiveSixes = 0
            }
        } else {
            consecutiveSixes = 0
        }

        // Three sixes — lose turn
        if (skipTurn) {
            val newState = advanceToNextPlayer(state.copy(
                diceValue = diceValue,
                consecutiveSixes = 0
            ))
            val move = SnlMove(
                playerId = currentPlayer.id,
                diceValue = diceValue,
                fromPos = currentPlayer.position,
                toPos = currentPlayer.position,
                finalPos = currentPlayer.position,
                moveType = SnlMoveType.OVERSHOOT
            )
            return RollResult(newState, move, bonusTurn = false, threeSixes = true)
        }

        // Entry rule: token starts off-board (0) and can enter only with a roll of 1.
        if (!currentPlayer.isOnBoard && diceValue != 1) {
            val move = SnlMove(
                playerId = currentPlayer.id,
                diceValue = diceValue,
                fromPos = currentPlayer.position,
                toPos = currentPlayer.position,
                finalPos = currentPlayer.position,
                moveType = SnlMoveType.NEED_ONE_TO_START
            )

            val newState = if (diceValue == 6) {
                // Keep the bonus-turn rule for sixes, even while still off-board.
                state.copy(
                    diceValue = diceValue,
                    lastMove = move,
                    canRollDice = true,
                    consecutiveSixes = consecutiveSixes
                )
            } else {
                advanceToNextPlayer(state.copy(
                    diceValue = diceValue,
                    lastMove = move,
                    consecutiveSixes = 0
                ))
            }

            return RollResult(newState, move, bonusTurn = diceValue == 6)
        }

        // Calculate movement
        val fromPos = currentPlayer.position
        val rawToPos = fromPos + diceValue

        // Overshoot past 100 — stay in place
        if (rawToPos > SnlGameState.WINNING_SQUARE) {
            val move = SnlMove(
                playerId = currentPlayer.id,
                diceValue = diceValue,
                fromPos = fromPos,
                toPos = fromPos,
                finalPos = fromPos,
                moveType = SnlMoveType.OVERSHOOT
            )

            val newState = if (diceValue == 6) {
                // Bonus turn on 6 even if overshoot
                state.copy(
                    diceValue = diceValue,
                    lastMove = move,
                    canRollDice = true,
                    consecutiveSixes = consecutiveSixes
                )
            } else {
                advanceToNextPlayer(state.copy(
                    diceValue = diceValue,
                    lastMove = move,
                    consecutiveSixes = 0
                ))
            }
            return RollResult(newState, move, bonusTurn = diceValue == 6)
        }

        // Exact 100 = win
        if (rawToPos == SnlGameState.WINNING_SQUARE) {
            val move = SnlMove(
                playerId = currentPlayer.id,
                diceValue = diceValue,
                fromPos = fromPos,
                toPos = rawToPos,
                finalPos = rawToPos,
                moveType = SnlMoveType.WIN
            )

            val updatedPlayer = currentPlayer.copy(position = rawToPos)
            val updatedFinishOrder = state.finishOrder + currentPlayer.id

            val activeCount = state.players.count { !it.hasWon && it.id != currentPlayer.id }

            val newState = if (state.players.size <= 2 || activeCount <= 1) {
                // Game over
                val finalOrder = if (activeCount == 1) {
                    val lastPlayer = state.players.find { !it.hasWon && it.id != currentPlayer.id }
                    if (lastPlayer != null) updatedFinishOrder + lastPlayer.id else updatedFinishOrder
                } else updatedFinishOrder

                state.copy(
                    players = state.players.map { if (it.id == currentPlayer.id) updatedPlayer else it },
                    winnerId = currentPlayer.id,
                    gameStatus = GameStatus.FINISHED,
                    canRollDice = false,
                    lastMove = move,
                    diceValue = diceValue,
                    finishOrder = finalOrder
                )
            } else {
                // Player finished but game continues (3-4 players)
                advanceToNextPlayer(state.copy(
                    players = state.players.map { if (it.id == currentPlayer.id) updatedPlayer else it },
                    lastMove = move,
                    diceValue = diceValue,
                    finishOrder = updatedFinishOrder,
                    consecutiveSixes = 0
                ))
            }

            return RollResult(newState, move, bonusTurn = false, playerFinished = true)
        }

        // Normal move — resolve snake/ladder
        val finalPos = SnlBoard.resolveSquare(rawToPos, state.snakes, state.ladders)
        val moveType = when {
            rawToPos in state.snakes -> SnlMoveType.SNAKE_BITE
            rawToPos in state.ladders -> SnlMoveType.LADDER_CLIMB
            else -> SnlMoveType.NORMAL
        }

        val move = SnlMove(
            playerId = currentPlayer.id,
            diceValue = diceValue,
            fromPos = fromPos,
            toPos = rawToPos,
            finalPos = finalPos,
            moveType = moveType
        )

        val updatedPlayer = currentPlayer.copy(position = finalPos)
        val updatedPlayers = state.players.map { if (it.id == currentPlayer.id) updatedPlayer else it }

        if (finalPos == SnlGameState.WINNING_SQUARE) {
            val updatedFinishOrder = state.finishOrder + currentPlayer.id
            val activeCount = state.players.count { !it.hasWon && it.id != currentPlayer.id }

            val newState = if (state.players.size <= 2 || activeCount <= 1) {
                val finalOrder = if (activeCount == 1) {
                    val lastPlayer = state.players.find { !it.hasWon && it.id != currentPlayer.id }
                    if (lastPlayer != null) updatedFinishOrder + lastPlayer.id else updatedFinishOrder
                } else updatedFinishOrder

                state.copy(
                    players = updatedPlayers,
                    winnerId = currentPlayer.id,
                    gameStatus = GameStatus.FINISHED,
                    canRollDice = false,
                    lastMove = move.copy(moveType = SnlMoveType.WIN),
                    diceValue = diceValue,
                    finishOrder = finalOrder
                )
            } else {
                advanceToNextPlayer(state.copy(
                    players = updatedPlayers,
                    diceValue = diceValue,
                    lastMove = move.copy(moveType = SnlMoveType.WIN),
                    finishOrder = updatedFinishOrder,
                    consecutiveSixes = 0
                ))
            }

            return RollResult(newState, move.copy(moveType = SnlMoveType.WIN, finalPos = SnlGameState.WINNING_SQUARE), bonusTurn = false, playerFinished = true)
        }

        // Bonus turn on rolling 6
        val bonusTurn = diceValue == 6

        val newState = if (bonusTurn) {
            state.copy(
                players = updatedPlayers,
                diceValue = diceValue,
                lastMove = move,
                canRollDice = true,
                consecutiveSixes = consecutiveSixes
            )
        } else {
            advanceToNextPlayer(state.copy(
                players = updatedPlayers,
                diceValue = diceValue,
                lastMove = move,
                consecutiveSixes = 0
            ))
        }

        return RollResult(newState, move, bonusTurn = bonusTurn)
    }

    /**
     * Advance to the next player who hasn't finished.
     */
    fun advanceToNextPlayer(state: SnlGameState): SnlGameState {
        val nextPlayer = state.getNextPlayer()
        return state.copy(
            currentTurnPlayerId = nextPlayer.id,
            diceValue = null,
            canRollDice = true,
            consecutiveSixes = 0
        )
    }

    /**
     * Start a new game with the given players.
     */
    fun startGame(
        players: List<SnlPlayer>,
        layout: SnlBoardLayout = SnlBoardLayout.CLASSIC
    ): SnlGameState {
        require(players.size in 2..4) { "SNL requires 2-4 players" }

        val (snakes, ladders) = when (layout) {
            SnlBoardLayout.CLASSIC -> SnlBoard.CLASSIC_SNAKES to SnlBoard.CLASSIC_LADDERS
            SnlBoardLayout.RANDOMIZED -> SnlBoard.generateRandomLayout()
        }

        return SnlGameState(
            players = players,
            currentTurnPlayerId = players.first().id,
            gameStatus = GameStatus.IN_PROGRESS,
            boardLayout = layout,
            snakes = snakes,
            ladders = ladders,
            canRollDice = true
        )
    }
}

/**
 * Result of a dice roll in Snake & Ladder.
 */
data class RollResult(
    val newState: SnlGameState,
    val move: SnlMove?,
    val bonusTurn: Boolean,
    val playerFinished: Boolean = false,
    val threeSixes: Boolean = false
)
