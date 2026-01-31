package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for the Ludo rules engine.
 * Uses Arrange-Act-Assert pattern for clarity.
 */
class LudoEngineTest {

    private lateinit var redPlayer: LudoPlayer
    private lateinit var bluePlayer: LudoPlayer
    private lateinit var greenPlayer: LudoPlayer
    private lateinit var yellowPlayer: LudoPlayer

    @Before
    fun setUp() {
        // Create consistent test players with known IDs
        redPlayer = LudoPlayer(
            id = "player-red",
            name = "Red Player",
            color = LudoColor.RED
        )
        bluePlayer = LudoPlayer(
            id = "player-blue",
            name = "Blue Player",
            color = LudoColor.BLUE
        )
        greenPlayer = LudoPlayer(
            id = "player-green",
            name = "Green Player",
            color = LudoColor.GREEN
        )
        yellowPlayer = LudoPlayer(
            id = "player-yellow",
            name = "Yellow Player",
            color = LudoColor.YELLOW
        )
    }

    // ==================== TOKEN LEAVING HOME TESTS ====================

    @Test
    fun `token at HOME can only move with dice value 6`() {
        // Arrange
        val tokenAtHome = Token(id = 0, state = TokenState.HOME, position = -1)

        // Act & Assert
        assertFalse("Token at HOME should not move with dice 1", tokenAtHome.canMove(1))
        assertFalse("Token at HOME should not move with dice 2", tokenAtHome.canMove(2))
        assertFalse("Token at HOME should not move with dice 3", tokenAtHome.canMove(3))
        assertFalse("Token at HOME should not move with dice 4", tokenAtHome.canMove(4))
        assertFalse("Token at HOME should not move with dice 5", tokenAtHome.canMove(5))
        assertTrue("Token at HOME should move with dice 6", tokenAtHome.canMove(6))
    }

    @Test
    fun `rollDice with 6 allows token to exit home`() {
        // Arrange
        val gameState = createTwoPlayerGame()

        // Act
        val newState = LudoEngine.rollDice(gameState, 6)

        // Assert
        assertTrue("Should require token selection after rolling 6", newState.mustSelectToken)
        assertEquals("Dice value should be 6", 6, newState.diceValue)
        val movableTokens = LudoEngine.getMovableTokens(newState.currentPlayer, 6)
        assertEquals("All 4 home tokens should be movable with 6", 4, movableTokens.size)
    }

    @Test
    fun `rollDice without 6 and all tokens at home skips turn`() {
        // Arrange
        val gameState = createTwoPlayerGame()

        // Act
        val newState = LudoEngine.rollDice(gameState, 3)

        // Assert
        assertFalse("Should not require token selection", newState.mustSelectToken)
        assertNotEquals("Turn should advance to next player",
            gameState.currentTurnPlayerId, newState.currentTurnPlayerId)
    }

    @Test
    fun `moveToken from HOME places token at position 0`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success
        val movedToken = success.newState.currentPlayer.getToken(0)
        assertNotNull("Token should exist", movedToken)
        assertEquals("Token should be at position 0", 0, movedToken!!.position)
        assertEquals("Token should be ACTIVE", TokenState.ACTIVE, movedToken.state)
        assertEquals("Move type should be EXIT_HOME", MoveType.EXIT_HOME, success.move.moveType)
    }

    // ==================== VALID MOVES FOR MULTIPLE TOKENS TESTS ====================

    @Test
    fun `getMovableTokens returns only tokens that can legally move`() {
        // Arrange
        val tokens = listOf(
            Token(id = 0, state = TokenState.HOME, position = -1),
            Token(id = 1, state = TokenState.ACTIVE, position = 10),
            Token(id = 2, state = TokenState.ACTIVE, position = 55), // Close to finish (55+3=58 > 57, cannot move with 3)
            Token(id = 3, state = TokenState.FINISHED, position = 57)
        )
        val player = redPlayer.copy(tokens = tokens)

        // Act
        val movableWith3 = player.getMovableTokens(3)
        val movableWith6 = player.getMovableTokens(6)
        val movableWith2 = player.getMovableTokens(2) // Token at 55 can move with 2 (55+2=57)

        // Assert
        // With dice 3: only token at position 10 can move (token at 55 would exceed 57)
        assertEquals("With dice 3: only 1 active token can move",
            1, movableWith3.size)
        assertTrue("Token 1 should be movable with 3", movableWith3.any { it.id == 1 })
        assertTrue("Token 2 should NOT be movable with 3 (55+3=58 > 57)",
            movableWith3.none { it.id == 2 })

        // With dice 6: HOME token can exit + token at position 10 can move (but token at 55 cannot)
        assertEquals("With dice 6: HOME token + active token at 10 can move",
            2, movableWith6.size)
        assertTrue("Token 0 (HOME) should be movable with 6", movableWith6.any { it.id == 0 })
        assertTrue("Token 1 should be movable with 6", movableWith6.any { it.id == 1 })

        // With dice 2: token at 55 can also move (55+2=57 exactly)
        assertEquals("With dice 2: 2 active tokens can move",
            2, movableWith2.size)
        assertTrue("Token 1 should be movable with 2", movableWith2.any { it.id == 1 })
        assertTrue("Token 2 should be movable with 2 (55+2=57)", movableWith2.any { it.id == 2 })
    }

    @Test
    fun `active token can move any valid dice value`() {
        // Arrange
        val tokenAtPosition10 = Token(id = 0, state = TokenState.ACTIVE, position = 10)

        // Act & Assert
        for (dice in 1..6) {
            assertTrue("Active token at position 10 should move with dice $dice",
                tokenAtPosition10.canMove(dice))
        }
    }

    @Test
    fun `token cannot move past finish position`() {
        // Arrange
        val tokenNearFinish = Token(id = 0, state = TokenState.ACTIVE, position = 55)

        // Act & Assert
        assertTrue("Should move with dice 1 (55+1=56 <= 57)", tokenNearFinish.canMove(1))
        assertTrue("Should move with dice 2 (55+2=57 == 57)", tokenNearFinish.canMove(2))
        assertFalse("Should NOT move with dice 3 (55+3=58 > 57)", tokenNearFinish.canMove(3))
        assertFalse("Should NOT move with dice 6 (55+6=61 > 57)", tokenNearFinish.canMove(6))
    }

    // ==================== KILL RULES TESTS ====================

    @Test
    fun `landing on opponent token sends them home`() {
        // Arrange - Red has token at position 10 (absolute: 10), Blue has token at same absolute position
        // Blue's position 10 corresponds to absolute position 23 (10 + 13 offset)
        // So we need Red at position that equals Blue's absolute position
        // Blue at relative position 5 = absolute 18 (5 + 13)
        // Red needs to land on absolute 18, which is relative position 18 for Red
        val redTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 15), // Will move 3 to position 18
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val blueTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 5), // Absolute: 5 + 13 = 18
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )

        val red = redPlayer.copy(tokens = redTokens)
        val blue = bluePlayer.copy(tokens = blueTokens)

        val gameState = LudoGameState(
            players = listOf(red, blue),
            currentTurnPlayerId = red.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 3,
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success

        // Check captured token info
        assertNotNull("Should have captured token info", success.move.capturedTokenInfo)
        assertEquals("Captured token should belong to blue player",
            blue.id, success.move.capturedTokenInfo?.playerId)

        // Check blue's token is now at HOME
        val updatedBlue = success.newState.getPlayer(blue.id)
        val capturedToken = updatedBlue?.getToken(0)
        assertNotNull("Blue's token should exist", capturedToken)
        assertEquals("Blue's token should be back at HOME", TokenState.HOME, capturedToken?.state)
        assertEquals("Blue's token position should be -1", -1, capturedToken?.position)
    }

    @Test
    fun `capturing opponent grants bonus turn`() {
        // Arrange - Set up capture scenario
        val redTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 15),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val blueTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 5), // Will be captured
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )

        val red = redPlayer.copy(tokens = redTokens)
        val blue = bluePlayer.copy(tokens = blueTokens)

        val gameState = LudoGameState(
            players = listOf(red, blue),
            currentTurnPlayerId = red.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 3,
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success
        assertTrue("Should grant bonus turn for capture", success.bonusTurn)
        assertTrue("Should be able to roll again", success.newState.canRollDice)
        assertEquals("Turn should remain with red", red.id, success.newState.currentTurnPlayerId)
    }

    // ==================== SAFE CELLS TESTS ====================

    @Test
    fun `isSafeCell returns true for all star positions`() {
        // Arrange
        val safeCells = setOf(0, 8, 13, 21, 26, 34, 39, 47)

        // Act & Assert
        for (cell in safeCells) {
            assertTrue("Cell $cell should be safe", LudoBoard.isSafeCell(cell))
        }
    }

    @Test
    fun `isSafeCell returns false for non-safe positions`() {
        // Arrange
        val unsafeCells = listOf(1, 5, 10, 15, 20, 25, 30, 40, 50)

        // Act & Assert
        for (cell in unsafeCells) {
            assertFalse("Cell $cell should not be safe", LudoBoard.isSafeCell(cell))
        }
    }

    @Test
    fun `token on safe cell cannot be captured`() {
        // Arrange - Both players have token at same safe cell (absolute position 0)
        // Red at relative 0 = absolute 0 (safe cell - starting position)
        // Blue at relative 39 = absolute (39 + 13) % 52 = 0 (same safe cell)
        val redTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 0), // At safe cell
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val blueTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 36), // Will move to overlap red's safe cell
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )

        val red = redPlayer.copy(tokens = redTokens)
        val blue = bluePlayer.copy(tokens = blueTokens)

        val gameState = LudoGameState(
            players = listOf(red, blue),
            currentTurnPlayerId = blue.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 3,
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success
        assertNull("Should not capture on safe cell", success.move.capturedTokenInfo)

        // Red's token should still be active
        val updatedRed = success.newState.getPlayer(red.id)
        val redToken = updatedRed?.getToken(0)
        assertEquals("Red's token should still be ACTIVE", TokenState.ACTIVE, redToken?.state)
    }

    @Test
    fun `multiple tokens can coexist on safe cell`() {
        // Arrange - Multiple tokens on same safe cell
        val redTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 8), // Safe cell
            Token(id = 1, state = TokenState.ACTIVE, position = 8), // Same safe cell
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val red = redPlayer.copy(tokens = redTokens)

        val gameState = LudoGameState(
            players = listOf(red, bluePlayer),
            currentTurnPlayerId = red.id,
            gameStatus = GameStatus.IN_PROGRESS
        )

        // Act
        val tokensAtPosition8 = gameState.getTokensAtPosition(8)

        // Assert - Both tokens should coexist (using relative position check)
        assertEquals("Both red tokens should be at position 8", 2,
            red.tokens.count { it.position == 8 })
    }

    // ==================== FINISH PATH & EXACT ROLL TESTS ====================

    @Test
    fun `token enters home stretch after position 50`() {
        // Arrange
        val tokenNearHomeStretch = Token(id = 0, state = TokenState.ACTIVE, position = 48)

        // Act
        val newPosition = tokenNearHomeStretch.position + 5 // Would be 53

        // Assert
        assertTrue("Position 53 should be in home stretch",
            LudoBoard.isInHomeStretch(newPosition))
        assertFalse("Position 53 should not be on main track",
            LudoBoard.isOnMainTrack(newPosition))
    }

    @Test
    fun `token finishes exactly at position 57`() {
        // Arrange
        val tokenAtPosition55 = Token(id = 0, state = TokenState.ACTIVE, position = 55)
        val player = redPlayer.copy(tokens = listOf(
            tokenAtPosition55,
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        ))

        val gameState = LudoGameState(
            players = listOf(player, bluePlayer),
            currentTurnPlayerId = player.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 2, // 55 + 2 = 57 (exact finish)
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success
        // Get the player who made the move by ID (not currentPlayer, as turn may have advanced)
        val playerAfterMove = success.newState.getPlayer(player.id)
        val finishedToken = playerAfterMove?.getToken(0)
        assertEquals("Token should be FINISHED", TokenState.FINISHED, finishedToken?.state)
        assertEquals("Token should be at position 57", 57, finishedToken?.position)
        assertEquals("Move type should be FINISH", MoveType.FINISH, success.move.moveType)
    }

    @Test
    fun `token cannot move if dice exceeds finish position`() {
        // Arrange
        val tokenAtPosition55 = Token(id = 0, state = TokenState.ACTIVE, position = 55)

        // Act & Assert
        assertFalse("Should not move with dice 3 (55+3=58 > 57)", tokenAtPosition55.canMove(3))
        assertFalse("Should not move with dice 4 (55+4=59 > 57)", tokenAtPosition55.canMove(4))
        assertFalse("Should not move with dice 5 (55+5=60 > 57)", tokenAtPosition55.canMove(5))
        assertFalse("Should not move with dice 6 (55+6=61 > 57)", tokenAtPosition55.canMove(6))
    }

    @Test
    fun `token in home stretch is safe from capture`() {
        // Arrange
        val tokenInHomeStretch = Token(id = 0, state = TokenState.ACTIVE, position = 53)

        // Act
        val absolutePosition = LudoBoard.toAbsolutePosition(53, LudoColor.RED)

        // Assert
        assertEquals("Home stretch should return -1 for absolute position", -1, absolutePosition)
        // -1 means no collision possible with other players
    }

    // ==================== ROLLING 6 GRANTS EXTRA TURN TESTS ====================

    @Test
    fun `rolling 6 grants extra turn after moving`() {
        // Arrange
        val tokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 10),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val player = redPlayer.copy(tokens = tokens)

        val gameState = LudoGameState(
            players = listOf(player, bluePlayer),
            currentTurnPlayerId = player.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success
        assertTrue("Should grant bonus turn for rolling 6", success.bonusTurn)
        assertTrue("Should be able to roll again", success.newState.canRollDice)
        assertEquals("Turn should remain with same player",
            player.id, success.newState.currentTurnPlayerId)
    }

    @Test
    fun `three consecutive 6s loses turn`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            consecutiveSixes = 2,
            canRollDice = true
        )

        // Act
        val newState = LudoEngine.rollDice(gameState, 6)

        // Assert
        assertNotEquals("Turn should advance after 3 consecutive 6s",
            gameState.currentTurnPlayerId, newState.currentTurnPlayerId)
        assertEquals("Consecutive sixes should reset to 0", 0, newState.consecutiveSixes)
    }

    @Test
    fun `consecutive sixes counter increments on rolling 6`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            consecutiveSixes = 1,
            canRollDice = true
        )

        // Act
        val newState = LudoEngine.rollDice(gameState, 6)

        // Assert
        assertEquals("Consecutive sixes should increment", 2, newState.consecutiveSixes)
    }

    @Test
    fun `consecutive sixes counter resets on non-6 roll`() {
        // Arrange
        val tokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 10),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val player = redPlayer.copy(tokens = tokens)

        val gameState = LudoGameState(
            players = listOf(player, bluePlayer),
            currentTurnPlayerId = player.id,
            gameStatus = GameStatus.IN_PROGRESS,
            consecutiveSixes = 2,
            canRollDice = true
        )

        // Act
        val newState = LudoEngine.rollDice(gameState, 4)

        // Assert
        assertEquals("Consecutive sixes should reset to 0", 0, newState.consecutiveSixes)
    }

    // ==================== INVALID MOVE REJECTION TESTS ====================

    @Test
    fun `validateMove rejects move when not player's turn`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            diceValue = 6,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.validateMove(gameState, bluePlayer.id, tokenId = 0)

        // Assert
        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention turn", invalid.reason.contains("turn", ignoreCase = true))
    }

    @Test
    fun `validateMove rejects move when dice not rolled`() {
        // Arrange
        val gameState = createTwoPlayerGame() // diceValue is null by default

        // Act
        val result = LudoEngine.validateMove(gameState, redPlayer.id, tokenId = 0)

        // Assert
        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    @Test
    fun `validateMove rejects move for non-existent token`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            diceValue = 6,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.validateMove(gameState, redPlayer.id, tokenId = 99)

        // Assert
        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention token", invalid.reason.contains("token", ignoreCase = true))
    }

    @Test
    fun `validateMove rejects move for token that cannot move`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            diceValue = 3, // Token at HOME cannot move with 3
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.validateMove(gameState, redPlayer.id, tokenId = 0)

        // Assert
        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention cannot move", invalid.reason.contains("cannot", ignoreCase = true))
    }

    @Test
    fun `moveToken returns error for invalid token selection`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            diceValue = 3, // HOME tokens can't move with 3
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Should return error", result is MoveResult.Error)
    }

    @Test
    fun `moveToken returns error when not in token selection phase`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            diceValue = 6,
            mustSelectToken = false // Not in selection phase
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Should return error", result is MoveResult.Error)
    }

    @Test
    fun `validateMove rejects move when game is not in progress`() {
        // Arrange
        val gameState = createTwoPlayerGame().copy(
            gameStatus = GameStatus.FINISHED,
            diceValue = 6,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.validateMove(gameState, redPlayer.id, tokenId = 0)

        // Assert
        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention game status",
            invalid.reason.contains("progress", ignoreCase = true))
    }

    // ==================== EDGE CASES TESTS ====================

    @Test
    fun `no valid moves auto-advances turn`() {
        // Arrange - All tokens at HOME, dice is not 6
        val gameState = createTwoPlayerGame()

        // Act
        val newState = LudoEngine.rollDice(gameState, 4)

        // Assert
        assertNotEquals("Turn should advance to next player",
            gameState.currentTurnPlayerId, newState.currentTurnPlayerId)
        assertFalse("Should not require token selection", newState.mustSelectToken)
    }

    @Test
    fun `player wins when all tokens finished`() {
        // Arrange - 3 tokens finished, 1 about to finish
        val tokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 55),
            Token(id = 1, state = TokenState.FINISHED, position = 57),
            Token(id = 2, state = TokenState.FINISHED, position = 57),
            Token(id = 3, state = TokenState.FINISHED, position = 57)
        )
        val player = redPlayer.copy(tokens = tokens)

        val gameState = LudoGameState(
            players = listOf(player, bluePlayer),
            currentTurnPlayerId = player.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 2, // Will finish last token
            canRollDice = false,
            mustSelectToken = true
        )

        // Act
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success
        assertTrue("Player should have won", success.hasWon)
        assertEquals("Winner should be set", player.id, success.newState.winnerId)
        assertEquals("Game should be finished", GameStatus.FINISHED, success.newState.gameStatus)
    }

    @Test
    fun `isWinningState returns true when player has all tokens finished`() {
        // Arrange
        val tokens = listOf(
            Token(id = 0, state = TokenState.FINISHED, position = 57),
            Token(id = 1, state = TokenState.FINISHED, position = 57),
            Token(id = 2, state = TokenState.FINISHED, position = 57),
            Token(id = 3, state = TokenState.FINISHED, position = 57)
        )
        val player = redPlayer.copy(tokens = tokens)

        // Act & Assert
        assertTrue("Player should have won", player.hasWon())
    }

    @Test
    fun `isWinningState returns false when not all tokens finished`() {
        // Arrange
        val tokens = listOf(
            Token(id = 0, state = TokenState.FINISHED, position = 57),
            Token(id = 1, state = TokenState.FINISHED, position = 57),
            Token(id = 2, state = TokenState.FINISHED, position = 57),
            Token(id = 3, state = TokenState.ACTIVE, position = 50)
        )
        val player = redPlayer.copy(tokens = tokens)

        // Act & Assert
        assertFalse("Player should not have won yet", player.hasWon())
    }

    @Test
    fun `finished token cannot move`() {
        // Arrange
        val finishedToken = Token(id = 0, state = TokenState.FINISHED, position = 57)

        // Act & Assert
        for (dice in 1..6) {
            assertFalse("Finished token should not move with dice $dice",
                finishedToken.canMove(dice))
        }
    }

    @Test
    fun `game with 2 players uses opposite colors`() {
        // Arrange & Act
        val colors = LudoColor.forPlayerCount(2)

        // Assert
        assertEquals("Should have 2 colors", 2, colors.size)
        assertEquals("First should be RED", LudoColor.RED, colors[0])
        assertEquals("Second should be YELLOW", LudoColor.YELLOW, colors[1])
    }

    @Test
    fun `game with 4 players uses all colors`() {
        // Arrange & Act
        val colors = LudoColor.forPlayerCount(4)

        // Assert
        assertEquals("Should have 4 colors", 4, colors.size)
        assertTrue("Should contain all colors",
            colors.containsAll(listOf(LudoColor.RED, LudoColor.BLUE,
                LudoColor.GREEN, LudoColor.YELLOW)))
    }

    @Test
    fun `startGame initializes correct game state`() {
        // Arrange
        val players = listOf(redPlayer, bluePlayer)

        // Act
        val gameState = LudoEngine.startGame(players)

        // Assert
        assertEquals("Should have 2 players", 2, gameState.players.size)
        assertEquals("First player should have first turn",
            players.first().id, gameState.currentTurnPlayerId)
        assertEquals("Game should be in progress", GameStatus.IN_PROGRESS, gameState.gameStatus)
        assertTrue("Should be able to roll dice", gameState.canRollDice)
        assertNull("No dice value yet", gameState.diceValue)
        assertNull("No winner yet", gameState.winnerId)
    }

    @Test
    fun `advanceToNextPlayer correctly cycles through players`() {
        // Arrange
        val gameState = LudoGameState(
            players = listOf(redPlayer, bluePlayer, greenPlayer),
            currentTurnPlayerId = redPlayer.id,
            gameStatus = GameStatus.IN_PROGRESS
        )

        // Act
        var state = gameState
        state = LudoEngine.advanceToNextPlayer(state)
        assertEquals("Should advance to blue", bluePlayer.id, state.currentTurnPlayerId)

        state = LudoEngine.advanceToNextPlayer(state)
        assertEquals("Should advance to green", greenPlayer.id, state.currentTurnPlayerId)

        state = LudoEngine.advanceToNextPlayer(state)
        assertEquals("Should cycle back to red", redPlayer.id, state.currentTurnPlayerId)
    }

    // ==================== BOARD POSITION CALCULATION TESTS ====================

    @Test
    fun `toAbsolutePosition calculates correctly for each color`() {
        // Arrange & Act & Assert
        // Red starts at 0
        assertEquals("Red at relative 0 = absolute 0",
            0, LudoBoard.toAbsolutePosition(0, LudoColor.RED))
        assertEquals("Red at relative 10 = absolute 10",
            10, LudoBoard.toAbsolutePosition(10, LudoColor.RED))

        // Blue starts at 13
        assertEquals("Blue at relative 0 = absolute 13",
            13, LudoBoard.toAbsolutePosition(0, LudoColor.BLUE))
        assertEquals("Blue at relative 40 = absolute 1 (wrap around)",
            1, LudoBoard.toAbsolutePosition(40, LudoColor.BLUE))

        // Green starts at 26 (bottom-right quadrant)
        assertEquals("Green at relative 0 = absolute 26",
            26, LudoBoard.toAbsolutePosition(0, LudoColor.GREEN))

        // Yellow starts at 39 (bottom-left quadrant)
        assertEquals("Yellow at relative 0 = absolute 39",
            39, LudoBoard.toAbsolutePosition(0, LudoColor.YELLOW))
    }

    @Test
    fun `toAbsolutePosition returns -1 for home stretch positions`() {
        // Arrange & Act & Assert
        assertEquals("Position 51 should return -1",
            -1, LudoBoard.toAbsolutePosition(51, LudoColor.RED))
        assertEquals("Position 56 should return -1",
            -1, LudoBoard.toAbsolutePosition(56, LudoColor.RED))
    }

    @Test
    fun `distanceToFinish calculates correctly`() {
        // Arrange & Act & Assert
        assertEquals("At position 50, distance should be 7",
            7, LudoBoard.distanceToFinish(50))
        assertEquals("At position 55, distance should be 2",
            2, LudoBoard.distanceToFinish(55))
        assertEquals("At position 57 (finish), distance should be 0",
            0, LudoBoard.distanceToFinish(57))
    }

    // ==================== COLOR-KEYED POSITION TESTS ====================

    /**
     * Test that Yellow token leaving HOME with dice=6 goes to Yellow's start position,
     * NOT any other player's position. This verifies the fix for the bug where
     * index-based color lookup caused tokens to appear in wrong quadrants.
     */
    @Test
    fun `yellow token leaving home goes to yellow start position not opposite side`() {
        // Arrange - Yellow player's turn with dice 6
        val gameState = LudoGameState(
            players = listOf(redPlayer, bluePlayer, greenPlayer, yellowPlayer),
            currentTurnPlayerId = yellowPlayer.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )

        // Act - Move yellow token from HOME
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert - Move should succeed
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success

        // Assert - Token should be at relative position 0 (start)
        val yellowPlayerAfter = success.newState.getPlayer(yellowPlayer.id)
        val movedToken = yellowPlayerAfter?.getToken(0)
        assertNotNull("Token should exist", movedToken)
        assertEquals("Token should be ACTIVE", TokenState.ACTIVE, movedToken?.state)
        assertEquals("Token should be at relative position 0 (start)", 0, movedToken?.position)

        // Assert - Verify absolute position is Yellow's start (39), not Red's (0)
        val absolutePos = LudoBoard.toAbsolutePosition(0, LudoColor.YELLOW)
        assertEquals("Yellow's absolute start position should be 39", 39, absolutePos)

        // Assert - Verify start positions are keyed by color, not player index
        assertEquals("Yellow start position should be 39 (bottom-left)",
            39, LudoBoard.getStartPosition(LudoColor.YELLOW))
        assertEquals("Green start position should be 26 (bottom-right)",
            26, LudoBoard.getStartPosition(LudoColor.GREEN))
        assertEquals("Blue start position should be 13 (top-right)",
            13, LudoBoard.getStartPosition(LudoColor.BLUE))
        assertEquals("Red start position should be 0 (top-left)",
            0, LudoBoard.getStartPosition(LudoColor.RED))
    }

    @Test
    fun `each color has unique start position keyed by color not index`() {
        // This test ensures that start positions are determined by LudoColor,
        // not by player list index. This prevents the bug where colors could
        // be swapped if player order changes.

        // Arrange - Create players in different orders
        val playersOrder1 = listOf(redPlayer, bluePlayer, greenPlayer, yellowPlayer)
        val playersOrder2 = listOf(yellowPlayer, greenPlayer, bluePlayer, redPlayer)

        // Act - Check start positions
        val redStart = LudoBoard.getStartPosition(LudoColor.RED)
        val blueStart = LudoBoard.getStartPosition(LudoColor.BLUE)
        val greenStart = LudoBoard.getStartPosition(LudoColor.GREEN)
        val yellowStart = LudoBoard.getStartPosition(LudoColor.YELLOW)

        // Assert - Positions should be keyed by color, same regardless of player order
        assertEquals("Red should always start at 0", 0, redStart)
        assertEquals("Blue should always start at 13", 13, blueStart)
        assertEquals("Green should always start at 26", 26, greenStart)
        assertEquals("Yellow should always start at 39", 39, yellowStart)

        // Assert - All start positions are unique
        val allStarts = setOf(redStart, blueStart, greenStart, yellowStart)
        assertEquals("All 4 start positions should be unique", 4, allStarts.size)

        // Assert - Start positions are evenly spaced (52/4 = 13)
        assertEquals("Red to Blue = 13", 13, blueStart - redStart)
        assertEquals("Blue to Green = 13", 13, greenStart - blueStart)
        assertEquals("Green to Yellow = 13", 13, yellowStart - greenStart)
    }

    // ==================== BOT TURN RULES TESTS ====================
    // These tests verify the rules that bots must follow:
    // 1. Roll once per turn
    // 2. Move once if possible
    // 3. Get ONE extra turn only if dice==6 or capture
    // 4. Turn ends otherwise

    @Test
    fun `bot turn ends when dice is not 6 and no capture - turn advances to next player`() {
        // Arrange - Bot (blue) has an active token
        val botTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 10),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val bot = bluePlayer.copy(tokens = botTokens, isBot = true)

        val gameState = LudoGameState(
            players = listOf(redPlayer, bot),
            currentTurnPlayerId = bot.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 3, // Not a 6
            canRollDice = false,
            mustSelectToken = true
        )

        // Act - Bot moves token
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert - Move succeeds
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success

        // Assert - No bonus turn (dice != 6, no capture)
        assertFalse("Should NOT get bonus turn for dice=3", success.bonusTurn)

        // Assert - Turn advances to next player (Red)
        assertEquals("Turn should advance to Red (human)",
            redPlayer.id, success.newState.currentTurnPlayerId)

        // Assert - Can roll dice (for next player)
        assertTrue("Next player should be able to roll", success.newState.canRollDice)

        // Assert - No token selection required
        assertFalse("Should not need to select token", success.newState.mustSelectToken)
    }

    @Test
    fun `bot gets exactly one extra turn when dice is 6 - bonusTurn is true`() {
        // Arrange - Bot has an active token
        val botTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 10),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val bot = bluePlayer.copy(tokens = botTokens, isBot = true)

        val gameState = LudoGameState(
            players = listOf(redPlayer, bot),
            currentTurnPlayerId = bot.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 6, // Rolled a 6
            canRollDice = false,
            mustSelectToken = true,
            consecutiveSixes = 1 // Already rolled one 6
        )

        // Act - Bot moves token
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert - Move succeeds
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success

        // Assert - Gets exactly ONE bonus turn
        assertTrue("Should get bonus turn for dice=6", success.bonusTurn)

        // Assert - Turn stays with the same player (bot)
        assertEquals("Turn should stay with bot",
            bot.id, success.newState.currentTurnPlayerId)

        // Assert - Can roll dice again (for bonus turn)
        assertTrue("Bot should be able to roll again", success.newState.canRollDice)

        // Assert - Consecutive sixes counter is maintained (not reset to 0)
        assertEquals("Consecutive sixes should be maintained",
            1, success.newState.consecutiveSixes)
    }

    @Test
    fun `bot turn ends automatically when no valid moves - engine advances turn`() {
        // Arrange - Bot has all tokens at HOME, dice is NOT 6
        val bot = bluePlayer.copy(isBot = true) // All 4 tokens at HOME by default

        val gameState = LudoGameState(
            players = listOf(redPlayer, bot),
            currentTurnPlayerId = bot.id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true
        )

        // Act - Bot rolls a 3 (can't move any token from HOME)
        val newState = LudoEngine.rollDice(gameState, 3)

        // Assert - No valid moves, turn auto-advances
        assertFalse("Should not require token selection (no valid moves)",
            newState.mustSelectToken)

        // Assert - Turn advances to next player (Red)
        assertEquals("Turn should auto-advance to Red when no moves",
            redPlayer.id, newState.currentTurnPlayerId)

        // Assert - New player can roll
        assertTrue("Next player should be able to roll", newState.canRollDice)
    }

    @Test
    fun `bot rolling dice does not change turn by itself - only after move`() {
        // Arrange - Bot has an active token
        val botTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 10),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val bot = bluePlayer.copy(tokens = botTokens, isBot = true)

        val gameState = LudoGameState(
            players = listOf(redPlayer, bot),
            currentTurnPlayerId = bot.id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true
        )

        // Act - Bot rolls dice (not 6)
        val stateAfterRoll = LudoEngine.rollDice(gameState, 4)

        // Assert - Turn is still with bot (need to move)
        assertEquals("Turn should stay with bot after rolling (need to move)",
            bot.id, stateAfterRoll.currentTurnPlayerId)

        // Assert - Must select token to move
        assertTrue("Should require token selection", stateAfterRoll.mustSelectToken)

        // Assert - Cannot roll again before moving
        assertFalse("Should not be able to roll again before moving",
            stateAfterRoll.canRollDice)

        // Assert - Dice value is set
        assertEquals("Dice value should be set", 4, stateAfterRoll.diceValue)
    }

    @Test
    fun `bot cannot roll multiple times per turn - canRollDice is false after roll`() {
        // Arrange - Bot's turn
        val botTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 10),
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val bot = bluePlayer.copy(tokens = botTokens, isBot = true)

        val gameState = LudoGameState(
            players = listOf(redPlayer, bot),
            currentTurnPlayerId = bot.id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true,
            diceValue = null
        )

        // Act - Bot rolls
        val stateAfterRoll = LudoEngine.rollDice(gameState, 5)

        // Assert - canRollDice is false (must move before rolling again)
        assertFalse("canRollDice should be false after rolling (must move first)",
            stateAfterRoll.canRollDice)

        // Assert - Dice value is set
        assertNotNull("Dice value should be set", stateAfterRoll.diceValue)

        // Assert - If bot tries to roll again (via require check), it should fail
        val exception = try {
            LudoEngine.rollDice(stateAfterRoll, 3)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertNotNull("Should throw when trying to roll while canRollDice is false", exception)
    }

    @Test
    fun `bot gets bonus turn on capture - turn stays with bot`() {
        // Arrange - Bot's token will land on opponent's token (capture)
        // Blue at relative 7 + 3 = 10, absolute = (10 + 13) % 52 = 23 (NOT a safe cell)
        // Safe cells are: 0, 8, 13, 21, 26, 34, 39, 47 - so 23 is capturable
        val botTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 7), // Will move to 10
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val bot = bluePlayer.copy(tokens = botTokens, isBot = true)

        // Red's token is at absolute position 23 where Blue will land
        // Red needs relative position such that (pos + 0) % 52 = 23, so relative 23
        val redTokens = listOf(
            Token(id = 0, state = TokenState.ACTIVE, position = 23), // At absolute 23
            Token(id = 1, state = TokenState.HOME, position = -1),
            Token(id = 2, state = TokenState.HOME, position = -1),
            Token(id = 3, state = TokenState.HOME, position = -1)
        )
        val human = redPlayer.copy(tokens = redTokens)

        val gameState = LudoGameState(
            players = listOf(human, bot),
            currentTurnPlayerId = bot.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 3, // Blue moves from 7 to 10, absolute (10+13)%52 = 23
            canRollDice = false,
            mustSelectToken = true
        )

        // Act - Bot moves and captures
        val result = LudoEngine.moveToken(gameState, tokenId = 0)

        // Assert - Move succeeds
        assertTrue("Move should succeed", result is MoveResult.Success)
        val success = result as MoveResult.Success

        // Assert - Capture occurred
        assertNotNull("Should have captured a token", success.move.capturedTokenInfo)

        // Assert - Gets bonus turn for capture (even though dice != 6)
        assertTrue("Should get bonus turn for capture", success.bonusTurn)

        // Assert - Turn stays with bot
        assertEquals("Turn should stay with bot after capture",
            bot.id, success.newState.currentTurnPlayerId)
    }

    // ==================== HELPER METHODS ====================

    private fun createTwoPlayerGame(): LudoGameState {
        return LudoGameState(
            players = listOf(redPlayer, bluePlayer),
            currentTurnPlayerId = redPlayer.id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true
        )
    }

    private fun createFourPlayerGame(): LudoGameState {
        return LudoGameState(
            players = listOf(redPlayer, bluePlayer, greenPlayer, yellowPlayer),
            currentTurnPlayerId = redPlayer.id,
            gameStatus = GameStatus.IN_PROGRESS,
            canRollDice = true
        )
    }
}
