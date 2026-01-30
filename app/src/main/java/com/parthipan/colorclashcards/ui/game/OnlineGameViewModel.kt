package com.parthipan.colorclashcards.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.data.model.ActionType
import com.parthipan.colorclashcards.data.model.PlayerAction
import com.parthipan.colorclashcards.data.model.PublicMatchState
import com.parthipan.colorclashcards.data.model.RoomPlayer
import com.parthipan.colorclashcards.data.repository.MatchRepository
import com.parthipan.colorclashcards.data.repository.RoomRepository
import com.parthipan.colorclashcards.game.engine.BotAgent
import com.parthipan.colorclashcards.game.engine.GameEngine
import com.parthipan.colorclashcards.game.model.Card
import com.parthipan.colorclashcards.game.model.CardColor
import com.parthipan.colorclashcards.game.model.GameState
import com.parthipan.colorclashcards.game.model.Player
import com.parthipan.colorclashcards.game.model.TurnPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * UI state for online game.
 */
data class OnlineGameUiState(
    val isHost: Boolean = false,
    val currentUserId: String = "",
    val publicState: PublicMatchState? = null,
    val myHand: List<Card> = emptyList(),
    val playableCards: List<Card> = emptyList(),
    val isMyTurn: Boolean = false,
    val topCard: Card? = null,
    val currentColor: CardColor = CardColor.RED,
    val turnPhase: TurnPhase = TurnPhase.PLAY_OR_DRAW,
    val players: List<OnlinePlayerState> = emptyList(),
    val winner: OnlinePlayerState? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showColorPicker: Boolean = false,
    val pendingWildCard: Card? = null
)

/**
 * Simplified player state for UI.
 */
data class OnlinePlayerState(
    val id: String,
    val name: String,
    val isBot: Boolean,
    val handSize: Int,
    val isCurrentTurn: Boolean,
    val hasCalledLastCard: Boolean = false
)

/**
 * ViewModel for online gameplay with authoritative host.
 */
class OnlineGameViewModel(
    private val matchRepository: MatchRepository = MatchRepository(),
    private val roomRepository: RoomRepository = RoomRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineGameUiState())
    val uiState: StateFlow<OnlineGameUiState> = _uiState.asStateFlow()

    private var roomId: String = ""
    private var observeJob: Job? = null
    private var hostJob: Job? = null
    private var botJob: Job? = null

    // Cache of players from room
    private var roomPlayers: List<RoomPlayer> = emptyList()

    // Host-only: full game state for processing
    private var localGameState: GameState? = null

    /**
     * Initialize online game.
     */
    fun initialize(roomId: String, isHost: Boolean) {
        this.roomId = roomId
        val userId = matchRepository.currentUserId ?: ""

        _uiState.value = _uiState.value.copy(
            currentUserId = userId,
            isHost = isHost,
            isLoading = true
        )

        // Start observing room players
        observeRoomPlayers()

        if (isHost) {
            // Host needs to initialize match state
            initializeMatchAsHost()
        } else {
            // Non-host just observes match state
            observeMatchState()
        }
    }

    /**
     * Observe room players list.
     */
    private fun observeRoomPlayers() {
        viewModelScope.launch {
            matchRepository.observeRoomPlayers(roomId).collect { players ->
                roomPlayers = players
                updatePlayersUI()
            }
        }
    }

    /**
     * Initialize match as host.
     */
    private fun initializeMatchAsHost() {
        viewModelScope.launch {
            try {
                // Wait for room data to load
                delay(500)

                // Get room for player list
                val roomResult = roomRepository.getRoom(roomId)
                val room = roomResult.getOrNull()

                if (room == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to get room data"
                    )
                    return@launch
                }

                roomPlayers = room.players

                // Create players list from room players
                val players = mutableListOf<Player>()
                room.players.forEach { roomPlayer ->
                    players.add(Player(
                        id = roomPlayer.odId,
                        name = roomPlayer.odisplayName,
                        isBot = false
                    ))
                }

                if (players.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No players in room"
                    )
                    return@launch
                }

                // Fill with bots if needed (minimum 2 players)
                var botCount = 1
                while (players.size < 2) {
                    val botId = "bot_$botCount"
                    val botName = "Bot $botCount"
                    players.add(Player(id = botId, name = botName, isBot = true))
                    botCount++
                }

                // Start the game
                val gameState = GameEngine.startGame(players)
                localGameState = gameState

                // Write initial state to Firestore
                val result = matchRepository.initializeMatch(roomId, gameState)
                result.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to initialize match: ${e.message}"
                    )
                    return@launch
                }

                // Start observing match state and actions
                observeMatchState()
                startHostProcessing()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to start game: ${e.message}"
                )
            }
        }
    }

    /**
     * Observe public match state and my hand.
     */
    private fun observeMatchState() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                matchRepository.observePublicState(roomId),
                matchRepository.observeMyHand(roomId)
            ) { publicState, myHand ->
                Pair(publicState, myHand)
            }.collect { (publicState, myHand) ->
                if (publicState == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@collect
                }

                val userId = _uiState.value.currentUserId
                val isMyTurn = publicState.currentTurn == userId

                // Parse top card
                val topCard = publicState.getTopCardOrNull()
                val currentColor = try {
                    CardColor.valueOf(publicState.currentColor)
                } catch (e: Exception) { CardColor.RED }
                val turnPhase = try {
                    TurnPhase.valueOf(publicState.turnPhase)
                } catch (e: Exception) { TurnPhase.PLAY_OR_DRAW }

                // Calculate playable cards
                val playable = if (isMyTurn && topCard != null && turnPhase == TurnPhase.PLAY_OR_DRAW) {
                    GameEngine.getPlayableCards(myHand, topCard, currentColor)
                } else {
                    emptyList()
                }

                // Build player states from hand counts
                val players = publicState.handCounts.map { (playerId, handSize) ->
                    val roomPlayer = roomPlayers.find { it.odId == playerId }
                    val isBot = playerId.startsWith("bot_")
                    OnlinePlayerState(
                        id = playerId,
                        name = roomPlayer?.odisplayName ?: if (isBot) "Bot" else "Player",
                        isBot = isBot,
                        handSize = handSize,
                        isCurrentTurn = playerId == publicState.currentTurn
                    )
                }

                // Check for winner
                val winner = publicState.winnerId?.let { winnerId ->
                    players.find { it.id == winnerId }
                }

                _uiState.value = _uiState.value.copy(
                    publicState = publicState,
                    myHand = myHand,
                    playableCards = playable,
                    isMyTurn = isMyTurn,
                    topCard = topCard,
                    currentColor = currentColor,
                    turnPhase = turnPhase,
                    players = players,
                    winner = winner,
                    isLoading = false
                )

                // If host, check for bot turns
                if (_uiState.value.isHost) {
                    checkBotTurn()
                }
            }
        }
    }

    /**
     * Update players UI when room players change.
     */
    private fun updatePlayersUI() {
        val publicState = _uiState.value.publicState ?: return

        val players = publicState.handCounts.map { (playerId, handSize) ->
            val roomPlayer = roomPlayers.find { it.odId == playerId }
            val isBot = playerId.startsWith("bot_")
            OnlinePlayerState(
                id = playerId,
                name = roomPlayer?.odisplayName ?: if (isBot) "Bot" else "Player",
                isBot = isBot,
                handSize = handSize,
                isCurrentTurn = playerId == publicState.currentTurn
            )
        }

        _uiState.value = _uiState.value.copy(players = players)
    }

    /**
     * Start host processing loop to handle player actions.
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
    }

    /**
     * Process a player action (host only).
     */
    private suspend fun processAction(action: PlayerAction) {
        val gameState = localGameState ?: return

        // Skip if not this player's turn
        if (gameState.currentPlayer.id != action.playerId) {
            matchRepository.deleteAction(roomId, action.id)
            return
        }

        var newState: GameState? = null

        when (action.type) {
            ActionType.PLAY_CARD.name -> {
                val card = gameState.currentPlayer.hand.find { it.id == action.cardId }
                if (card != null) {
                    val chosenColor = action.chosenColor?.let {
                        try { CardColor.valueOf(it) } catch (e: Exception) { null }
                    }
                    newState = GameEngine.playCard(gameState, card, chosenColor)
                }
            }
            ActionType.DRAW_CARD.name -> {
                val count = if (gameState.turnPhase == TurnPhase.MUST_DRAW) {
                    gameState.pendingDrawCount
                } else {
                    1
                }
                newState = GameEngine.drawCard(gameState, count)
            }
            ActionType.CALL_LAST_CARD.name -> {
                newState = GameEngine.callLastCard(gameState, action.playerId)
            }
        }

        // Delete processed action
        matchRepository.deleteAction(roomId, action.id)

        // Update state if action was valid
        if (newState != null) {
            localGameState = newState
            matchRepository.updateMatchState(roomId, newState, action.id)

            // Check for game end
            if (newState.winner != null) {
                matchRepository.endMatch(roomId, newState.winner!!.id)
            }
        }
    }

    /**
     * Check if it's a bot's turn and handle it (host only).
     */
    private fun checkBotTurn() {
        val state = _uiState.value
        if (!state.isHost) return

        val currentPlayer = state.players.find { it.isCurrentTurn }
        if (currentPlayer?.isBot != true) return

        // Cancel any existing bot job
        botJob?.cancel()
        botJob = viewModelScope.launch {
            delay(BotAgent.getThinkingDelayMs())

            val gameState = localGameState ?: return@launch
            val bot = gameState.currentPlayer

            // Handle forced draw
            if (gameState.turnPhase == TurnPhase.MUST_DRAW) {
                val newState = GameEngine.drawCard(gameState, gameState.pendingDrawCount)
                localGameState = newState
                matchRepository.updateMatchState(roomId, newState)
                return@launch
            }

            // Choose a card to play
            val cardToPlay = BotAgent.chooseCard(
                bot.hand,
                gameState.topCard!!,
                gameState.currentColor,
                "normal"
            )

            if (cardToPlay != null) {
                val chosenColor = if (cardToPlay.type.isWild()) {
                    BotAgent.chooseWildColor(bot.hand - cardToPlay)
                } else null

                val newState = GameEngine.playCard(gameState, cardToPlay, chosenColor)
                if (newState != null) {
                    localGameState = newState

                    // Bot auto-calls last card
                    if (newState.currentPlayer.cardCount == 1 && !newState.currentPlayer.hasCalledLastCard) {
                        val calledState = GameEngine.callLastCard(newState, bot.id)
                        localGameState = calledState
                        matchRepository.updateMatchState(roomId, calledState)
                    } else {
                        matchRepository.updateMatchState(roomId, newState)
                    }

                    if (newState.winner != null) {
                        matchRepository.endMatch(roomId, newState.winner!!.id)
                    }
                }
            } else {
                // Draw a card
                val newState = GameEngine.drawCard(gameState, 1)
                localGameState = newState
                matchRepository.updateMatchState(roomId, newState)

                delay(BotAgent.getThinkingDelayMs() / 2)

                val updatedState = localGameState ?: return@launch
                val drawnCard = updatedState.currentPlayer.hand.lastOrNull()
                if (drawnCard != null &&
                    BotAgent.shouldPlayDrawnCard(drawnCard, updatedState.topCard!!, updatedState.currentColor)
                ) {
                    val chosenColor = if (drawnCard.type.isWild()) {
                        BotAgent.chooseWildColor(updatedState.currentPlayer.hand - drawnCard)
                    } else null

                    val playedState = GameEngine.playCard(updatedState, drawnCard, chosenColor)
                    if (playedState != null) {
                        localGameState = playedState
                        matchRepository.updateMatchState(roomId, playedState)

                        if (playedState.winner != null) {
                            matchRepository.endMatch(roomId, playedState.winner!!.id)
                        }
                    }
                } else {
                    val passedState = GameEngine.passTurn(updatedState)
                    localGameState = passedState
                    matchRepository.updateMatchState(roomId, passedState)
                }
            }
        }
    }

    /**
     * Play a card (client action).
     */
    fun playCard(card: Card, chosenColor: CardColor? = null) {
        if (!_uiState.value.isMyTurn) return

        // If wild card and no color chosen, show picker
        if (card.type.isWild() && chosenColor == null) {
            _uiState.value = _uiState.value.copy(
                showColorPicker = true,
                pendingWildCard = card
            )
            return
        }

        viewModelScope.launch {
            matchRepository.sendAction(
                roomId = roomId,
                actionType = ActionType.PLAY_CARD,
                cardId = card.id,
                chosenColor = chosenColor
            )
        }

        _uiState.value = _uiState.value.copy(
            showColorPicker = false,
            pendingWildCard = null
        )
    }

    /**
     * Select color for wild card.
     */
    fun selectWildColor(color: CardColor) {
        val card = _uiState.value.pendingWildCard ?: return
        playCard(card, color)
    }

    /**
     * Cancel color selection.
     */
    fun cancelColorSelection() {
        _uiState.value = _uiState.value.copy(
            showColorPicker = false,
            pendingWildCard = null
        )
    }

    /**
     * Draw a card (client action).
     */
    fun drawCard() {
        if (!_uiState.value.isMyTurn) return

        viewModelScope.launch {
            matchRepository.sendAction(
                roomId = roomId,
                actionType = ActionType.DRAW_CARD
            )
        }
    }

    /**
     * Call last card (client action).
     */
    fun callLastCard() {
        viewModelScope.launch {
            matchRepository.sendAction(
                roomId = roomId,
                actionType = ActionType.CALL_LAST_CARD
            )
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        hostJob?.cancel()
        botJob?.cancel()
    }
}
