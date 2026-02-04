package com.parthipan.colorclashcards.ui.ludo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.game.ludo.engine.LudoBotAgent
import com.parthipan.colorclashcards.game.ludo.engine.LudoEngine
import com.parthipan.colorclashcards.game.ludo.engine.MoveResult
import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.ludo.model.LudoGameState
import com.parthipan.colorclashcards.game.ludo.model.LudoMove
import com.parthipan.colorclashcards.game.ludo.model.MoveType
import com.parthipan.colorclashcards.game.ludo.model.TokenState
import com.parthipan.colorclashcards.util.SystemTimeProvider
import com.parthipan.colorclashcards.util.TimeProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for offline Ludo game.
 */
data class LudoOfflineUiState(
    val gameState: LudoGameState? = null,
    val humanPlayerId: String = "",
    val isHumanTurn: Boolean = false,
    val isRolling: Boolean = false,
    val canRoll: Boolean = false,
    val mustSelectToken: Boolean = false,
    val diceValue: Int? = null,
    val movableTokenIds: List<Int> = emptyList(),
    val message: String? = null,
    val showWinDialog: Boolean = false,
    val winnerName: String? = null,
    val rankings: List<Pair<String, String>>? = null,
    val difficulty: String = "normal",
    // Turn timer state
    val turnStartedAt: Long = 0L,
    val timerRemainingSeconds: Int = TURN_TIMER_SECONDS,
    val timerProgress: Float = 1f,
    val showTimer: Boolean = false,
    val isTimerWarning: Boolean = false,
    // Token interaction state
    val selectedTokenId: Int? = null,
    val previewPath: List<BoardPosition> = emptyList(),
    val isTokenAnimating: Boolean = false,
    val animatingTokenId: Int? = null
) {
    companion object {
        const val TURN_TIMER_SECONDS = 30
        const val WARNING_THRESHOLD_SECONDS = 10
    }
}

/**
 * ViewModel for offline Ludo game using local game engine.
 *
 * @param timeProvider Injectable time provider for testability
 */
class LudoOfflineViewModel(
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LudoOfflineUiState())
    val uiState: StateFlow<LudoOfflineUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var botJob: Job? = null

    /**
     * Initialize a new offline game.
     */
    fun initializeGame(botCount: Int, difficulty: String) {
        stopAllJobs()

        val gameState = LudoEngine.createOfflineGame("You", botCount)
        val humanPlayerId = gameState.players.first { !it.isBot }.id
        val now = timeProvider.currentTimeMillis()

        _uiState.value = LudoOfflineUiState(
            gameState = gameState,
            humanPlayerId = humanPlayerId,
            isHumanTurn = true,
            canRoll = true,
            difficulty = difficulty,
            turnStartedAt = now,
            timerRemainingSeconds = LudoOfflineUiState.TURN_TIMER_SECONDS,
            timerProgress = 1f,
            showTimer = true,
            isTimerWarning = false
        )

        startTurnTimer()
    }

    /**
     * Start the turn timer countdown.
     */
    private fun startTurnTimer() {
        stopTurnTimer()

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Tick every second

                val currentState = _uiState.value
                if (!currentState.isHumanTurn || currentState.gameState?.isGameOver == true) {
                    break
                }

                val elapsed = timeProvider.currentTimeMillis() - currentState.turnStartedAt
                val remainingMs = (LudoOfflineUiState.TURN_TIMER_SECONDS * 1000L) - elapsed
                val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)
                val progress = (remainingMs.toFloat() / (LudoOfflineUiState.TURN_TIMER_SECONDS * 1000f)).coerceIn(0f, 1f)
                val isWarning = remainingSeconds <= LudoOfflineUiState.WARNING_THRESHOLD_SECONDS

                _uiState.value = currentState.copy(
                    timerRemainingSeconds = remainingSeconds,
                    timerProgress = progress,
                    isTimerWarning = isWarning
                )

                // Auto-skip when timer reaches 0
                if (remainingSeconds <= 0) {
                    autoSkipTurn()
                    break
                }
            }
        }
    }

    /**
     * Stop the turn timer.
     */
    private fun stopTurnTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Stop the bot coroutine.
     */
    private fun stopBotJob() {
        botJob?.cancel()
        botJob = null
    }

    /**
     * Stop all active jobs (timer + bot). Called on game end.
     */
    private fun stopAllJobs() {
        stopTurnTimer()
        stopBotJob()
    }

    /**
     * Auto-skip the turn when timer expires.
     */
    private fun autoSkipTurn() {
        val currentState = _uiState.value
        val gameState = currentState.gameState ?: return

        if (!currentState.isHumanTurn || gameState.isGameOver) return
        if (gameState.currentPlayer.hasWon()) return

        // Advance to next player
        val newState = LudoEngine.advanceToNextPlayer(gameState)

        _uiState.value = currentState.copy(
            gameState = newState,
            diceValue = null,
            mustSelectToken = false,
            movableTokenIds = emptyList(),
            canRoll = false,
            message = "Time's up! Turn skipped.",
            isHumanTurn = newState.currentTurnPlayerId == currentState.humanPlayerId,
            showTimer = false
        )

        // Trigger bot turn if applicable
        if (newState.currentPlayer.isBot && !newState.isGameOver) {
            botJob = viewModelScope.launch {
                delay(1000)
                processBotTurn()
            }
        }
    }

    /**
     * Reset timer for a new turn.
     */
    private fun resetTurnTimer() {
        val now = timeProvider.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            turnStartedAt = now,
            timerRemainingSeconds = LudoOfflineUiState.TURN_TIMER_SECONDS,
            timerProgress = 1f,
            showTimer = true,
            isTimerWarning = false
        )
        startTurnTimer()
    }

    /**
     * Called when app resumes from background. Recomputes remaining time.
     */
    fun onResume() {
        val currentState = _uiState.value
        if (!currentState.isHumanTurn || currentState.gameState?.isGameOver == true) return

        val elapsed = timeProvider.currentTimeMillis() - currentState.turnStartedAt
        val remainingMs = (LudoOfflineUiState.TURN_TIMER_SECONDS * 1000L) - elapsed
        val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)

        if (remainingSeconds <= 0) {
            autoSkipTurn()
        } else {
            val progress = (remainingMs.toFloat() / (LudoOfflineUiState.TURN_TIMER_SECONDS * 1000f)).coerceIn(0f, 1f)
            val isWarning = remainingSeconds <= LudoOfflineUiState.WARNING_THRESHOLD_SECONDS

            _uiState.value = currentState.copy(
                timerRemainingSeconds = remainingSeconds,
                timerProgress = progress,
                isTimerWarning = isWarning
            )
            startTurnTimer()
        }
    }

    /**
     * Roll the dice (human player action).
     */
    fun rollDice() {
        val currentState = _uiState.value
        if (!currentState.canRoll || currentState.isRolling) return
        if (!currentState.isHumanTurn) return
        val gameState = currentState.gameState ?: return
        if (gameState.isGameOver) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isRolling = true,
                message = "Rolling..."
            )

            // Simulate rolling animation delay
            delay(500)

            val diceValue = LudoBotAgent.rollDice()
            processRoll(diceValue)
        }
    }

    /**
     * Process a dice roll result.
     */
    private fun processRoll(diceValue: Int) {
        val currentState = _uiState.value
        val gameState = currentState.gameState ?: return

        val newState = LudoEngine.rollDice(gameState, diceValue)
        val currentPlayer = gameState.currentPlayer

        // Get movable tokens
        val movableTokens = LudoEngine.getMovableTokens(currentPlayer, diceValue)
        val movableIds = movableTokens.map { it.id }

        val message = when {
            newState.consecutiveSixes >= 3 -> "Three 6s! Turn skipped."
            movableTokens.isEmpty() -> "No valid moves. Turn passed."
            diceValue == 6 && movableTokens.isNotEmpty() -> "Rolled 6! Move a token."
            else -> "Rolled $diceValue"
        }

        _uiState.value = currentState.copy(
            gameState = newState,
            isRolling = false,
            diceValue = diceValue,
            canRoll = newState.canRollDice && !newState.mustSelectToken,
            mustSelectToken = newState.mustSelectToken,
            movableTokenIds = if (newState.mustSelectToken) movableIds else emptyList(),
            message = message,
            isHumanTurn = newState.currentTurnPlayerId == currentState.humanPlayerId
        )

        // Check if turn was skipped (no moves available or three 6s)
        if (!newState.mustSelectToken && newState.currentTurnPlayerId != currentState.humanPlayerId) {
            // Turn passed to bot, stop timer and trigger bot turn
            stopTurnTimer()
            _uiState.value = _uiState.value.copy(showTimer = false)
            botJob = viewModelScope.launch {
                delay(1000)
                processBotTurn()
            }
        }

        // Auto-select if only one token can move
        if (newState.mustSelectToken && movableIds.size == 1 &&
            newState.currentTurnPlayerId == currentState.humanPlayerId) {
            viewModelScope.launch {
                delay(300)
                selectToken(movableIds.first())
            }
        }
    }

    /**
     * Select a token to move (human player action).
     * First click: shows path preview
     * Second click (same token): executes move with animation
     */
    fun selectToken(tokenId: Int) {
        val currentState = _uiState.value
        if (!currentState.mustSelectToken) return
        if (tokenId !in currentState.movableTokenIds) return
        if (!currentState.isHumanTurn) return
        if (currentState.isTokenAnimating) return // Don't allow selection during animation

        val gameState = currentState.gameState ?: return
        val diceValue = currentState.diceValue ?: return

        // If clicking the same token that's already selected, execute the move
        if (currentState.selectedTokenId == tokenId) {
            executeTokenMove(tokenId)
            return
        }

        // First click: show path preview
        val humanPlayer = gameState.players.find { it.id == currentState.humanPlayerId } ?: return
        val token = humanPlayer.tokens.find { it.id == tokenId } ?: return

        // Calculate path preview
        val pathPositions = calculateTokenPath(token, humanPlayer.color, diceValue)

        _uiState.value = currentState.copy(
            selectedTokenId = tokenId,
            previewPath = pathPositions,
            message = "Tap again to move, or select another token"
        )
    }

    /**
     * Calculate the path positions for a token's movement.
     */
    private fun calculateTokenPath(
        token: com.parthipan.colorclashcards.game.ludo.model.Token,
        color: com.parthipan.colorclashcards.game.ludo.model.LudoColor,
        diceValue: Int
    ): List<BoardPosition> {
        if (diceValue <= 0) return emptyList()

        val positions = mutableListOf<BoardPosition>()

        // Handle token leaving home
        if (token.state == TokenState.HOME) {
            if (diceValue == 6) {
                LudoBoardPositions.getGridPosition(0, color)?.let {
                    positions.add(it)
                }
            }
            return positions
        }

        // Calculate each step of the path
        var currentPos = token.position
        for (step in 1..diceValue) {
            val nextPos = currentPos + 1
            if (nextPos > 56) break

            val boardPos = LudoBoardPositions.getGridPosition(nextPos, color)
            boardPos?.let { positions.add(it) }
            currentPos = nextPos
        }

        return positions
    }

    /**
     * Execute token move with animation.
     */
    private fun executeTokenMove(tokenId: Int) {
        val currentState = _uiState.value
        val gameState = currentState.gameState ?: return

        // Start animation
        _uiState.value = currentState.copy(
            isTokenAnimating = true,
            animatingTokenId = tokenId
        )

        viewModelScope.launch {
            // Wait for animation to complete (600ms)
            delay(650)

            // Execute the actual move
            when (val result = LudoEngine.moveToken(gameState, tokenId)) {
                is MoveResult.Success -> {
                    handleMoveSuccess(result)
                }
                is MoveResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        message = result.message,
                        isTokenAnimating = false,
                        animatingTokenId = null,
                        selectedTokenId = null,
                        previewPath = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Clear token selection (e.g., when clicking elsewhere).
     */
    fun clearTokenSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTokenId = null,
            previewPath = emptyList()
        )
    }

    /**
     * Handle a successful move.
     */
    private fun handleMoveSuccess(result: MoveResult.Success) {
        val currentState = _uiState.value
        val move = result.move

        // Handle player finished (Mode B: not full game over yet)
        val message = if (result.playerFinished && !result.hasWon) {
            val rank = result.newState.getPlayerRank(result.move.playerId)
            val finishedPlayer = result.newState.getPlayer(result.move.playerId)
            "${finishedPlayer?.name} finished in position $rank!"
        } else {
            buildMoveMessage(move, result.bonusTurn, result.hasWon)
        }

        val isStillHumanTurn = result.newState.currentTurnPlayerId == currentState.humanPlayerId

        // Build rankings when game is fully over
        val rankings = if (result.hasWon && result.newState.players.size > 2) {
            result.newState.finishOrder.mapIndexed { index, playerId ->
                val name = result.newState.getPlayer(playerId)?.name ?: "Unknown"
                Pair("#${index + 1}", name)
            }
        } else null

        _uiState.value = currentState.copy(
            gameState = result.newState,
            diceValue = null,
            mustSelectToken = false,
            movableTokenIds = emptyList(),
            isRolling = false, // Ensure rolling state is cleared
            canRoll = result.bonusTurn && !result.hasWon,
            message = message,
            isHumanTurn = isStillHumanTurn,
            showWinDialog = result.hasWon,
            winnerName = if (result.hasWon) {
                result.newState.winner?.name
            } else null,
            rankings = rankings,
            showTimer = isStillHumanTurn && !result.hasWon,
            // Clear token interaction state
            selectedTokenId = null,
            previewPath = emptyList(),
            isTokenAnimating = false,
            animatingTokenId = null
        )

        if (result.hasWon) {
            stopAllJobs()
            return
        }

        // If it's still human's turn (bonus turn), reset timer
        if (isStillHumanTurn) {
            resetTurnTimer()
        } else {
            // It's bot's turn, stop timer and process bot
            stopTurnTimer()
            botJob = viewModelScope.launch {
                delay(1000)
                processBotTurn()
            }
        }
    }

    /**
     * Process bot player turns.
     *
     * Turn rules:
     * 1. Bot rolls dice ONCE per turn
     * 2. If valid moves exist, bot makes ONE move
     * 3. If dice == 6 OR capture, bot gets ONE bonus turn (back to step 1)
     * 4. Otherwise, turn ends
     */
    private suspend fun processBotTurn() {
        val currentState = _uiState.value
        var gameState = currentState.gameState ?: return
        val difficulty = currentState.difficulty

        // Process bot turns - each iteration is ONE complete turn (roll + optional move)
        while (gameState.currentPlayer.isBot && !gameState.isGameOver) {
            val bot = gameState.currentPlayer

            // GUARD: Skip finished bots
            if (bot.hasWon()) {
                gameState = LudoEngine.advanceToNextPlayer(gameState)
                _uiState.value = _uiState.value.copy(gameState = gameState)
                continue
            }

            val botId = bot.id

            // STEP 1: Show bot thinking
            _uiState.value = _uiState.value.copy(
                gameState = gameState,
                message = "${bot.name} is thinking...",
                isHumanTurn = false,
                diceValue = null,
                canRoll = false,
                mustSelectToken = false
            )

            delay(LudoBotAgent.getThinkingDelayMs(difficulty))

            // STEP 2: Roll dice ONCE
            val diceValue = LudoBotAgent.rollDice()

            _uiState.value = _uiState.value.copy(
                diceValue = diceValue,
                message = "${bot.name} rolled $diceValue"
            )

            delay(800)

            // STEP 3: Apply dice roll to game state
            val stateAfterRoll = LudoEngine.rollDice(gameState, diceValue)

            // ALWAYS update gameState and uiState after roll
            gameState = stateAfterRoll
            _uiState.value = _uiState.value.copy(gameState = gameState)

            // STEP 4: Check if bot can/must move
            if (!gameState.mustSelectToken) {
                // No valid moves OR three 6s - turn was auto-advanced by engine
                val msg = if (diceValue == 6 && gameState.consecutiveSixes == 0) {
                    "${bot.name} rolled three 6s! Turn skipped."
                } else {
                    "${bot.name} has no valid moves."
                }
                _uiState.value = _uiState.value.copy(
                    message = msg,
                    diceValue = null
                )
                delay(1000)
                // Continue to check next player (might be another bot or human)
                continue
            }

            // STEP 5: Bot has valid moves - choose and execute ONE move
            val movableTokens = LudoEngine.getMovableTokens(bot, diceValue)

            if (movableTokens.isEmpty()) {
                // Safety check - shouldn't happen if mustSelectToken is true
                gameState = LudoEngine.advanceToNextPlayer(gameState)
                _uiState.value = _uiState.value.copy(gameState = gameState, diceValue = null)
                continue
            }

            val tokenId = LudoBotAgent.chooseToken(gameState, movableTokens, difficulty)

            _uiState.value = _uiState.value.copy(
                message = "${bot.name} is moving..."
            )

            delay(500)

            // STEP 6: Execute the move
            when (val result = LudoEngine.moveToken(gameState, tokenId)) {
                is MoveResult.Success -> {
                    gameState = result.newState

                    // Defensive: re-check game over using token states (2-player mode)
                    if (!result.hasWon && gameState.players.any { it.hasWon() } && gameState.players.size <= 2) {
                        val winner = gameState.players.first { it.hasWon() }
                        if (gameState.winnerId == null) {
                            gameState = gameState.copy(
                                winnerId = winner.id,
                                gameStatus = GameStatus.FINISHED,
                                canRollDice = false
                            )
                        }
                        _uiState.value = _uiState.value.copy(
                            gameState = gameState,
                            showWinDialog = true,
                            winnerName = winner.name
                        )
                        stopAllJobs()
                        return
                    }

                    val moveMsg = buildBotMoveMessage(bot.name, result.move, result.bonusTurn)

                    // Build rankings when game is fully over
                    val rankings = if (result.hasWon && gameState.players.size > 2) {
                        gameState.finishOrder.mapIndexed { index, playerId ->
                            val name = gameState.getPlayer(playerId)?.name ?: "Unknown"
                            Pair("#${index + 1}", name)
                        }
                    } else null

                    _uiState.value = _uiState.value.copy(
                        gameState = gameState,
                        diceValue = null,
                        message = moveMsg,
                        showWinDialog = result.hasWon,
                        winnerName = if (result.hasWon) gameState.winner?.name else null,
                        rankings = rankings
                    )

                    // STEP 7: Check for game end
                    if (result.hasWon) {
                        stopAllJobs()
                        return
                    }

                    // Bot finished but game continues (Mode B)
                    if (result.playerFinished) {
                        _uiState.value = _uiState.value.copy(
                            message = "${bot.name} finished all tokens!"
                        )
                        delay(1000)
                        // Don't give bonus turn — advance handled by engine
                        continue
                    }

                    // STEP 8: If bonus turn, delay then continue loop (will roll again)
                    // Otherwise, turn ends and loop will check next player
                    if (result.bonusTurn) {
                        _uiState.value = _uiState.value.copy(
                            message = "${bot.name} gets another turn!"
                        )
                        delay(1000)
                        // Loop continues - next iteration will be this bot's bonus turn
                    } else {
                        delay(500)
                        // Turn ended - next iteration checks if next player is bot
                    }
                }
                is MoveResult.Error -> {
                    // Shouldn't happen, but handle gracefully by ending turn
                    gameState = LudoEngine.advanceToNextPlayer(gameState)
                    _uiState.value = _uiState.value.copy(gameState = gameState, diceValue = null)
                }
            }
        }

        // Loop exited - it's now human's turn (or game ended)
        if (!gameState.isGameOver) {
            val isHumanTurn = gameState.currentTurnPlayerId == currentState.humanPlayerId
            _uiState.value = _uiState.value.copy(
                gameState = gameState,
                isHumanTurn = isHumanTurn,
                canRoll = gameState.canRollDice,
                mustSelectToken = false,
                movableTokenIds = emptyList(),
                diceValue = null,
                message = "Your turn! Tap dice to roll.",
                showTimer = isHumanTurn
            )

            if (isHumanTurn) {
                resetTurnTimer()
            }
        }
    }

    /**
     * Build a message for human player's move.
     */
    private fun buildMoveMessage(move: LudoMove, bonusTurn: Boolean, hasWon: Boolean): String {
        return when {
            hasWon -> "You win!"
            move.moveType == MoveType.EXIT_HOME -> {
                if (bonusTurn) "Token out! Roll again." else "Token moved out of home."
            }
            move.moveType == MoveType.FINISH -> "Token reached home!"
            move.capturedTokenInfo != null -> {
                if (bonusTurn) "Captured! Roll again." else "Captured an opponent's token!"
            }
            bonusTurn -> "Rolled 6! Roll again."
            else -> "Moved to position ${move.toPosition}."
        }
    }

    /**
     * Build a message for bot's move.
     */
    private fun buildBotMoveMessage(botName: String, move: LudoMove, bonusTurn: Boolean): String {
        return when {
            move.moveType == MoveType.EXIT_HOME -> "$botName moved a token out."
            move.moveType == MoveType.FINISH -> "$botName got a token home!"
            move.capturedTokenInfo != null -> "$botName captured a token!"
            bonusTurn -> "$botName rolled 6 and moves again."
            else -> "$botName moved."
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllJobs()
    }
}
