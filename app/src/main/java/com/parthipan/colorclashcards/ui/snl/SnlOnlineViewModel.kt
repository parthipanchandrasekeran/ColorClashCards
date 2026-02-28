package com.parthipan.colorclashcards.ui.snl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.parthipan.colorclashcards.data.model.*
import com.parthipan.colorclashcards.data.repository.SnlMatchRepository
import com.parthipan.colorclashcards.data.repository.SnlRoomRepository
import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.snl.engine.SnlBoard
import com.parthipan.colorclashcards.game.snl.engine.SnlBotAgent
import com.parthipan.colorclashcards.game.snl.engine.SnlEngine
import com.parthipan.colorclashcards.game.snl.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class SnlOnlineUiState(
    val gameState: SnlGameState? = null,
    val room: SnlRoom? = null,
    val isHost: Boolean = false,
    val localPlayerId: String = "",
    val isMyTurn: Boolean = false,
    val isRolling: Boolean = false,
    val canRoll: Boolean = false,
    val diceValue: Int? = null,
    val message: String? = null,
    val error: String? = null,
    val showWinDialog: Boolean = false,
    val winnerName: String? = null,
    val isLoading: Boolean = true,
    val disconnectedPlayers: Set<String> = emptySet(),
    val gameEnded: Boolean = false,
    val endReason: String? = null,
    // Timer
    val timerRemainingSeconds: Int = 30,
    val timerProgress: Float = 1f,
    val showTimer: Boolean = false,
    val isTimerWarning: Boolean = false,
    // Animation
    val isAnimating: Boolean = false,
    val animatingPlayerId: String? = null,
    val animFromPos: Int = 0,
    val animToPos: Int = 0,
    val animFinalPos: Int = 0,
    val animationProgress: Float = 0f,
    val lastMoveType: SnlMoveType? = null
)

class SnlOnlineViewModel : ViewModel() {

    private val roomRepository = SnlRoomRepository()
    private val matchRepository = SnlMatchRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(SnlOnlineUiState())
    val uiState: StateFlow<SnlOnlineUiState> = _uiState.asStateFlow()

    private var roomId: String = ""
    private var localGameState: SnlGameState? = null
    private var lastTurnStartedAt: Timestamp? = null
    private var isProcessingAfkTurn = false

    private var observeJob: Job? = null
    private var hostJob: Job? = null
    private var presenceJob: Job? = null
    private var afkJob: Job? = null
    private var heartbeatJob: Job? = null

    private val localPlayerId: String get() = auth.currentUser?.uid ?: ""

    fun initialize(roomId: String, isHost: Boolean) {
        this.roomId = roomId
        _uiState.value = _uiState.value.copy(isHost = isHost, localPlayerId = localPlayerId, isLoading = true)

        if (isHost) initializeMatchAsHost() else observeMatch()
        startHeartbeat()
    }

    private fun initializeMatchAsHost() {
        viewModelScope.launch {
            val room = roomRepository.getRoom(roomId).getOrNull()
            if (room == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Room not found")
                return@launch
            }

            val players = room.players.map { rp ->
                SnlPlayer(id = rp.odId, name = rp.odisplayName, colorIndex = rp.colorIndex, isOnline = true)
            }

            val layout = try { SnlBoardLayout.valueOf(room.boardLayout) } catch (_: Exception) { SnlBoardLayout.CLASSIC }
            val (snakes, ladders) = when (layout) {
                SnlBoardLayout.CLASSIC -> SnlBoard.CLASSIC_SNAKES to SnlBoard.CLASSIC_LADDERS
                SnlBoardLayout.RANDOMIZED -> SnlBoard.generateRandomLayout()
            }

            val gameState = SnlGameState.createOnlineGame(players, players.first().id, layout, snakes, ladders)
            localGameState = gameState

            val initResult = matchRepository.initializeMatch(roomId, gameState)
            if (initResult.isFailure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to start: ${initResult.exceptionOrNull()?.message}")
                return@launch
            }

            observeMatch()
            startHostProcessing()
        }
    }

    private fun observeMatch() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                matchRepository.observeMatchState(roomId).distinctUntilChanged(),
                matchRepository.observePresence(roomId).distinctUntilChanged(),
                roomRepository.observeRoom(roomId).distinctUntilChanged()
            ) { matchState, presenceList, room -> Triple(matchState, presenceList, room) }
                .collect { (matchState, presenceList, room) ->
                    handleStateUpdate(matchState, presenceList, room)
                }
        }
    }

    private fun handleStateUpdate(matchState: SnlMatchState?, presenceList: List<SnlPlayerPresence>, room: SnlRoom?) {
        if (matchState == null) { _uiState.value = _uiState.value.copy(isLoading = true); return }

        val gameState = matchState.toGameState()
        localGameState = gameState
        lastTurnStartedAt = matchState.turnStartedAt

        val isMyTurn = gameState.currentTurnPlayerId == localPlayerId

        val disconnected = presenceList.filter { !it.isOnline }.map { it.playerId }.toSet()
        val droppedPlayers = presenceList.filter { !it.isOnline && !matchRepository.canRejoin(it.disconnectedAt) }.map { it.playerId }
        val activePlayers = gameState.players.filter { it.id !in droppedPlayers }

        val previousTurnPlayer = _uiState.value.gameState?.currentTurnPlayerId
        val turnChanged = previousTurnPlayer != null && previousTurnPlayer != gameState.currentTurnPlayerId
        val newDiceValue = when {
            matchState.diceValue != null && !turnChanged -> matchState.diceValue
            matchState.diceValue != null && _uiState.value.isRolling -> matchState.diceValue
            turnChanged -> null
            else -> _uiState.value.diceValue
        }

        val newIsRolling = when {
            !isMyTurn -> false
            _uiState.value.isRolling && matchState.diceValue != null -> false
            else -> _uiState.value.isRolling
        }

        _uiState.value = _uiState.value.copy(
            gameState = gameState,
            room = room,
            isLoading = false,
            isRolling = newIsRolling,
            isMyTurn = isMyTurn,
            diceValue = newDiceValue,
            canRoll = isMyTurn && gameState.canRollDice,
            disconnectedPlayers = disconnected,
            showWinDialog = gameState.winnerId != null,
            winnerName = gameState.winner?.name,
            gameEnded = gameState.gameStatus == GameStatus.FINISHED
        )

        if (isMyTurn && !gameState.isGameOver) startAfkMonitor(matchState.turnStartedAt) else stopAfkMonitor()
        if (_uiState.value.isHost && activePlayers.size < 2 && !gameState.isGameOver) {
            handleInsufficientPlayers(activePlayers.firstOrNull()?.id)
        }
    }

    private fun startHostProcessing() {
        hostJob?.cancel()
        hostJob = viewModelScope.launch {
            matchRepository.observeActions(roomId).collect { actions ->
                for (action in actions) processAction(action)
            }
        }

        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (true) { delay(5000); checkAfkAndDisconnects() }
        }
    }

    private suspend fun processAction(action: SnlPlayerAction) {
        val gameState = localGameState ?: return
        if (gameState.currentTurnPlayerId != action.playerId) {
            matchRepository.deleteAction(roomId, action.id); return
        }

        when (action.type) {
            SnlActionType.ROLL_DICE.name -> {
                if (gameState.canRollDice) {
                    val diceValue = SnlBotAgent.rollDice()
                    val result = SnlEngine.rollDice(gameState, diceValue)
                    localGameState = result.newState
                    _uiState.value = _uiState.value.copy(gameState = result.newState, diceValue = diceValue)
                    matchRepository.updateMatchState(roomId, result.newState, diceValue)

                    if (result.newState.isGameOver) {
                        result.newState.winnerId?.let { winnerId ->
                            matchRepository.endMatch(roomId, winnerId)
                        }
                    }
                }
            }
            SnlActionType.HEARTBEAT.name -> { /* keep-alive */ }
        }

        matchRepository.deleteAction(roomId, action.id)
    }

    private suspend fun checkAfkAndDisconnects() {
        if (!_uiState.value.isHost || isProcessingAfkTurn) return
        val gameState = localGameState ?: return
        if (gameState.isGameOver) return

        val turnStartedAt = lastTurnStartedAt ?: return
        if (matchRepository.isPlayerAfk(turnStartedAt)) {
            val currentPlayer = gameState.currentPlayer
            if (!currentPlayer.isBot) {
                isProcessingAfkTurn = true
                try { processAfkAutoPlay(gameState) } finally { isProcessingAfkTurn = false }
            }
        }
    }

    private suspend fun processAfkAutoPlay(initialState: SnlGameState) {
        var gameState = initialState
        val afkPlayerId = gameState.currentTurnPlayerId

        while (gameState.currentTurnPlayerId == afkPlayerId && !gameState.isGameOver) {
            _uiState.value = _uiState.value.copy(message = "${gameState.currentPlayer.name} (auto-play)...")
            delay(500)

            val diceValue = SnlBotAgent.rollDice()
            val result = SnlEngine.rollDice(gameState, diceValue)
            gameState = result.newState
            localGameState = gameState
            matchRepository.updateMatchState(roomId, gameState, diceValue)

            if (result.newState.isGameOver) {
                gameState.winnerId?.let { winnerId ->
                    matchRepository.endMatch(roomId, winnerId)
                }
                return
            }
            if (!result.bonusTurn) break
            delay(500)
        }
    }

    private fun handleInsufficientPlayers(remainingPlayerId: String?) {
        viewModelScope.launch {
            matchRepository.endMatchDueToDropout(roomId, remainingPlayerId)
            _uiState.value = _uiState.value.copy(
                gameEnded = true,
                endReason = if (remainingPlayerId != null) "Other players left. You win!" else "All players disconnected"
            )
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) { matchRepository.updatePresence(roomId); delay(10_000) }
        }
    }

    private fun startAfkMonitor(turnStartedAt: Timestamp?) {
        afkJob?.cancel()
        if (turnStartedAt == null) return

        afkJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - turnStartedAt.toDate().time
                val remainingMs = SnlMatchState.AFK_TIMEOUT_MS - elapsed
                val remainingSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)
                val progress = (remainingMs.toFloat() / SnlMatchState.AFK_TIMEOUT_MS).coerceIn(0f, 1f)

                _uiState.value = _uiState.value.copy(
                    timerRemainingSeconds = remainingSeconds,
                    timerProgress = progress,
                    showTimer = true,
                    isTimerWarning = remainingSeconds <= 10
                )

                if (remainingSeconds <= 0) break
                delay(1000)
            }
        }
    }

    private fun stopAfkMonitor() {
        afkJob?.cancel()
        _uiState.value = _uiState.value.copy(showTimer = false, timerProgress = 1f, timerRemainingSeconds = 30, isTimerWarning = false)
    }

    fun rollDice() {
        val state = _uiState.value
        if (!state.canRoll || state.isRolling) return

        _uiState.value = state.copy(isRolling = true)

        viewModelScope.launch {
            if (state.isHost) {
                delay(500)
                val gameState = localGameState ?: return@launch
                val diceValue = SnlBotAgent.rollDice()
                val result = SnlEngine.rollDice(gameState, diceValue)
                localGameState = result.newState

                _uiState.value = _uiState.value.copy(
                    isRolling = false,
                    gameState = result.newState,
                    diceValue = diceValue,
                    canRoll = false
                )
                matchRepository.updateMatchState(roomId, result.newState, diceValue)

                if (result.newState.isGameOver) {
                    result.newState.winnerId?.let { winnerId ->
                        matchRepository.endMatch(roomId, winnerId)
                    }
                }
            } else {
                matchRepository.sendAction(roomId, SnlActionType.ROLL_DICE)
                _uiState.value = _uiState.value.copy(canRoll = false)
            }
        }
    }

    fun leaveGame() {
        viewModelScope.launch {
            matchRepository.markDisconnected(roomId)
            roomRepository.leaveRoom(roomId)
        }
        cleanup()
    }

    private fun cleanup() {
        observeJob?.cancel()
        hostJob?.cancel()
        presenceJob?.cancel()
        afkJob?.cancel()
        heartbeatJob?.cancel()
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        cleanup()
        viewModelScope.launch { matchRepository.markDisconnected(roomId) }
    }
}
