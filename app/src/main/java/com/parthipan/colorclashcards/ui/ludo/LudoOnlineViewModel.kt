package com.parthipan.colorclashcards.ui.ludo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.parthipan.colorclashcards.data.model.*
import com.parthipan.colorclashcards.data.repository.LudoMatchRepository
import com.parthipan.colorclashcards.data.repository.LudoRoomRepository
import com.parthipan.colorclashcards.game.ludo.engine.LudoBotAgent
import com.parthipan.colorclashcards.game.ludo.engine.LudoDebugLogger
import com.parthipan.colorclashcards.game.ludo.engine.LudoEngine
import com.parthipan.colorclashcards.game.ludo.engine.MoveResult
import com.parthipan.colorclashcards.game.ludo.model.*
import com.parthipan.colorclashcards.game.ludo.model.TokenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * UI State for online Ludo game.
 */
data class LudoOnlineUiState(
    val gameState: LudoGameState? = null,
    val room: LudoRoom? = null,
    val isHost: Boolean = false,
    val localPlayerId: String = "",
    val isMyTurn: Boolean = false,
    val isRolling: Boolean = false,
    val canRoll: Boolean = false,
    val mustSelectToken: Boolean = false,
    val diceValue: Int? = null,
    val movableTokenIds: List<Int> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val showWinDialog: Boolean = false,
    val winnerName: String? = null,
    val isLoading: Boolean = true,
    val disconnectedPlayers: Set<String> = emptySet(),
    val afkWarning: Boolean = false,           // Show warning when player is AFK
    val afkCountdown: Int? = null,             // Seconds until auto-skip
    val gameEnded: Boolean = false,
    val endReason: String? = null,
    // Turn timer state (uses server timestamp for sync)
    val timerRemainingSeconds: Int = TURN_TIMER_SECONDS,
    val timerProgress: Float = 1f,
    val showTimer: Boolean = false,
    val isTimerWarning: Boolean = false,
    // Token interaction state
    val selectedTokenId: Int? = null,
    val previewPath: List<BoardPosition> = emptyList(),
    val isTokenAnimating: Boolean = false,
    val animatingTokenId: Int? = null,
    val animationProgress: Float = 0f
) {
    companion object {
        const val TURN_TIMER_SECONDS = 30
        const val WARNING_THRESHOLD_SECONDS = 10
    }
}

/**
 * ViewModel for online Ludo game with Firebase synchronization.
 * Handles:
 * - Real-time game state sync
 * - Player disconnect detection
 * - 60-second rejoin window
 * - 30-second AFK auto-skip
 * - Clean game ending on player dropout
 */
class LudoOnlineViewModel : ViewModel() {

    private val roomRepository = LudoRoomRepository()
    private val matchRepository = LudoMatchRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(LudoOnlineUiState())
    val uiState: StateFlow<LudoOnlineUiState> = _uiState.asStateFlow()

    private var roomId: String = ""
    private var localGameState: LudoGameState? = null
    private var lastTurnStartedAt: Timestamp? = null

    private var observeJob: Job? = null
    private var hostJob: Job? = null
    private var presenceJob: Job? = null
    private var afkJob: Job? = null
    private var heartbeatJob: Job? = null

    private val localPlayerId: String
        get() = auth.currentUser?.uid ?: ""

    /**
     * Initialize the online game.
     */
    fun initialize(roomId: String, isHost: Boolean) {
        this.roomId = roomId

        _uiState.value = _uiState.value.copy(
            isHost = isHost,
            localPlayerId = localPlayerId,
            isLoading = true
        )

        if (isHost) {
            initializeMatchAsHost()
        } else {
            observeMatch()
        }

        startHeartbeat()
    }

    /**
     * Initialize match as host.
     */
    private fun initializeMatchAsHost() {
        viewModelScope.launch {
            val roomResult = roomRepository.getRoom(roomId)
            val room = roomResult.getOrNull()

            if (room == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Room not found"
                )
                return@launch
            }

            // Create players from room - use stored color, keyed by player color (not index)
            val players = room.players.map { roomPlayer ->
                val playerColor = try {
                    LudoColor.valueOf(roomPlayer.color)
                } catch (e: Exception) {
                    // Color should always be valid since it's assigned from LudoColor.entries
                    // If we get here, something is seriously wrong with the data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Invalid player color: ${roomPlayer.color}"
                    )
                    return@launch
                }
                LudoPlayer(
                    id = roomPlayer.odId,
                    name = roomPlayer.odisplayName,
                    color = playerColor,
                    isBot = false,
                    isOnline = true
                )
            }

            // Start game
            val gameState = LudoEngine.startGame(players)
            localGameState = gameState

            // Write to Firestore
            val initResult = matchRepository.initializeMatch(roomId, gameState)
            if (initResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to start match: ${initResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            // Start observing and processing
            observeMatch()
            startHostProcessing()
        }
    }

    /**
     * Observe match state and presence.
     */
    private fun observeMatch() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                matchRepository.observeMatchState(roomId).distinctUntilChanged(),
                matchRepository.observePresence(roomId).distinctUntilChanged(),
                roomRepository.observeRoom(roomId).distinctUntilChanged()
            ) { matchState, presenceList, room ->
                Triple(matchState, presenceList, room)
            }.collect { (matchState, presenceList, room) ->
                handleStateUpdate(matchState, presenceList, room)
            }
        }
    }

    /**
     * Handle state updates from Firestore.
     */
    private fun handleStateUpdate(
        matchState: LudoMatchState?,
        presenceList: List<LudoPlayerPresence>,
        room: LudoRoom?
    ) {
        if (matchState == null) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            return
        }

        val gameState = matchState.toGameState()
        localGameState = gameState
        lastTurnStartedAt = matchState.turnStartedAt

        val isMyTurn = gameState.currentTurnPlayerId == localPlayerId
        val currentPlayer = gameState.currentPlayer

        // Track disconnected players
        val disconnected = presenceList
            .filter { !it.isOnline }
            .map { it.playerId }
            .toSet()

        // Check if game should end due to dropouts
        val droppedPlayers = presenceList.filter { presence ->
            !presence.isOnline && !matchRepository.canRejoin(presence.disconnectedAt)
        }.map { it.playerId }

        val activePlayers = gameState.players.filter { it.id !in droppedPlayers && !it.isBot }

        // Calculate movable tokens if it's my turn and must select a token
        // Use matchState.diceValue directly for consistency with displayed dice
        val diceVal = matchState.diceValue
        val movableIds = if (isMyTurn && gameState.mustSelectToken && diceVal != null) {
            LudoEngine.getMovableTokens(currentPlayer, diceVal).map { it.id }
        } else emptyList()

        // Don't overwrite dice value with null — keep last visible value until new turn starts
        val previousTurnPlayer = _uiState.value.gameState?.currentTurnPlayerId
        val turnChanged = previousTurnPlayer != null && previousTurnPlayer != gameState.currentTurnPlayerId
        val newDiceValue = when {
            matchState.diceValue != null -> matchState.diceValue  // New dice value from Firestore
            turnChanged -> null  // Turn changed, clear dice
            else -> _uiState.value.diceValue  // Keep previous dice value visible
        }

        // For non-host: keep isRolling until Firestore delivers the actual dice value
        val newIsRolling = when {
            !isMyTurn -> false
            _uiState.value.isRolling && matchState.diceValue != null -> false  // Got dice value, stop rolling
            else -> _uiState.value.isRolling  // Preserve rolling state
        }

        _uiState.value = _uiState.value.copy(
            gameState = gameState,
            room = room,
            isLoading = false,
            isRolling = newIsRolling,
            isMyTurn = isMyTurn,
            diceValue = newDiceValue,
            canRoll = isMyTurn && gameState.canRollDice,
            mustSelectToken = isMyTurn && gameState.mustSelectToken,
            movableTokenIds = movableIds,
            disconnectedPlayers = disconnected,
            showWinDialog = gameState.winnerId != null,
            winnerName = gameState.winner?.name,
            gameEnded = gameState.gameStatus == GameStatus.FINISHED
        )

        // Auto-move if only one token can move (skip "Tap again" confirmation)
        if (isMyTurn && gameState.mustSelectToken && movableIds.size == 1 &&
            _uiState.value.selectedTokenId == null && !_uiState.value.isTokenAnimating) {
            viewModelScope.launch {
                delay(300)
                executeOnlineTokenMove(movableIds.first())
            }
        }

        // Start AFK monitoring if it's my turn
        if (isMyTurn && !gameState.isGameOver) {
            startAfkMonitor(matchState.turnStartedAt)
        } else {
            stopAfkMonitor()
        }

        // Host: check for player dropouts
        if (_uiState.value.isHost && activePlayers.size < 2 && !gameState.isGameOver) {
            handleInsufficientPlayers(activePlayers.firstOrNull()?.id)
        }
    }

    /**
     * Start host processing loop for actions.
     */
    private fun startHostProcessing() {
        hostJob?.cancel()
        hostJob = viewModelScope.launch {
            matchRepository.observeActions(roomId).collect { actions ->
                for (action in actions) {
                    processAction(action)
                }
            }
        }

        // Also monitor for AFK players and auto-skip
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                checkAfkAndDisconnects()
            }
        }
    }

    /**
     * Process a player action (host only).
     */
    private suspend fun processAction(action: LudoPlayerAction) {
        val gameState = localGameState ?: return

        // Verify it's this player's turn
        if (gameState.currentTurnPlayerId != action.playerId) {
            matchRepository.deleteAction(roomId, action.id)
            return
        }

        when (action.type) {
            LudoActionType.ROLL_DICE.name -> {
                if (gameState.canRollDice) {
                    val diceValue = LudoBotAgent.rollDice()
                    val newState = LudoEngine.rollDice(gameState, diceValue)
                    localGameState = newState
                    _uiState.value = _uiState.value.copy(
                        gameState = newState,
                        diceValue = diceValue
                    )
                    matchRepository.updateMatchState(roomId, newState)
                }
            }

            LudoActionType.MOVE_TOKEN.name -> {
                val tokenId = action.tokenId ?: return
                if (gameState.mustSelectToken) {
                    when (val result = LudoEngine.moveToken(gameState, tokenId)) {
                        is MoveResult.Success -> {
                            localGameState = result.newState
                            _uiState.value = _uiState.value.copy(gameState = result.newState)
                            matchRepository.updateMatchState(roomId, result.newState)

                            if (result.hasWon) {
                                matchRepository.endMatch(roomId, result.newState.winnerId!!)
                            }
                        }
                        is MoveResult.Error -> {
                            // Invalid move, ignore
                        }
                    }
                }
            }

            LudoActionType.HEARTBEAT.name -> {
                // Just a keep-alive, no action needed
            }
        }

        matchRepository.deleteAction(roomId, action.id)
    }

    /**
     * Check for AFK players and disconnects (host only).
     */
    private suspend fun checkAfkAndDisconnects() {
        if (!_uiState.value.isHost) return

        val gameState = localGameState ?: return
        if (gameState.isGameOver) return

        // Use cached timestamp instead of reading Firestore
        val turnStartedAt = lastTurnStartedAt ?: return

        // Check AFK timeout
        if (matchRepository.isPlayerAfk(turnStartedAt)) {
            val currentPlayer = gameState.currentPlayer

            // Skip turn for AFK player
            if (!currentPlayer.isBot) {
                val newState = LudoEngine.advanceToNextPlayer(gameState)
                localGameState = newState
                matchRepository.updateMatchState(roomId, newState)

                _uiState.value = _uiState.value.copy(
                    message = "${currentPlayer.name}'s turn was skipped (AFK)"
                )
            }
        }
    }

    /**
     * Handle insufficient players (end game).
     */
    private fun handleInsufficientPlayers(remainingPlayerId: String?) {
        viewModelScope.launch {
            matchRepository.endMatchDueToDropout(roomId, remainingPlayerId)

            _uiState.value = _uiState.value.copy(
                gameEnded = true,
                endReason = if (remainingPlayerId != null) {
                    "Other players left. You win!"
                } else {
                    "All players disconnected"
                }
            )
        }
    }

    /**
     * Start heartbeat to maintain presence.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                matchRepository.updatePresence(roomId)
                delay(10_000) // Every 10 seconds
            }
        }
    }

    /**
     * Start AFK monitor and turn timer for local player.
     */
    private fun startAfkMonitor(turnStartedAt: Timestamp?) {
        afkJob?.cancel()

        if (turnStartedAt == null) return

        afkJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - turnStartedAt.toDate().time
                val remainingMs = LudoMatchState.AFK_TIMEOUT_MS - elapsed
                val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)
                val timerProgress = (remainingMs.toFloat() / LudoMatchState.AFK_TIMEOUT_MS).coerceIn(0f, 1f)
                val isWarning = remainingSeconds <= LudoOnlineUiState.WARNING_THRESHOLD_SECONDS

                if (remainingSeconds <= 10 && remainingSeconds > 0) {
                    _uiState.value = _uiState.value.copy(
                        afkWarning = true,
                        afkCountdown = remainingSeconds,
                        timerRemainingSeconds = remainingSeconds,
                        timerProgress = timerProgress,
                        showTimer = true,
                        isTimerWarning = isWarning
                    )
                } else if (remainingSeconds <= 0) {
                    _uiState.value = _uiState.value.copy(
                        afkWarning = false,
                        afkCountdown = null,
                        timerRemainingSeconds = 0,
                        timerProgress = 0f,
                        showTimer = false
                    )
                    break
                } else {
                    _uiState.value = _uiState.value.copy(
                        afkWarning = false,
                        afkCountdown = null,
                        timerRemainingSeconds = remainingSeconds,
                        timerProgress = timerProgress,
                        showTimer = true,
                        isTimerWarning = isWarning
                    )
                }

                delay(1000)
            }
        }
    }

    /**
     * Stop AFK monitor and turn timer.
     */
    private fun stopAfkMonitor() {
        afkJob?.cancel()
        _uiState.value = _uiState.value.copy(
            afkWarning = false,
            afkCountdown = null,
            showTimer = false,
            timerRemainingSeconds = LudoOnlineUiState.TURN_TIMER_SECONDS,
            timerProgress = 1f,
            isTimerWarning = false
        )
    }

    /**
     * Roll dice (player action).
     */
    fun rollDice() {
        val state = _uiState.value
        if (!state.canRoll || state.isRolling) return

        _uiState.value = state.copy(isRolling = true)

        viewModelScope.launch {
            // If host, process locally
            if (state.isHost) {
                // Simulate rolling animation delay (matching offline)
                delay(500)

                val gameState = localGameState ?: return@launch
                val diceValue = LudoBotAgent.rollDice()
                val newState = LudoEngine.rollDice(gameState, diceValue)
                localGameState = newState

                // Immediately update UI with new state (don't wait for Firestore round-trip)
                val currentPlayer = newState.currentPlayer
                val movableIds = if (newState.mustSelectToken) {
                    LudoEngine.getMovableTokens(currentPlayer, diceValue).map { it.id }
                } else emptyList()

                _uiState.value = _uiState.value.copy(
                    isRolling = false,
                    gameState = newState,
                    diceValue = diceValue,
                    canRoll = false,
                    mustSelectToken = newState.mustSelectToken,
                    movableTokenIds = movableIds
                )

                // Then persist to Firestore
                matchRepository.updateMatchState(roomId, newState)

                // Auto-move if only one token can move (skip "Tap again" confirmation)
                if (newState.mustSelectToken && movableIds.size == 1) {
                    delay(300)
                    executeOnlineTokenMove(movableIds.first())
                }
            } else {
                // Send roll action to host — keep isRolling true until Firestore
                // delivers the actual dice value (handled in handleStateUpdate)
                matchRepository.sendAction(roomId, LudoActionType.ROLL_DICE)
                _uiState.value = _uiState.value.copy(canRoll = false)
            }
        }
    }

    /**
     * Move a token (player action).
     * First click: shows path preview
     * Second click (same token): executes move with animation
     */
    fun moveToken(tokenId: Int) {
        val state = _uiState.value
        if (!state.mustSelectToken) return
        if (tokenId !in state.movableTokenIds) return
        if (state.isTokenAnimating) return

        val gameState = state.gameState ?: return
        val diceValue = state.diceValue ?: return

        // If clicking the same token that's already selected, execute the move
        if (state.selectedTokenId == tokenId) {
            executeOnlineTokenMove(tokenId)
            return
        }

        // First click: show path preview
        val localPlayer = gameState.players.find { it.id == state.localPlayerId } ?: return
        val token = localPlayer.tokens.find { it.id == tokenId } ?: return

        // Calculate path preview
        val pathPositions = calculateOnlineTokenPath(token, localPlayer.color, diceValue)

        _uiState.value = state.copy(
            selectedTokenId = tokenId,
            previewPath = pathPositions,
            message = "Tap again to move"
        )
    }

    /**
     * Calculate the path positions for a token's movement (online).
     */
    private fun calculateOnlineTokenPath(
        token: Token,
        color: LudoColor,
        diceValue: Int
    ): List<BoardPosition> {
        if (diceValue <= 0) return emptyList()

        val positions = mutableListOf<BoardPosition>()

        if (token.state == TokenState.HOME) {
            if (diceValue == 6) {
                LudoBoardPositions.getGridPosition(0, color)?.let {
                    positions.add(it)
                }
            }
            return positions
        }

        var currentPos = token.position
        for (step in 1..diceValue) {
            val nextPos = currentPos + 1

            // If reaching finish position, add color's finish triangle and stop
            if (nextPos >= 56) {
                positions.add(LudoBoardPositions.getFinishPosition(color))
                break
            }

            val boardPos = LudoBoardPositions.getGridPosition(nextPos, color)
            if (boardPos != null) {
                positions.add(boardPos)
            } else {
                // Position has no grid mapping — still count the step
                positions.add(positions.lastOrNull() ?: LudoBoardPositions.getFinishPosition(color))
            }
            currentPos = nextPos
        }

        return positions
    }

    /**
     * Execute token move with animation (online).
     */
    private fun executeOnlineTokenMove(tokenId: Int) {
        val state = _uiState.value

        // Stop AFK timer during animation (matching offline behavior)
        stopAfkMonitor()

        // Capture state snapshot BEFORE animation for non-host optimistic calculation
        // During the 600ms animation, handleStateUpdate() can overwrite localGameState
        val preAnimationState = localGameState

        // Start animation
        _uiState.value = state.copy(
            isTokenAnimating = true,
            animatingTokenId = tokenId,
            animationProgress = 0f,
            showTimer = false
        )

        viewModelScope.launch {
            // Animate progress from 0 to 1 over 600ms (~60fps)
            val animationDuration = 600L
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / animationDuration).coerceAtMost(1f)
                _uiState.value = _uiState.value.copy(animationProgress = progress)
                if (progress >= 1f) break
                delay(16)
            }

            // Small settle delay
            delay(50)

            if (state.isHost) {
                val gameState = localGameState ?: return@launch
                when (val result = LudoEngine.moveToken(gameState, tokenId)) {
                    is MoveResult.Success -> {
                        localGameState = result.newState

                        _uiState.value = _uiState.value.copy(
                            gameState = result.newState,
                            isTokenAnimating = false,
                            animatingTokenId = null,
                            animationProgress = 0f,
                            selectedTokenId = null,
                            previewPath = emptyList()
                        )

                        matchRepository.updateMatchState(roomId, result.newState)

                        if (result.hasWon) {
                            matchRepository.endMatch(roomId, result.newState.winnerId!!)
                        }
                    }
                    is MoveResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.message,
                            isTokenAnimating = false,
                            animatingTokenId = null,
                            animationProgress = 0f,
                            selectedTokenId = null,
                            previewPath = emptyList()
                        )
                    }
                }
            } else {
                // Optimistic local update: use pre-animation snapshot to avoid race with handleStateUpdate()
                val optimisticState = if (preAnimationState != null) {
                    when (val result = LudoEngine.moveToken(preAnimationState, tokenId)) {
                        is MoveResult.Success -> result.newState
                        is MoveResult.Error -> null
                    }
                } else null

                _uiState.value = _uiState.value.copy(
                    gameState = optimisticState ?: _uiState.value.gameState,
                    isTokenAnimating = false,
                    animatingTokenId = null,
                    animationProgress = 0f,
                    selectedTokenId = null,
                    previewPath = emptyList()
                )

                // Send move to host — Firestore state will overwrite with canonical result
                matchRepository.sendAction(roomId, LudoActionType.MOVE_TOKEN, tokenId)
            }
        }
    }

    /**
     * Clear token selection.
     */
    fun clearTokenSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTokenId = null,
            previewPath = emptyList()
        )
    }

    /**
     * Leave the game.
     */
    fun leaveGame() {
        viewModelScope.launch {
            matchRepository.markDisconnected(roomId)
            roomRepository.leaveRoom(roomId)
        }
        cleanup()
    }

    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        observeJob?.cancel()
        hostJob?.cancel()
        presenceJob?.cancel()
        afkJob?.cancel()
        heartbeatJob?.cancel()
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear info message.
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
        // TEMPORARY: Upload game log on exit for remote debugging
        LudoDebugLogger.uploadToFirestore("game_exit")
        // Mark as disconnected on cleanup
        viewModelScope.launch {
            matchRepository.markDisconnected(roomId)
        }
    }
}
