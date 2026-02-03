package com.parthipan.colorclashcards.ui.ludo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parthipan.colorclashcards.game.ludo.*
import com.parthipan.colorclashcards.game.ludo.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Ludo game screen.
 */
data class LudoUiState(
    val gameState: LudoGameState? = null,
    val isLoading: Boolean = false,
    val isRollingDice: Boolean = false,
    val isMovingToken: Boolean = false,
    val diceValue: Int? = null,
    val canRollDice: Boolean = false,
    val mustSelectToken: Boolean = false,
    val movableTokenIds: List<Int> = emptyList(),
    val lastMove: LudoMove? = null,
    val message: String? = null,
    val error: String? = null,
    val showWinDialog: Boolean = false,
    val winnerName: String? = null
)

/**
 * ViewModel for Ludo game logic.
 * Handles dice rolls, token moves, and turn management via Firebase Cloud Functions.
 */
class LudoGameViewModel : ViewModel() {

    private val repository = LudoRepository()

    private val _uiState = MutableStateFlow(LudoUiState())
    val uiState: StateFlow<LudoUiState> = _uiState.asStateFlow()

    private var roomId: String = ""
    private var localPlayerId: String = ""

    /**
     * Initialize the game with room and player info.
     */
    fun initialize(roomId: String, playerId: String, gameState: LudoGameState) {
        this.roomId = roomId
        this.localPlayerId = playerId

        val isMyTurn = gameState.currentTurnPlayerId == playerId
        val canRoll = isMyTurn && gameState.canRollDice

        _uiState.value = LudoUiState(
            gameState = gameState,
            canRollDice = canRoll,
            mustSelectToken = isMyTurn && gameState.mustSelectToken,
            diceValue = gameState.diceValue
        )

        // Calculate movable tokens if needed
        if (isMyTurn && gameState.mustSelectToken && gameState.diceValue != null) {
            updateMovableTokens(gameState.diceValue)
        }
    }

    /**
     * Roll the dice. Only works if it's the player's turn and they can roll.
     */
    fun rollDice() {
        val state = _uiState.value
        if (!state.canRollDice || state.isRollingDice) return

        viewModelScope.launch {
            _uiState.value = state.copy(
                isRollingDice = true,
                error = null,
                message = null
            )

            repository.rollDice(roomId).fold(
                onSuccess = { result ->
                    handleDiceRollResult(result)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRollingDice = false,
                        error = (error as? LudoGameException)?.message ?: "Failed to roll dice"
                    )
                }
            )
        }
    }

    /**
     * Handle the result of a dice roll.
     */
    private fun handleDiceRollResult(result: DiceRollResult) {
        val currentState = _uiState.value

        val message = when {
            result.skipTurn -> result.reason ?: "Turn skipped"
            result.canRollAgain -> "Rolled a 6! Roll again after moving."
            else -> "Rolled ${result.diceValue}"
        }

        _uiState.value = currentState.copy(
            isRollingDice = false,
            diceValue = result.diceValue,
            canRollDice = result.canRollAgain && !result.mustSelectToken,
            mustSelectToken = result.mustSelectToken,
            message = message
        )

        // Calculate which tokens can move
        if (result.mustSelectToken) {
            updateMovableTokens(result.diceValue)
        } else if (result.skipTurn) {
            // Turn was skipped, will be updated by game state listener
            clearTurnState()
        }
    }

    /**
     * Move a token. Only works if it's the player's turn and they must select a token.
     */
    fun moveToken(tokenId: Int) {
        val state = _uiState.value
        if (!state.mustSelectToken || state.isMovingToken) return
        if (tokenId !in state.movableTokenIds) return

        viewModelScope.launch {
            _uiState.value = state.copy(
                isMovingToken = true,
                error = null
            )

            repository.moveToken(roomId, tokenId).fold(
                onSuccess = { result ->
                    handleMoveResult(result)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isMovingToken = false,
                        error = (error as? LudoGameException)?.message ?: "Failed to move token"
                    )
                }
            )
        }
    }

    /**
     * Handle the result of a token move.
     */
    private fun handleMoveResult(result: MoveTokenResult) {
        val message = buildMoveMessage(result)

        _uiState.value = _uiState.value.copy(
            isMovingToken = false,
            lastMove = result.move,
            message = message,
            diceValue = null,
            mustSelectToken = false,
            movableTokenIds = emptyList(),
            canRollDice = result.bonusTurn,
            showWinDialog = result.hasWon
        )

        if (result.hasWon) {
            _uiState.value = _uiState.value.copy(
                winnerName = "You",
                canRollDice = false
            )
        } else if (!result.bonusTurn) {
            // Turn ended, will be updated by game state listener
            clearTurnState()
        }
    }

    /**
     * Build a message describing the move.
     */
    private fun buildMoveMessage(result: MoveTokenResult): String {
        val move = result.move
        return when (move.moveType) {
            MoveType.EXIT_HOME -> "Token moved out of home!"
            MoveType.FINISH -> "Token reached the finish!"
            MoveType.CAPTURE -> {
                val bonus = if (result.bonusTurn) " Bonus turn!" else ""
                "Captured an opponent's token!$bonus"
            }
            MoveType.NORMAL, MoveType.ENTER_HOME_STRETCH -> {
                if (result.bonusTurn) {
                    result.bonusReason ?: "Bonus turn!"
                } else {
                    "Moved to position ${move.toPosition}"
                }
            }
            MoveType.SKIP -> "Turn skipped"
        }
    }

    /**
     * Update the list of movable tokens based on current dice value.
     */
    private fun updateMovableTokens(diceValue: Int) {
        val gameState = _uiState.value.gameState ?: return
        val player = gameState.getPlayer(localPlayerId) ?: return

        val movable = player.getMovableTokens(diceValue).map { it.id }

        _uiState.value = _uiState.value.copy(
            movableTokenIds = movable
        )

        // If only one token can move, auto-select it
        if (movable.size == 1) {
            moveToken(movable.first())
        }
    }

    /**
     * Clear turn-related state when turn ends.
     */
    private fun clearTurnState() {
        _uiState.value = _uiState.value.copy(
            diceValue = null,
            canRollDice = false,
            mustSelectToken = false,
            movableTokenIds = emptyList()
        )
    }

    /**
     * Update game state from external source (e.g., Firestore listener).
     * Called when the game state changes from another player's action.
     */
    fun onGameStateUpdated(newState: LudoGameState) {
        val isMyTurn = newState.currentTurnPlayerId == localPlayerId
        val canRoll = isMyTurn && newState.canRollDice

        _uiState.value = _uiState.value.copy(
            gameState = newState,
            canRollDice = canRoll,
            mustSelectToken = isMyTurn && newState.mustSelectToken,
            diceValue = if (isMyTurn) newState.diceValue else null,
            lastMove = newState.lastMove,
            showWinDialog = newState.winnerId != null,
            winnerName = newState.winner?.name
        )

        // Calculate movable tokens if it's my turn and I need to select
        if (isMyTurn && newState.mustSelectToken && newState.diceValue != null) {
            updateMovableTokens(newState.diceValue)
        } else if (!isMyTurn) {
            clearTurnState()
        }
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

    /**
     * Dismiss win dialog.
     */
    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }
}
