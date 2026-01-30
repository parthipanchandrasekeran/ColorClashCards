package com.parthipan.colorclashcards.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.game.engine.BotAgent
import com.parthipan.colorclashcards.game.engine.GameEngine
import com.parthipan.colorclashcards.game.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the game screen.
 */
data class GameUiState(
    val gameState: GameState? = null,
    val isLoading: Boolean = true,
    val showColorPicker: Boolean = false,
    val pendingWildCard: Card? = null,
    val showWinDialog: Boolean = false,
    val winnerName: String? = null,
    val lastDrawnCard: Card? = null,
    val canPlayDrawnCard: Boolean = false,
    val message: String? = null,
    val isProcessingBotTurn: Boolean = false,
    // Round/Match UI state
    val showRoundSummary: Boolean = false,
    val showFinalResults: Boolean = false
)

/**
 * ViewModel for the game screen.
 */
class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var botTurnJob: Job? = null
    private var timerJob: Job? = null
    private var difficulty: String = "easy"
    private var humanPlayerId: String = ""

    /**
     * Start a new offline game.
     *
     * @param botCount Number of bot players (1-3)
     * @param difficulty Game difficulty ("easy" or "normal")
     */
    fun startOfflineGame(botCount: Int, difficulty: String) {
        this.difficulty = difficulty

        val humanPlayer = Player.human("You")
        humanPlayerId = humanPlayer.id

        val botPlayers = (1..botCount).map { index ->
            Player.bot("Bot $index")
        }

        val allPlayers = listOf(humanPlayer) + botPlayers
        val initialState = GameEngine.startGame(allPlayers)

        _uiState.value = GameUiState(
            gameState = initialState,
            isLoading = false
        )

        // Start the game timer
        startGameTimer()

        // If first player is a bot, start bot turn
        checkAndProcessBotTurn()
    }

    /**
     * Start the main game timer loop.
     * Ticks every second and handles:
     * - Turn timer countdown (for human players)
     * - Round elapsed time tracking
     * - Sudden death activation and countdown
     */
    private fun startGameTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Tick every second

                val state = _uiState.value.gameState ?: continue
                if (state.gamePhase != GamePhase.PLAYING) continue

                var newState = state

                // Increment round elapsed time
                newState = newState.copy(
                    roundSecondsElapsed = newState.roundSecondsElapsed + 1
                )

                // Check for sudden death activation
                if (newState.shouldActivateSuddenDeath) {
                    newState = newState.copy(
                        suddenDeathActive = true,
                        suddenDeathSecondsRemaining = GameState.SUDDEN_DEATH_SECONDS
                    )
                    _uiState.value = _uiState.value.copy(
                        gameState = newState,
                        message = "Final minute! Time running out!"
                    )
                    continue
                }

                // Handle sudden death countdown
                if (newState.suddenDeathActive) {
                    val newSuddenDeathRemaining = newState.suddenDeathSecondsRemaining - 1
                    if (newSuddenDeathRemaining <= 0) {
                        // Round timeout - determine winner by lowest hand points
                        handleRoundTimeout()
                        continue
                    }
                    newState = newState.copy(
                        suddenDeathSecondsRemaining = newSuddenDeathRemaining
                    )
                }

                // Only decrement turn timer for human players (bots ignore timer)
                val isHumanTurn = newState.currentPlayer.id == humanPlayerId &&
                                  !_uiState.value.isProcessingBotTurn

                if (isHumanTurn) {
                    val newTurnRemaining = newState.turnSecondsRemaining - 1
                    if (newTurnRemaining <= 0) {
                        // Turn timeout - auto-draw and advance turn
                        handleTurnTimeout()
                        continue
                    }
                    newState = newState.copy(
                        turnSecondsRemaining = newTurnRemaining
                    )
                }

                _uiState.value = _uiState.value.copy(gameState = newState)
            }
        }
    }

    /**
     * Handle turn timeout - auto-draw 1 card and advance turn.
     */
    private fun handleTurnTimeout() {
        val state = _uiState.value.gameState ?: return
        if (state.gamePhase != GamePhase.PLAYING) return

        val newState = GameEngine.handleTurnTimeout(state)

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            message = "Time's up! Drew a card.",
            lastDrawnCard = null,
            canPlayDrawnCard = false
        )

        // Clear message and check for bot turn
        viewModelScope.launch {
            delay(1500)
            if (_uiState.value.message == "Time's up! Drew a card.") {
                _uiState.value = _uiState.value.copy(message = null)
            }
            checkAndProcessBotTurn()
        }
    }

    /**
     * Handle round timeout (sudden death expired).
     */
    private fun handleRoundTimeout() {
        val state = _uiState.value.gameState ?: return
        if (state.gamePhase != GamePhase.PLAYING) return

        val newState = GameEngine.handleRoundTimeout(state)

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            showRoundSummary = newState.isRoundOver,
            showFinalResults = newState.isMatchOver,
            winnerName = newState.winner?.name,
            message = null
        )
    }

    /**
     * Get playable cards for the human player.
     */
    fun getPlayableCards(): List<Card> {
        val state = _uiState.value.gameState ?: return emptyList()
        val topCard = state.topCard ?: return emptyList()

        return GameEngine.getPlayableCards(
            state.currentPlayer.hand,
            topCard,
            state.currentColor
        )
    }

    /**
     * Check if a specific card can be played.
     */
    fun canPlayCard(card: Card): Boolean {
        val state = _uiState.value.gameState ?: return false
        if (state.currentPlayer.id != humanPlayerId) return false
        if (state.turnPhase == TurnPhase.MUST_DRAW) return false

        val topCard = state.topCard ?: return false
        return card.canPlayOn(topCard, state.currentColor)
    }

    /**
     * Play a card from the human player's hand.
     */
    fun playCard(card: Card) {
        val state = _uiState.value.gameState ?: return
        if (state.currentPlayer.id != humanPlayerId) return

        // If it's a wild card, show color picker
        if (card.type.isWild()) {
            _uiState.value = _uiState.value.copy(
                showColorPicker = true,
                pendingWildCard = card
            )
            return
        }

        executePlayCard(card, null)
    }

    /**
     * Select color for wild card.
     */
    fun selectWildColor(color: CardColor) {
        val card = _uiState.value.pendingWildCard ?: return

        _uiState.value = _uiState.value.copy(
            showColorPicker = false,
            pendingWildCard = null
        )

        executePlayCard(card, color)
    }

    /**
     * Cancel wild card color selection.
     */
    fun cancelColorPicker() {
        _uiState.value = _uiState.value.copy(
            showColorPicker = false,
            pendingWildCard = null
        )
    }

    /**
     * Execute playing a card.
     */
    private fun executePlayCard(card: Card, chosenColor: CardColor?) {
        val state = _uiState.value.gameState ?: return

        val newState = GameEngine.playCard(state, card, chosenColor)
        if (newState == null) {
            _uiState.value = _uiState.value.copy(
                message = "Cannot play that card"
            )
            return
        }

        // Clear any drawn card state
        _uiState.value = _uiState.value.copy(
            gameState = newState,
            lastDrawnCard = null,
            canPlayDrawnCard = false,
            message = null
        )

        // Check for round/match end
        if (newState.isRoundOver || newState.isMatchOver) {
            _uiState.value = _uiState.value.copy(
                showRoundSummary = newState.isRoundOver,
                showFinalResults = newState.isMatchOver,
                winnerName = newState.winner?.name
            )
            return
        }

        // Check for last card call needed
        val humanPlayer = newState.getPlayer(humanPlayerId)
        if (humanPlayer?.needsLastCardCall == true) {
            _uiState.value = _uiState.value.copy(
                message = "Press 'Last Card!' quickly!"
            )
        }

        // Process bot turn if needed
        checkAndProcessBotTurn()
    }

    /**
     * Draw a card from the deck.
     *
     * Rules:
     * - If MUST_DRAW (from +2/+4): Draw penalty cards, turn ends automatically.
     * - If PLAY_OR_DRAW: Draw exactly ONE card.
     *   - If drawn card is playable: Show "Play it?" prompt.
     *   - If drawn card is NOT playable: Pass turn automatically.
     * - If already DREW_CARD: Cannot draw again.
     */
    fun drawCard() {
        val state = _uiState.value.gameState ?: return
        if (state.currentPlayer.id != humanPlayerId) return

        // Handle forced draw from +2 or +4
        if (state.turnPhase == TurnPhase.MUST_DRAW) {
            val newState = GameEngine.drawCard(state, state.pendingDrawCount)
            _uiState.value = _uiState.value.copy(
                gameState = newState,
                lastDrawnCard = null,
                canPlayDrawnCard = false,
                message = "Drew ${state.pendingDrawCount} cards"
            )
            // Turn already advanced by GameEngine
            checkAndProcessBotTurn()
            return
        }

        // Only allow drawing if in PLAY_OR_DRAW phase (haven't drawn yet this turn)
        if (state.turnPhase != TurnPhase.PLAY_OR_DRAW) return

        // Draw exactly ONE card
        val newState = GameEngine.drawCard(state, 1)
        val drawnCard = newState.currentPlayer.hand.lastOrNull()
        val topCard = newState.topCard

        // Check if drawn card can be played
        if (drawnCard != null && topCard != null && drawnCard.canPlayOn(topCard, newState.currentColor)) {
            // Drawn card is playable - let player choose to play or keep
            _uiState.value = _uiState.value.copy(
                gameState = newState,
                lastDrawnCard = drawnCard,
                canPlayDrawnCard = true,
                message = "You drew ${drawnCard.displayName()}. Play it?"
            )
        } else {
            // Drawn card is NOT playable - pass turn automatically
            val passedState = GameEngine.passTurn(newState)
            _uiState.value = _uiState.value.copy(
                gameState = passedState,
                lastDrawnCard = null,
                canPlayDrawnCard = false,
                message = "Drew a card. Turn ended."
            )
            // Clear message and check for bot turn
            viewModelScope.launch {
                delay(1000)
                if (_uiState.value.message == "Drew a card. Turn ended.") {
                    _uiState.value = _uiState.value.copy(message = null)
                }
            }
            checkAndProcessBotTurn()
        }
    }

    /**
     * Play the card that was just drawn.
     */
    fun playDrawnCard() {
        val drawnCard = _uiState.value.lastDrawnCard ?: return
        _uiState.value = _uiState.value.copy(
            lastDrawnCard = null,
            canPlayDrawnCard = false
        )
        playCard(drawnCard)
    }

    /**
     * Keep the drawn card and pass turn.
     */
    fun keepDrawnCard() {
        val state = _uiState.value.gameState ?: return

        val newState = GameEngine.passTurn(state)

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            lastDrawnCard = null,
            canPlayDrawnCard = false,
            message = null
        )

        checkAndProcessBotTurn()
    }

    /**
     * Call "Last Card!" when down to one card.
     */
    fun callLastCard() {
        val state = _uiState.value.gameState ?: return

        val newState = GameEngine.callLastCard(state, humanPlayerId)

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            message = "Last Card!"
        )

        // Clear message after a delay
        viewModelScope.launch {
            delay(1500)
            if (_uiState.value.message == "Last Card!") {
                _uiState.value = _uiState.value.copy(message = null)
            }
        }
    }

    /**
     * Check if human player can call "Last Card!".
     */
    fun canCallLastCard(): Boolean {
        val state = _uiState.value.gameState ?: return false
        val humanPlayer = state.getPlayer(humanPlayerId) ?: return false
        return humanPlayer.needsLastCardCall
    }

    /**
     * Check and process bot turns.
     */
    private fun checkAndProcessBotTurn() {
        val state = _uiState.value.gameState ?: return
        if (state.gamePhase != GamePhase.PLAYING) return
        if (state.currentPlayer.id == humanPlayerId) return
        if (_uiState.value.isProcessingBotTurn) return

        processBotTurn()
    }

    /**
     * Process a bot player's turn.
     *
     * Bot follows the same rules as human:
     * - If has playable cards: Play one.
     * - If no playable cards: Draw exactly ONE card.
     *   - If drawn card is playable: Play it.
     *   - Otherwise: Pass turn.
     */
    private fun processBotTurn() {
        botTurnJob?.cancel()
        botTurnJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingBotTurn = true)

            // Add thinking delay
            delay(BotAgent.getThinkingDelayMs())

            var state = _uiState.value.gameState ?: return@launch

            // Handle forced draw from +2 or +4
            if (state.turnPhase == TurnPhase.MUST_DRAW) {
                val botName = state.currentPlayer.name
                val drawCount = state.pendingDrawCount
                state = GameEngine.drawCard(state, drawCount)
                _uiState.value = _uiState.value.copy(
                    gameState = state,
                    message = "$botName drew $drawCount cards"
                )
                delay(500)
                // Turn already advanced - check for next bot
                _uiState.value = _uiState.value.copy(isProcessingBotTurn = false)
                delay(500)
                _uiState.value = _uiState.value.copy(message = null)
                checkAndProcessBotTurn()
                return@launch
            }

            // Get current state
            state = _uiState.value.gameState ?: return@launch
            if (state.isGameOver || state.currentPlayer.id == humanPlayerId) {
                _uiState.value = _uiState.value.copy(isProcessingBotTurn = false)
                return@launch
            }

            val bot = state.currentPlayer
            val topCard = state.topCard ?: return@launch

            // Bot chooses a card from hand (if any playable)
            val cardToPlay = BotAgent.chooseCard(
                bot.hand,
                topCard,
                state.currentColor,
                difficulty
            )

            if (cardToPlay != null) {
                // Bot has a playable card - play it
                val chosenColor = if (cardToPlay.type.isWild()) {
                    BotAgent.chooseWildColor(bot.hand.filter { it.id != cardToPlay.id })
                } else null

                val newState = GameEngine.playCard(state, cardToPlay, chosenColor)
                if (newState != null) {
                    _uiState.value = _uiState.value.copy(
                        gameState = newState,
                        message = "${bot.name} played ${cardToPlay.displayName()}" +
                                if (chosenColor != null) " and chose $chosenColor" else ""
                    )

                    // Bot auto-calls last card
                    val updatedBot = newState.getPlayer(bot.id)
                    if (updatedBot?.needsLastCardCall == true) {
                        delay(300)
                        val stateAfterCall = GameEngine.callLastCard(newState, bot.id)
                        _uiState.value = _uiState.value.copy(
                            gameState = stateAfterCall,
                            message = "${bot.name} called Last Card!"
                        )
                    }

                    // Check for round/match end
                    if (newState.isRoundOver || newState.isMatchOver) {
                        _uiState.value = _uiState.value.copy(
                            showRoundSummary = newState.isRoundOver,
                            showFinalResults = newState.isMatchOver,
                            winnerName = newState.winner?.name,
                            isProcessingBotTurn = false
                        )
                        return@launch
                    }
                }
            } else {
                // Bot has NO playable cards - draw exactly ONE card
                val stateAfterDraw = GameEngine.drawCard(state, 1)
                val drawnCard = stateAfterDraw.currentPlayer.hand.lastOrNull()

                _uiState.value = _uiState.value.copy(
                    gameState = stateAfterDraw,
                    message = "${bot.name} drew a card"
                )

                delay(500)

                // Check if drawn card can be played
                val currentState = _uiState.value.gameState ?: return@launch
                val currentTop = currentState.topCard ?: return@launch

                if (drawnCard != null && drawnCard.canPlayOn(currentTop, currentState.currentColor)) {
                    // Drawn card is playable - bot plays it
                    val chosenColor = if (drawnCard.type.isWild()) {
                        BotAgent.chooseWildColor(currentState.currentPlayer.hand.filter { it.id != drawnCard.id })
                    } else null

                    val newState = GameEngine.playCard(currentState, drawnCard, chosenColor)
                    if (newState != null) {
                        _uiState.value = _uiState.value.copy(
                            gameState = newState,
                            message = "${bot.name} played ${drawnCard.displayName()}"
                        )

                        if (newState.isRoundOver || newState.isMatchOver) {
                            _uiState.value = _uiState.value.copy(
                                showRoundSummary = newState.isRoundOver,
                                showFinalResults = newState.isMatchOver,
                                winnerName = newState.winner?.name,
                                isProcessingBotTurn = false
                            )
                            return@launch
                        }
                    }
                } else {
                    // Drawn card is NOT playable - pass turn (no more draws allowed)
                    val passedState = GameEngine.passTurn(currentState)
                    _uiState.value = _uiState.value.copy(gameState = passedState)
                }
            }

            _uiState.value = _uiState.value.copy(isProcessingBotTurn = false)

            // Clear message after delay
            delay(1000)
            _uiState.value = _uiState.value.copy(message = null)

            // Continue with next bot if needed
            checkAndProcessBotTurn()
        }
    }

    /**
     * Check if it's currently the human player's turn.
     */
    fun isHumanTurn(): Boolean {
        val state = _uiState.value.gameState ?: return false
        return state.gamePhase == GamePhase.PLAYING &&
               state.currentPlayer.id == humanPlayerId &&
               !_uiState.value.isProcessingBotTurn
    }

    /**
     * Get the human player.
     */
    fun getHumanPlayer(): Player? {
        return _uiState.value.gameState?.getPlayer(humanPlayerId)
    }

    /**
     * Dismiss win dialog and return to setup.
     */
    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    /**
     * Start the next round in the match.
     */
    fun startNextRound() {
        val state = _uiState.value.gameState ?: return
        if (state.gamePhase != GamePhase.ROUND_OVER) return

        val newState = GameEngine.startNextRound(state)

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            showRoundSummary = false,
            showFinalResults = false,
            winnerName = null,
            message = null,
            lastDrawnCard = null,
            canPlayDrawnCard = false
        )

        // Restart the game timer
        startGameTimer()

        // If first player is a bot, start bot turn
        checkAndProcessBotTurn()
    }

    /**
     * Start a completely new match.
     * Resets all scores and starts from round 1.
     */
    fun startNewMatch() {
        val state = _uiState.value.gameState ?: return
        val newState = GameEngine.startNewMatch(state.players)

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            showRoundSummary = false,
            showFinalResults = false,
            winnerName = null,
            message = null,
            lastDrawnCard = null,
            canPlayDrawnCard = false
        )

        // Restart the game timer
        startGameTimer()

        // If first player is a bot, start bot turn
        checkAndProcessBotTurn()
    }

    /**
     * Dismiss round summary (used when navigating away).
     */
    fun dismissRoundSummary() {
        _uiState.value = _uiState.value.copy(showRoundSummary = false)
    }

    /**
     * Dismiss final results (used when navigating away).
     */
    fun dismissFinalResults() {
        _uiState.value = _uiState.value.copy(showFinalResults = false)
    }

    /**
     * Clean up when leaving the game.
     */
    override fun onCleared() {
        super.onCleared()
        botTurnJob?.cancel()
        timerJob?.cancel()
    }
}
