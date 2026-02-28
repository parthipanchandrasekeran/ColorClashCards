package com.parthipan.colorclashcards.ui.snl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.snl.engine.SnlBotAgent
import com.parthipan.colorclashcards.game.snl.engine.SnlEngine
import com.parthipan.colorclashcards.game.snl.engine.RollResult
import com.parthipan.colorclashcards.game.snl.model.*
import com.parthipan.colorclashcards.util.SystemTimeProvider
import com.parthipan.colorclashcards.util.TimeProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for offline Snake & Ladder game.
 */
data class SnlOfflineUiState(
    val gameState: SnlGameState? = null,
    val humanPlayerId: String = "",
    val isHumanTurn: Boolean = false,
    val isRolling: Boolean = false,
    val canRoll: Boolean = false,
    val diceValue: Int? = null,
    val message: String? = null,
    val showWinDialog: Boolean = false,
    val winnerName: String? = null,
    val rankings: List<Pair<String, String>>? = null,
    val difficulty: String = "normal",
    // Turn timer
    val turnStartedAt: Long = 0L,
    val timerRemainingSeconds: Int = TURN_TIMER_SECONDS,
    val timerProgress: Float = 1f,
    val showTimer: Boolean = false,
    val isTimerWarning: Boolean = false,
    // Token animation
    val animatingPlayerId: String? = null,
    val animFromPos: Int = 0,
    val animToPos: Int = 0,
    val animFinalPos: Int = 0,
    val animationProgress: Float = 0f,
    val isAnimating: Boolean = false,
    val lastMoveType: SnlMoveType? = null
) {
    companion object {
        const val TURN_TIMER_SECONDS = 30
        const val WARNING_THRESHOLD_SECONDS = 10
    }
}

/**
 * ViewModel for offline Snake & Ladder game.
 * Simpler than Ludo: no token selection, dice roll fully resolves turn.
 */
class SnlOfflineViewModel(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnlOfflineUiState())
    val uiState: StateFlow<SnlOfflineUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var botJob: Job? = null

    fun initializeGame(botCount: Int, difficulty: String, colorIndex: Int = 0, layout: SnlBoardLayout = SnlBoardLayout.CLASSIC) {
        stopAllJobs()

        val gameState = SnlGameState.createOfflineGame("You", botCount, colorIndex, layout)
        val humanPlayerId = gameState.players.first { !it.isBot }.id
        val now = timeProvider.currentTimeMillis()

        _uiState.value = SnlOfflineUiState(
            gameState = gameState,
            humanPlayerId = humanPlayerId,
            isHumanTurn = true,
            canRoll = true,
            difficulty = difficulty,
            turnStartedAt = now,
            timerRemainingSeconds = SnlOfflineUiState.TURN_TIMER_SECONDS,
            timerProgress = 1f,
            showTimer = true
        )

        startTurnTimer()
    }

    // ==================== TURN TIMER ====================

    private fun startTurnTimer() {
        stopTurnTimer()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _uiState.value
                if (!current.isHumanTurn || current.gameState?.isGameOver == true) break

                val elapsed = timeProvider.currentTimeMillis() - current.turnStartedAt
                val remainingMs = (SnlOfflineUiState.TURN_TIMER_SECONDS * 1000L) - elapsed
                val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)
                val progress = (remainingMs.toFloat() / (SnlOfflineUiState.TURN_TIMER_SECONDS * 1000f)).coerceIn(0f, 1f)

                _uiState.update {
                    it.copy(
                        timerRemainingSeconds = remainingSeconds,
                        timerProgress = progress,
                        isTimerWarning = remainingSeconds <= SnlOfflineUiState.WARNING_THRESHOLD_SECONDS
                    )
                }

                if (remainingSeconds <= 0) {
                    autoSkipTurn()
                    break
                }
            }
        }
    }

    private fun stopTurnTimer() { timerJob?.cancel(); timerJob = null }
    private fun stopBotJob() { botJob?.cancel(); botJob = null }
    private fun stopAllJobs() { stopTurnTimer(); stopBotJob() }

    private fun autoSkipTurn() {
        val current = _uiState.value
        val gameState = current.gameState ?: return
        if (!current.isHumanTurn || gameState.isGameOver) return

        val newState = SnlEngine.advanceToNextPlayer(gameState)
        _uiState.update {
            it.copy(
                gameState = newState,
                diceValue = null,
                canRoll = false,
                message = "Time's up! Turn skipped.",
                isHumanTurn = newState.currentTurnPlayerId == it.humanPlayerId,
                showTimer = false
            )
        }

        if (newState.currentPlayer.isBot && !newState.isGameOver) {
            botJob = viewModelScope.launch {
                delay(1000)
                processBotTurns()
            }
        }
    }

    private fun resetTurnTimer() {
        val now = timeProvider.currentTimeMillis()
        _uiState.update {
            it.copy(
                turnStartedAt = now,
                timerRemainingSeconds = SnlOfflineUiState.TURN_TIMER_SECONDS,
                timerProgress = 1f,
                showTimer = true,
                isTimerWarning = false
            )
        }
        startTurnTimer()
    }

    fun onResume() {
        val current = _uiState.value
        if (!current.isHumanTurn || current.gameState?.isGameOver == true) return

        val elapsed = timeProvider.currentTimeMillis() - current.turnStartedAt
        val remainingMs = (SnlOfflineUiState.TURN_TIMER_SECONDS * 1000L) - elapsed
        val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)

        if (remainingSeconds <= 0) {
            autoSkipTurn()
        } else {
            _uiState.update {
                it.copy(
                    timerRemainingSeconds = remainingSeconds,
                    timerProgress = (remainingMs.toFloat() / (SnlOfflineUiState.TURN_TIMER_SECONDS * 1000f)).coerceIn(0f, 1f),
                    isTimerWarning = remainingSeconds <= SnlOfflineUiState.WARNING_THRESHOLD_SECONDS
                )
            }
            startTurnTimer()
        }
    }

    // ==================== DICE ROLL ====================

    fun rollDice() {
        val current = _uiState.value
        if (!current.canRoll || current.isRolling || !current.isHumanTurn) return
        val gameState = current.gameState ?: return
        if (gameState.isGameOver) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRolling = true, message = "Rolling...") }
            delay(500)

            val diceValue = SnlBotAgent.rollDice()
            processRoll(diceValue)
        }
    }

    private fun processRoll(diceValue: Int) {
        val current = _uiState.value
        val gameState = current.gameState ?: return

        stopTurnTimer()

        val result = SnlEngine.rollDice(gameState, diceValue)
        val move = result.move

        val message = buildHumanMoveMessage(result, diceValue)

        // Animate the move
        if (move != null && move.fromPos != move.finalPos) {
            _uiState.update {
                it.copy(
                    isRolling = false,
                    diceValue = diceValue,
                    canRoll = false,
                    message = "Rolled $diceValue",
                    isAnimating = true,
                    animatingPlayerId = current.humanPlayerId,
                    animFromPos = move.fromPos,
                    animToPos = move.toPos,
                    animFinalPos = move.finalPos,
                    animationProgress = 0f,
                    lastMoveType = move.moveType
                )
            }

            viewModelScope.launch {
                // Animate dice → position
                animateMovement(600L)

                // If snake or ladder, pause then animate to final pos
                if (move.toPos != move.finalPos) {
                    delay(400)
                    _uiState.update {
                        it.copy(
                            animFromPos = move.toPos,
                            animToPos = move.finalPos,
                            animFinalPos = move.finalPos,
                            animationProgress = 0f,
                            message = when (move.moveType) {
                                SnlMoveType.SNAKE_BITE -> "Snake! Slide down to ${move.finalPos}"
                                SnlMoveType.LADDER_CLIMB -> "Ladder! Climb up to ${move.finalPos}"
                                else -> message
                            }
                        )
                    }
                    animateMovement(500L)
                }

                delay(100)
                applyMoveResult(result, message, diceValue)
            }
        } else {
            // No movement (overshoot or three sixes)
            _uiState.update {
                it.copy(
                    gameState = result.newState,
                    isRolling = false,
                    diceValue = diceValue,
                    canRoll = result.bonusTurn && !result.newState.isGameOver,
                    message = message,
                    isHumanTurn = result.newState.currentTurnPlayerId == it.humanPlayerId,
                    isAnimating = false,
                    showTimer = result.newState.currentTurnPlayerId == it.humanPlayerId && !result.newState.isGameOver
                )
            }

            handlePostMove(result)
        }
    }

    private suspend fun animateMovement(durationMs: Long) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / durationMs).coerceAtMost(1f)
            _uiState.update { it.copy(animationProgress = progress) }
            if (progress >= 1f) break
            delay(16)
        }
    }

    private fun applyMoveResult(result: RollResult, message: String, diceValue: Int) {
        val current = _uiState.value
        val isStillHumanTurn = result.newState.currentTurnPlayerId == current.humanPlayerId
        val gameOver = result.newState.isGameOver

        val rankings = if (gameOver && result.newState.players.size > 2) {
            result.newState.finishOrder.mapIndexed { index, playerId ->
                val name = result.newState.getPlayer(playerId)?.name ?: "Unknown"
                Pair("#${index + 1}", name)
            }
        } else null

        _uiState.update {
            it.copy(
                gameState = result.newState,
                diceValue = null,
                canRoll = result.bonusTurn && !gameOver,
                message = message,
                isHumanTurn = isStillHumanTurn,
                showWinDialog = gameOver,
                winnerName = if (gameOver) result.newState.winner?.name else null,
                rankings = rankings,
                showTimer = isStillHumanTurn && !gameOver,
                isAnimating = false,
                animatingPlayerId = null,
                animationProgress = 0f,
                lastMoveType = null
            )
        }

        if (gameOver) {
            stopAllJobs()
            return
        }

        handlePostMove(result)
    }

    private fun handlePostMove(result: RollResult) {
        val current = _uiState.value
        val isStillHumanTurn = result.newState.currentTurnPlayerId == current.humanPlayerId

        if (isStillHumanTurn && !result.newState.isGameOver) {
            resetTurnTimer()
        } else if (!result.newState.isGameOver) {
            stopTurnTimer()
            botJob = viewModelScope.launch {
                delay(1000)
                processBotTurns()
            }
        }
    }

    // ==================== BOT TURNS ====================

    private suspend fun processBotTurns() {
        val current = _uiState.value
        var gameState = current.gameState ?: return
        val difficulty = current.difficulty

        while (gameState.currentPlayer.isBot && !gameState.isGameOver) {
            val bot = gameState.currentPlayer

            if (bot.hasWon) {
                gameState = SnlEngine.advanceToNextPlayer(gameState)
                _uiState.update { it.copy(gameState = gameState) }
                continue
            }

            _uiState.update {
                it.copy(
                    gameState = gameState,
                    message = "${bot.name} is thinking...",
                    isHumanTurn = false,
                    diceValue = null,
                    canRoll = false
                )
            }

            delay(SnlBotAgent.getThinkingDelayMs(difficulty))

            val diceValue = SnlBotAgent.rollDice()
            _uiState.update { it.copy(diceValue = diceValue, message = "${bot.name} rolled $diceValue") }
            delay(800)

            val result = SnlEngine.rollDice(gameState, diceValue)
            val move = result.move

            // Animate bot movement
            if (move != null && move.fromPos != move.finalPos) {
                _uiState.update {
                    it.copy(
                        isAnimating = true,
                        animatingPlayerId = bot.id,
                        animFromPos = move.fromPos,
                        animToPos = move.toPos,
                        animFinalPos = move.finalPos,
                        animationProgress = 0f,
                        lastMoveType = move.moveType
                    )
                }
                animateMovement(500L)

                if (move.toPos != move.finalPos) {
                    delay(300)
                    _uiState.update {
                        it.copy(
                            animFromPos = move.toPos,
                            animToPos = move.finalPos,
                            animFinalPos = move.finalPos,
                            animationProgress = 0f,
                            message = when (move.moveType) {
                                SnlMoveType.SNAKE_BITE -> "${bot.name} hit a snake!"
                                SnlMoveType.LADDER_CLIMB -> "${bot.name} found a ladder!"
                                else -> "${bot.name} moved"
                            }
                        )
                    }
                    animateMovement(400L)
                }
            }

            gameState = result.newState

            val rankings = if (result.newState.isGameOver && gameState.players.size > 2) {
                gameState.finishOrder.mapIndexed { index, playerId ->
                    val name = gameState.getPlayer(playerId)?.name ?: "Unknown"
                    Pair("#${index + 1}", name)
                }
            } else null

            _uiState.update {
                it.copy(
                    gameState = gameState,
                    diceValue = null,
                    message = buildBotMoveMessage(bot.name, result),
                    showWinDialog = result.newState.isGameOver,
                    winnerName = if (result.newState.isGameOver) result.newState.winner?.name else null,
                    rankings = rankings,
                    isAnimating = false,
                    animatingPlayerId = null,
                    animationProgress = 0f,
                    lastMoveType = null
                )
            }

            if (result.newState.isGameOver) {
                stopAllJobs()
                return
            }

            if (result.playerFinished) {
                _uiState.update { it.copy(message = "${bot.name} finished!") }
                delay(1000)
                continue
            }

            if (result.bonusTurn) {
                _uiState.update { it.copy(message = "${bot.name} gets another turn!") }
                delay(1000)
            } else {
                delay(500)
            }
        }

        // Now human's turn
        if (!gameState.isGameOver) {
            _uiState.update {
                it.copy(
                    gameState = gameState,
                    isHumanTurn = gameState.currentTurnPlayerId == it.humanPlayerId,
                    canRoll = gameState.canRollDice,
                    diceValue = null,
                    message = "Your turn! Tap to roll.",
                    showTimer = true
                )
            }
            resetTurnTimer()
        }
    }

    // ==================== MESSAGES ====================

    private fun buildHumanMoveMessage(result: RollResult, diceValue: Int): String {
        val move = result.move ?: return if (result.threeSixes) "Three 6s! Turn lost." else "No valid move."
        return when {
            result.newState.isGameOver && result.newState.winnerId == _uiState.value.humanPlayerId -> "You win!"
            move.moveType == SnlMoveType.WIN -> "You reached 100! You win!"
            move.moveType == SnlMoveType.SNAKE_BITE -> "Snake! Slid from ${move.toPos} to ${move.finalPos}"
            move.moveType == SnlMoveType.LADDER_CLIMB -> "Ladder! Climbed from ${move.toPos} to ${move.finalPos}"
            move.moveType == SnlMoveType.OVERSHOOT -> "Need exact roll to reach 100."
            move.moveType == SnlMoveType.NEED_ONE_TO_START -> "Roll 1 to enter the board."
            result.bonusTurn -> "Rolled 6! Roll again."
            else -> "Moved to ${move.finalPos}"
        }
    }

    private fun buildBotMoveMessage(botName: String, result: RollResult): String {
        val move = result.move ?: return "$botName couldn't move."
        return when (move.moveType) {
            SnlMoveType.WIN -> "$botName reached 100!"
            SnlMoveType.SNAKE_BITE -> "$botName hit a snake! (${move.toPos} → ${move.finalPos})"
            SnlMoveType.LADDER_CLIMB -> "$botName climbed a ladder! (${move.toPos} → ${move.finalPos})"
            SnlMoveType.OVERSHOOT -> "$botName overshot 100."
            SnlMoveType.NEED_ONE_TO_START -> "$botName needs 1 to start."
            else -> "$botName moved to ${move.finalPos}."
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllJobs()
    }
}
