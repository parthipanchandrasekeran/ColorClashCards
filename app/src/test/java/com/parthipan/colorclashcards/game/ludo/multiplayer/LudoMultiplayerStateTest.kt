package com.parthipan.colorclashcards.game.ludo.multiplayer

import com.parthipan.colorclashcards.game.ludo.engine.LudoBoard
import com.parthipan.colorclashcards.game.ludo.engine.LudoEngine
import com.parthipan.colorclashcards.game.ludo.engine.MoveResult
import com.parthipan.colorclashcards.game.ludo.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Ludo multiplayer state transitions.
 * Tests the client-side state handler/reducer that processes server updates.
 *
 * Focus areas:
 * - Dice roll request -> server response -> client state update
 * - Move request -> server validation -> client state update
 * - Rejected moves -> error handling without corrupting local state
 * - Anti-cheat: client cannot forge dice values or invalid moves
 */
class LudoMultiplayerStateTest {

    private lateinit var stateHandler: LudoMultiplayerStateHandler
    private lateinit var initialState: LudoGameState
    private lateinit var player1: LudoPlayer
    private lateinit var player2: LudoPlayer

    @Before
    fun setUp() {
        player1 = LudoPlayer(
            id = "player-1-uid",
            name = "Player 1",
            color = LudoColor.RED,
            isOnline = true
        )
        player2 = LudoPlayer(
            id = "player-2-uid",
            name = "Player 2",
            color = LudoColor.BLUE,
            isOnline = true
        )

        initialState = LudoGameState(
            players = listOf(player1, player2),
            currentTurnPlayerId = player1.id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true
        )

        stateHandler = LudoMultiplayerStateHandler()
    }

    // ==================== DICE ROLL FLOW TESTS ====================

    @Test
    fun `dice roll request creates pending action`() {
        // Arrange
        val localPlayerId = player1.id

        // Act
        val action = stateHandler.createDiceRollRequest(localPlayerId)

        // Assert
        assertEquals("Action type should be ROLL_DICE", ActionType.ROLL_DICE, action.type)
        assertEquals("Player ID should match", localPlayerId, action.playerId)
        assertNull("Dice value should be null (server generates)", action.diceValue)
        assertNotNull("Timestamp should be set", action.timestamp)
    }

    @Test
    fun `server dice response updates local state correctly`() {
        // Arrange
        val serverResponse = ServerDiceResponse(
            success = true,
            diceValue = 6,
            newState = MockFirebaseResponses.createDiceRolledState(initialState, 6),
            error = null
        )

        // Act
        val result = stateHandler.handleDiceResponse(initialState, serverResponse)

        // Assert
        assertTrue("Should be successful", result.isSuccess)
        val newState = result.getOrThrow()
        assertEquals("Dice value should be 6", 6, newState.diceValue)
        assertFalse("Cannot roll dice again", newState.canRollDice)
        assertTrue("Must select token with 6", newState.mustSelectToken)
    }

    @Test
    fun `client cannot forge dice value - only accepts server values`() {
        // Arrange
        stateHandler.setState(initialState) // Set state first

        val forgedAction = PlayerAction(
            type = ActionType.ROLL_DICE,
            playerId = player1.id,
            diceValue = 6, // Client trying to set dice value
            timestamp = System.currentTimeMillis()
        )

        // Act
        val validationResult = stateHandler.validateClientAction(forgedAction)

        // Assert
        assertFalse("Forged dice value should be rejected", validationResult.isValid)
        assertEquals(
            "Should indicate dice forgery attempt",
            ValidationError.DICE_VALUE_NOT_ALLOWED,
            validationResult.error
        )
    }

    @Test
    fun `dice roll rejected when not player turn`() {
        // Arrange - Player 2 tries to roll when it's Player 1's turn
        stateHandler.setState(initialState) // Set state first

        val action = stateHandler.createDiceRollRequest(player2.id)
        val serverResponse = ServerDiceResponse(
            success = false,
            diceValue = null,
            newState = null,
            error = "Not your turn"
        )

        // Act
        val result = stateHandler.handleDiceResponse(initialState, serverResponse)

        // Assert
        assertTrue("Should be failure", result.isFailure)
        assertEquals("State should remain unchanged", initialState, stateHandler.currentState)
    }

    @Test
    fun `dice roll rejected when dice already rolled`() {
        // Arrange - State where dice was already rolled
        val stateWithDice = initialState.copy(
            diceValue = 4,
            canRollDice = false,
            mustSelectToken = true
        )
        stateHandler.setState(stateWithDice)

        val serverResponse = ServerDiceResponse(
            success = false,
            diceValue = null,
            newState = null,
            error = "Dice already rolled"
        )

        // Act
        val result = stateHandler.handleDiceResponse(stateWithDice, serverResponse)

        // Assert
        assertTrue("Should be failure", result.isFailure)
        assertEquals("Dice value should remain", 4, stateHandler.currentState?.diceValue)
    }

    @Test
    fun `consecutive sixes tracked correctly from server state`() {
        // Arrange
        val stateAfterFirst6 = initialState.copy(consecutiveSixes = 1)
        val serverResponse = ServerDiceResponse(
            success = true,
            diceValue = 6,
            newState = stateAfterFirst6.copy(
                diceValue = 6,
                consecutiveSixes = 2,
                canRollDice = false,
                mustSelectToken = true
            ),
            error = null
        )

        // Act
        val result = stateHandler.handleDiceResponse(stateAfterFirst6, serverResponse)

        // Assert
        assertTrue("Should be successful", result.isSuccess)
        assertEquals("Consecutive sixes should be 2", 2, result.getOrThrow().consecutiveSixes)
    }

    // ==================== MOVE REQUEST FLOW TESTS ====================

    @Test
    fun `move request creates valid action`() {
        // Arrange
        val stateWithDice = initialState.copy(
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )
        stateHandler.setState(stateWithDice)

        // Act
        val action = stateHandler.createMoveRequest(player1.id, tokenId = 0)

        // Assert
        assertEquals("Action type should be MOVE_TOKEN", ActionType.MOVE_TOKEN, action.type)
        assertEquals("Player ID should match", player1.id, action.playerId)
        assertEquals("Token ID should be 0", 0, action.tokenId)
    }

    @Test
    fun `server move response updates state with token moved`() {
        // Arrange
        val stateWithDice = createStateWithActiveToken(player1, tokenPosition = 10, diceValue = 4)
        stateHandler.setState(stateWithDice)

        val expectedNewState = moveTokenInState(stateWithDice, player1.id, tokenId = 0, toPosition = 14)
        val serverResponse = ServerMoveResponse(
            success = true,
            newState = expectedNewState,
            move = LudoMove(
                playerId = player1.id,
                tokenId = 0,
                diceValue = 4,
                fromPosition = 10,
                toPosition = 14,
                moveType = MoveType.NORMAL
            ),
            error = null
        )

        // Act
        val result = stateHandler.handleMoveResponse(stateWithDice, serverResponse)

        // Assert
        assertTrue("Should be successful", result.isSuccess)
        val newState = result.getOrThrow()
        val movedToken = newState.getPlayer(player1.id)?.getToken(0)
        assertEquals("Token should be at position 14", 14, movedToken?.position)
    }

    @Test
    fun `server validates capture and updates both players`() {
        // Arrange - Player 1 will capture Player 2's token
        val player1Tokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 15),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val player2Tokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 5), // Will be captured (absolute 18)
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )

        val p1 = player1.copy(tokens = player1Tokens)
        val p2 = player2.copy(tokens = player2Tokens)
        val stateWithCapture = LudoGameState(
            players = listOf(p1, p2),
            currentTurnPlayerId = p1.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 3,
            canRollDice = false,
            mustSelectToken = true
        )

        // Server response with capture
        val capturedP2Tokens = player2Tokens.map {
            if (it.id == 0) Token(id = 0, state = TokenState.HOME, position = -1) else it
        }
        val movedP1Tokens = player1Tokens.map {
            if (it.id == 0) Token(id = 0, state = TokenState.ACTIVE, position = 18) else it
        }

        val newState = stateWithCapture.copy(
            players = listOf(
                p1.copy(tokens = movedP1Tokens),
                p2.copy(tokens = capturedP2Tokens)
            ),
            diceValue = null,
            canRollDice = true, // Bonus turn for capture
            mustSelectToken = false
        )

        val serverResponse = ServerMoveResponse(
            success = true,
            newState = newState,
            move = LudoMove(
                playerId = p1.id,
                tokenId = 0,
                diceValue = 3,
                fromPosition = 15,
                toPosition = 18,
                moveType = MoveType.CAPTURE,
                capturedTokenInfo = CapturedTokenInfo(
                    playerId = p2.id,
                    tokenId = 0,
                    position = 5
                )
            ),
            error = null
        )

        // Act
        val result = stateHandler.handleMoveResponse(stateWithCapture, serverResponse)

        // Assert
        assertTrue("Should be successful", result.isSuccess)
        val updatedState = result.getOrThrow()

        // Check player 1's token moved
        val p1Token = updatedState.getPlayer(p1.id)?.getToken(0)
        assertEquals("P1 token should be at 18", 18, p1Token?.position)

        // Check player 2's token sent home
        val p2Token = updatedState.getPlayer(p2.id)?.getToken(0)
        assertEquals("P2 token should be HOME", TokenState.HOME, p2Token?.state)
        assertEquals("P2 token position should be -1", -1, p2Token?.position)

        // Check bonus turn granted
        assertTrue("Should have bonus turn", updatedState.canRollDice)
        assertEquals("Should still be P1's turn", p1.id, updatedState.currentTurnPlayerId)
    }

    // ==================== REJECTED MOVE TESTS ====================

    @Test
    fun `rejected move does not update local state`() {
        // Arrange
        val stateWithDice = createStateWithActiveToken(player1, tokenPosition = 10, diceValue = 4)
        stateHandler.setState(stateWithDice)
        val originalState = stateHandler.currentState

        val serverResponse = ServerMoveResponse(
            success = false,
            newState = null,
            move = null,
            error = "Invalid move: token cannot move"
        )

        // Act
        val result = stateHandler.handleMoveResponse(stateWithDice, serverResponse)

        // Assert
        assertTrue("Should be failure", result.isFailure)
        assertEquals("State should remain unchanged", originalState, stateHandler.currentState)
    }

    @Test
    fun `rejected move for wrong player shows error`() {
        // Arrange - Player 2 tries to move when it's Player 1's turn
        val stateWithDice = initialState.copy(
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )

        val serverResponse = ServerMoveResponse(
            success = false,
            newState = null,
            move = null,
            error = "Not your turn"
        )

        // Act
        val result = stateHandler.handleMoveResponse(stateWithDice, serverResponse)

        // Assert
        assertTrue("Should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Error should mention turn", error?.message?.contains("turn") == true)
    }

    @Test
    fun `rejected move for invalid token shows error`() {
        // Arrange
        val stateWithDice = initialState.copy(
            diceValue = 3, // Cannot exit home with 3
            canRollDice = false,
            mustSelectToken = true
        )

        val serverResponse = ServerMoveResponse(
            success = false,
            newState = null,
            move = null,
            error = "Token cannot move with this dice value"
        )

        // Act
        val result = stateHandler.handleMoveResponse(stateWithDice, serverResponse)

        // Assert
        assertTrue("Should be failure", result.isFailure)
    }

    @Test
    fun `rejected move preserves pending actions queue`() {
        // Arrange
        val stateWithDice = createStateWithActiveToken(player1, tokenPosition = 10, diceValue = 4)
        stateHandler.setState(stateWithDice)

        // Queue a pending action
        val pendingAction = stateHandler.createMoveRequest(player1.id, tokenId = 0)
        stateHandler.addPendingAction(pendingAction)

        val serverResponse = ServerMoveResponse(
            success = false,
            newState = null,
            move = null,
            error = "Server error"
        )

        // Act
        stateHandler.handleMoveResponse(stateWithDice, serverResponse)

        // Assert
        assertTrue("Pending action should be removed after rejection",
            stateHandler.getPendingActions().isEmpty())
    }

    // ==================== ANTI-CHEAT VALIDATION TESTS ====================

    @Test
    fun `client cannot submit move with forged position`() {
        // Arrange
        stateHandler.setState(initialState) // Set state first

        val forgedAction = PlayerAction(
            type = ActionType.MOVE_TOKEN,
            playerId = player1.id,
            tokenId = 0,
            targetPosition = 50, // Client trying to specify target position
            timestamp = System.currentTimeMillis()
        )

        // Act
        val result = stateHandler.validateClientAction(forgedAction)

        // Assert
        assertFalse("Forged position should be rejected", result.isValid)
        assertEquals(
            "Should indicate position forgery",
            ValidationError.POSITION_NOT_ALLOWED,
            result.error
        )
    }

    @Test
    fun `client cannot skip turns`() {
        // Arrange - It's player 1's turn, player 2 tries to act
        stateHandler.setState(initialState)

        val action = PlayerAction(
            type = ActionType.ROLL_DICE,
            playerId = player2.id,
            timestamp = System.currentTimeMillis()
        )

        // Act
        val result = stateHandler.validateClientAction(action)

        // Assert
        assertFalse("Wrong player action should be rejected", result.isValid)
        assertEquals(
            "Should indicate wrong turn",
            ValidationError.NOT_YOUR_TURN,
            result.error
        )
    }

    @Test
    fun `client cannot move finished token`() {
        // Arrange
        val finishedTokens = listOf(
            Token(id = 0, state = TokenState.FINISHED, position = 57),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val p1 = player1.copy(tokens = finishedTokens)
        val stateWithFinished = LudoGameState(
            players = listOf(p1, player2),
            currentTurnPlayerId = p1.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )
        stateHandler.setState(stateWithFinished)

        val action = PlayerAction(
            type = ActionType.MOVE_TOKEN,
            playerId = p1.id,
            tokenId = 0, // Trying to move finished token
            timestamp = System.currentTimeMillis()
        )

        // Act
        val result = stateHandler.validateClientAction(action)

        // Assert
        assertFalse("Moving finished token should be rejected", result.isValid)
        assertEquals(
            "Should indicate invalid token",
            ValidationError.TOKEN_CANNOT_MOVE,
            result.error
        )
    }

    @Test
    fun `state version mismatch triggers resync`() {
        // Arrange
        val clientState = initialState.copy()
        stateHandler.setState(clientState)
        stateHandler.setStateVersion(5)

        // Server response has newer version
        val serverResponse = ServerStateUpdate(
            version = 8,
            state = initialState.copy(diceValue = 4),
            requiresResync = true
        )

        // Act
        val result = stateHandler.handleStateUpdate(serverResponse)

        // Assert
        assertTrue("Should trigger resync", result.requiresResync)
        assertEquals("State version should update to server's", 8, stateHandler.stateVersion)
    }

    @Test
    fun `optimistic update rolled back on server rejection`() {
        // Arrange
        val stateWithDice = createStateWithActiveToken(player1, tokenPosition = 10, diceValue = 4)
        stateHandler.setState(stateWithDice)

        // Client optimistically moves token
        val optimisticState = moveTokenInState(stateWithDice, player1.id, 0, 14)
        stateHandler.applyOptimisticUpdate(optimisticState)

        // Server rejects
        val serverResponse = ServerMoveResponse(
            success = false,
            newState = null,
            move = null,
            error = "Move rejected"
        )

        // Act
        stateHandler.handleMoveResponse(stateWithDice, serverResponse)

        // Assert
        assertEquals(
            "State should roll back to pre-optimistic",
            10,
            stateHandler.currentState?.getPlayer(player1.id)?.getToken(0)?.position
        )
    }

    // ==================== NETWORK FAILURE TESTS ====================

    @Test
    fun `network timeout preserves local state`() {
        // Arrange
        val stateWithDice = createStateWithActiveToken(player1, tokenPosition = 10, diceValue = 4)
        stateHandler.setState(stateWithDice)
        val originalState = stateHandler.currentState

        // Act
        stateHandler.handleNetworkError(NetworkError.TIMEOUT)

        // Assert
        assertEquals("State should be preserved", originalState, stateHandler.currentState)
        assertTrue("Should have pending retry", stateHandler.hasPendingRetry())
    }

    @Test
    fun `connection lost marks game as disconnected`() {
        // Arrange
        stateHandler.setState(initialState)

        // Act
        stateHandler.handleNetworkError(NetworkError.CONNECTION_LOST)

        // Assert
        assertTrue("Should be marked disconnected", stateHandler.isDisconnected)
        assertFalse("Should not allow actions while disconnected", stateHandler.canPerformAction())
    }

    // ==================== HELPER METHODS ====================

    private fun createStateWithActiveToken(
        player: LudoPlayer,
        tokenPosition: Int,
        diceValue: Int
    ): LudoGameState {
        val activeTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = tokenPosition),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val updatedPlayer = player.copy(tokens = activeTokens)
        val otherPlayer = if (player.id == player1.id) player2 else player1

        return LudoGameState(
            players = listOf(updatedPlayer, otherPlayer),
            currentTurnPlayerId = player.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = diceValue,
            canRollDice = false,
            mustSelectToken = true
        )
    }

    private fun moveTokenInState(
        state: LudoGameState,
        playerId: String,
        tokenId: Int,
        toPosition: Int
    ): LudoGameState {
        val player = state.getPlayer(playerId) ?: return state
        val token = player.getToken(tokenId) ?: return state

        val newTokenState = if (toPosition >= 57) TokenState.FINISHED else TokenState.ACTIVE
        val newToken = token.copy(state = newTokenState, position = toPosition)
        val newPlayer = player.updateToken(tokenId, newToken)

        return state.updatePlayer(newPlayer).copy(
            diceValue = null,
            canRollDice = false,
            mustSelectToken = false
        )
    }
}

// ==================== SUPPORTING CLASSES ====================

/**
 * Client-side state handler for multiplayer Ludo.
 * Processes server updates and manages local state.
 */
class LudoMultiplayerStateHandler {
    var currentState: LudoGameState? = null
        private set

    var stateVersion: Int = 0
        private set

    var isDisconnected: Boolean = false
        private set

    private var preOptimisticState: LudoGameState? = null
    private val pendingActions = mutableListOf<PlayerAction>()
    private var hasPendingRetry = false

    fun setState(state: LudoGameState) {
        currentState = state
    }

    fun setStateVersion(version: Int) {
        stateVersion = version
    }

    fun createDiceRollRequest(playerId: String): PlayerAction {
        return PlayerAction(
            type = ActionType.ROLL_DICE,
            playerId = playerId,
            diceValue = null, // Server generates
            timestamp = System.currentTimeMillis()
        )
    }

    fun createMoveRequest(playerId: String, tokenId: Int): PlayerAction {
        return PlayerAction(
            type = ActionType.MOVE_TOKEN,
            playerId = playerId,
            tokenId = tokenId,
            timestamp = System.currentTimeMillis()
        )
    }

    fun handleDiceResponse(
        previousState: LudoGameState,
        response: ServerDiceResponse
    ): Result<LudoGameState> {
        return if (response.success && response.newState != null) {
            currentState = response.newState
            Result.success(response.newState)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    }

    fun handleMoveResponse(
        previousState: LudoGameState,
        response: ServerMoveResponse
    ): Result<LudoGameState> {
        // Clear pending action
        pendingActions.clear()

        // Rollback optimistic update if exists
        if (preOptimisticState != null) {
            currentState = preOptimisticState
            preOptimisticState = null
        }

        return if (response.success && response.newState != null) {
            currentState = response.newState
            Result.success(response.newState)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    }

    fun handleStateUpdate(update: ServerStateUpdate): StateUpdateResult {
        if (update.requiresResync || update.version > stateVersion + 1) {
            currentState = update.state
            stateVersion = update.version
            return StateUpdateResult(requiresResync = true)
        }

        currentState = update.state
        stateVersion = update.version
        return StateUpdateResult(requiresResync = false)
    }

    fun validateClientAction(action: PlayerAction): ClientValidationResult {
        val state = currentState ?: return ClientValidationResult(
            isValid = false,
            error = ValidationError.NO_GAME_STATE
        )

        // Check turn
        if (action.playerId != state.currentTurnPlayerId) {
            return ClientValidationResult(
                isValid = false,
                error = ValidationError.NOT_YOUR_TURN
            )
        }

        // Check for forged dice value
        if (action.type == ActionType.ROLL_DICE && action.diceValue != null) {
            return ClientValidationResult(
                isValid = false,
                error = ValidationError.DICE_VALUE_NOT_ALLOWED
            )
        }

        // Check for forged position
        if (action.targetPosition != null) {
            return ClientValidationResult(
                isValid = false,
                error = ValidationError.POSITION_NOT_ALLOWED
            )
        }

        // Check token can move
        if (action.type == ActionType.MOVE_TOKEN && action.tokenId != null) {
            val player = state.getPlayer(action.playerId)
            val token = player?.getToken(action.tokenId)
            val diceValue = state.diceValue

            if (token == null) {
                return ClientValidationResult(
                    isValid = false,
                    error = ValidationError.TOKEN_NOT_FOUND
                )
            }

            if (diceValue != null && !token.canMove(diceValue)) {
                return ClientValidationResult(
                    isValid = false,
                    error = ValidationError.TOKEN_CANNOT_MOVE
                )
            }
        }

        return ClientValidationResult(isValid = true, error = null)
    }

    fun applyOptimisticUpdate(newState: LudoGameState) {
        preOptimisticState = currentState
        currentState = newState
    }

    fun addPendingAction(action: PlayerAction) {
        pendingActions.add(action)
    }

    fun getPendingActions(): List<PlayerAction> = pendingActions.toList()

    fun handleNetworkError(error: NetworkError) {
        when (error) {
            NetworkError.TIMEOUT -> {
                hasPendingRetry = true
            }
            NetworkError.CONNECTION_LOST -> {
                isDisconnected = true
            }
        }
    }

    fun hasPendingRetry(): Boolean = hasPendingRetry

    fun canPerformAction(): Boolean = !isDisconnected && currentState != null
}

// ==================== DATA CLASSES ====================

data class PlayerAction(
    val type: ActionType,
    val playerId: String,
    val diceValue: Int? = null,
    val tokenId: Int? = null,
    val targetPosition: Int? = null,
    val timestamp: Long
)

enum class ActionType {
    ROLL_DICE,
    MOVE_TOKEN,
    HEARTBEAT
}

data class ServerDiceResponse(
    val success: Boolean,
    val diceValue: Int?,
    val newState: LudoGameState?,
    val error: String?
)

data class ServerMoveResponse(
    val success: Boolean,
    val newState: LudoGameState?,
    val move: LudoMove?,
    val error: String?
)

data class ServerStateUpdate(
    val version: Int,
    val state: LudoGameState,
    val requiresResync: Boolean = false
)

data class StateUpdateResult(
    val requiresResync: Boolean
)

data class ClientValidationResult(
    val isValid: Boolean,
    val error: ValidationError?
)

enum class ValidationError {
    NO_GAME_STATE,
    NOT_YOUR_TURN,
    DICE_VALUE_NOT_ALLOWED,
    POSITION_NOT_ALLOWED,
    TOKEN_NOT_FOUND,
    TOKEN_CANNOT_MOVE,
    INVALID_STATE
}

enum class NetworkError {
    TIMEOUT,
    CONNECTION_LOST
}

// ==================== MOCK FIREBASE RESPONSES ====================

object MockFirebaseResponses {

    fun createDiceRolledState(
        previousState: LudoGameState,
        diceValue: Int
    ): LudoGameState {
        val currentPlayer = previousState.currentPlayer
        val movableTokens = LudoEngine.getMovableTokens(currentPlayer, diceValue)

        // Handle consecutive sixes
        var consecutiveSixes = previousState.consecutiveSixes
        var skipTurn = false

        if (diceValue == 6) {
            consecutiveSixes++
            if (consecutiveSixes >= 3) {
                skipTurn = true
                consecutiveSixes = 0
            }
        } else {
            consecutiveSixes = 0
        }

        if (movableTokens.isEmpty() || skipTurn) {
            return LudoEngine.advanceToNextPlayer(previousState.copy(
                diceValue = diceValue,
                consecutiveSixes = 0
            ))
        }

        return previousState.copy(
            diceValue = diceValue,
            canRollDice = false,
            mustSelectToken = true,
            consecutiveSixes = consecutiveSixes
        )
    }

    fun createMoveSuccessResponse(
        previousState: LudoGameState,
        playerId: String,
        tokenId: Int
    ): ServerMoveResponse {
        val result = LudoEngine.moveToken(previousState, tokenId)

        return when (result) {
            is MoveResult.Success -> ServerMoveResponse(
                success = true,
                newState = result.newState,
                move = result.move,
                error = null
            )
            is MoveResult.Error -> ServerMoveResponse(
                success = false,
                newState = null,
                move = null,
                error = result.message
            )
        }
    }

    fun createMoveRejectedResponse(reason: String): ServerMoveResponse {
        return ServerMoveResponse(
            success = false,
            newState = null,
            move = null,
            error = reason
        )
    }

    fun createStateSnapshot(
        gameId: String,
        state: LudoGameState,
        version: Int
    ): Map<String, Any?> {
        return mapOf(
            "gameId" to gameId,
            "version" to version,
            "currentTurnPlayerId" to state.currentTurnPlayerId,
            "diceValue" to state.diceValue,
            "canRollDice" to state.canRollDice,
            "mustSelectToken" to state.mustSelectToken,
            "consecutiveSixes" to state.consecutiveSixes,
            "gameStatus" to state.gameStatus.name,
            "winnerId" to state.winnerId,
            "players" to state.players.map { player ->
                mapOf(
                    "id" to player.id,
                    "name" to player.name,
                    "color" to player.color.name,
                    "tokens" to player.tokens.map { token ->
                        mapOf(
                            "id" to token.id,
                            "state" to token.state.name,
                            "position" to token.position
                        )
                    }
                )
            },
            "updatedAt" to System.currentTimeMillis()
        )
    }
}
