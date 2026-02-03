package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoGameState
import com.parthipan.colorclashcards.game.ludo.model.LudoPlayer
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jetpack Compose UI tests for the Ludo game screen.
 *
 * These tests verify:
 * - Game screen renders board and tokens
 * - Current player indicator updates when turn changes
 * - Roll dice button enabled only for current player
 * - After dice roll, valid tokens highlight
 * - Clicking a highlighted token triggers move
 * - Invalid token click does nothing
 * - 'Player disconnected' banner appears when presence flag changes
 * - Turn timer is visible and updates
 */
@RunWith(AndroidJUnit4::class)
class LudoGameScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ============================================================
    // Test Helpers
    // ============================================================

    private fun createTestPlayer(
        id: String,
        name: String,
        color: LudoColor,
        tokens: List<Token> = listOf(
            Token(0, TokenState.HOME, 0),
            Token(1, TokenState.HOME, 0),
            Token(2, TokenState.HOME, 0),
            Token(3, TokenState.HOME, 0)
        )
    ) = LudoPlayer(
        id = id,
        name = name,
        color = color,
        isBot = false,
        isOnline = true,
        tokens = tokens
    )

    private fun createTestGameState(
        players: List<LudoPlayer> = listOf(
            createTestPlayer("player1", "Player 1", LudoColor.RED),
            createTestPlayer("player2", "Player 2", LudoColor.BLUE)
        ),
        currentTurnPlayerId: String = "player1",
        diceValue: Int? = null,
        canRollDice: Boolean = true,
        mustSelectToken: Boolean = false
    ) = LudoGameState(
        players = players,
        currentTurnPlayerId = currentTurnPlayerId,
        diceValue = diceValue,
        canRollDice = canRollDice,
        mustSelectToken = mustSelectToken
    )

    private fun createTestUiState(
        gameState: LudoGameState? = createTestGameState(),
        isMyTurn: Boolean = true,
        isRolling: Boolean = false,
        canRoll: Boolean = true,
        mustSelectToken: Boolean = false,
        diceValue: Int? = null,
        movableTokenIds: List<Int> = emptyList(),
        disconnectedPlayers: Set<String> = emptySet(),
        afkWarning: Boolean = false,
        afkCountdown: Int? = null,
        showWinDialog: Boolean = false,
        winnerName: String? = null,
        localPlayerId: String = "player1",
        isLoading: Boolean = false
    ) = LudoOnlineUiState(
        gameState = gameState,
        isMyTurn = isMyTurn,
        isRolling = isRolling,
        canRoll = canRoll,
        mustSelectToken = mustSelectToken,
        diceValue = diceValue,
        movableTokenIds = movableTokenIds,
        disconnectedPlayers = disconnectedPlayers,
        afkWarning = afkWarning,
        afkCountdown = afkCountdown,
        showWinDialog = showWinDialog,
        winnerName = winnerName,
        localPlayerId = localPlayerId,
        isLoading = isLoading
    )

    // ============================================================
    // Test: Game screen renders board and tokens
    // ============================================================

    @Test
    fun gameScreenRendersBoard() {
        composeTestRule.setContent {
            TestLudoGameContent(uiState = createTestUiState())
        }

        // Verify board is displayed
        composeTestRule.onNodeWithTag("ludoBoard").assertIsDisplayed()
    }

    @Test
    fun gameScreenRendersTokens() {
        val player = createTestPlayer(
            id = "player1",
            name = "Test Player",
            color = LudoColor.RED,
            tokens = listOf(
                Token(0, TokenState.HOME, 0),
                Token(1, TokenState.ACTIVE, 10),
                Token(2, TokenState.HOME, 0),
                Token(3, TokenState.HOME, 0)
            )
        )

        val gameState = createTestGameState(
            players = listOf(player, createTestPlayer("player2", "Player 2", LudoColor.BLUE))
        )

        composeTestRule.setContent {
            TestLudoGameContent(uiState = createTestUiState(gameState = gameState))
        }

        // Verify tokens are displayed
        composeTestRule.onNodeWithTag("token_red_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("token_red_1").assertIsDisplayed()
    }

    @Test
    fun gameScreenRendersPlayerCards() {
        composeTestRule.setContent {
            TestLudoGameContent(uiState = createTestUiState())
        }

        // Verify player cards are displayed
        composeTestRule.onNodeWithTag("playerCard_player1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playerCard_player2").assertIsDisplayed()
    }

    // ============================================================
    // Test: Current player indicator updates when turn changes
    // ============================================================

    @Test
    fun turnLabelShowsYourTurnWhenLocalPlayerTurn() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    localPlayerId = "player1"
                )
            )
        }

        composeTestRule.onNodeWithTag("turnLabel").assertTextEquals("Your turn")
    }

    @Test
    fun turnLabelShowsOtherPlayerNameWhenNotLocalTurn() {
        val gameState = createTestGameState(currentTurnPlayerId = "player2")

        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    gameState = gameState,
                    isMyTurn = false,
                    localPlayerId = "player1"
                )
            )
        }

        composeTestRule.onNodeWithTag("turnLabel").assertTextContains("Player 2")
    }

    @Test
    fun turnIndicatorUpdatesWhenTurnChanges() {
        val uiStateHolder = mutableStateOf(
            createTestUiState(
                isMyTurn = true,
                localPlayerId = "player1"
            )
        )

        composeTestRule.setContent {
            TestLudoGameContent(uiState = uiStateHolder.value)
        }

        // Initially shows "Your turn"
        composeTestRule.onNodeWithTag("turnLabel").assertTextEquals("Your turn")

        // Update turn to other player
        val newGameState = createTestGameState(currentTurnPlayerId = "player2")
        uiStateHolder.value = createTestUiState(
            gameState = newGameState,
            isMyTurn = false,
            localPlayerId = "player1"
        )

        composeTestRule.waitForIdle()

        // Now shows other player's name
        composeTestRule.onNodeWithTag("turnLabel").assertTextContains("Player 2")
    }

    // ============================================================
    // Test: Roll dice button enabled only for current player
    // ============================================================

    @Test
    fun diceIsClickableWhenCanRoll() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    canRoll = true,
                    isRolling = false
                )
            )
        }

        // Dice should be enabled
        composeTestRule.onNodeWithTag("rollDiceButton").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Tap to roll dice").assertIsDisplayed()
    }

    @Test
    fun diceShowsRollingStateWhileAnimating() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    canRoll = false,
                    isRolling = true
                )
            )
        }

        composeTestRule.onNodeWithContentDescription("Dice rolling").assertIsDisplayed()
    }

    @Test
    fun diceShowsValueAfterRoll() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    canRoll = false,
                    isRolling = false,
                    diceValue = 6,
                    mustSelectToken = true
                )
            )
        }

        composeTestRule.onNodeWithContentDescription("Dice showing 6").assertIsDisplayed()
    }

    @Test
    fun statusTextShowsWaitingWhenNotMyTurn() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = false,
                    canRoll = false
                )
            )
        }

        composeTestRule.onNodeWithTag("statusText")
            .assertTextContains("Waiting for other player")
    }

    @Test
    fun statusTextShowsTapDiceWhenCanRoll() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    canRoll = true,
                    isRolling = false
                )
            )
        }

        composeTestRule.onNodeWithTag("statusText")
            .assertTextContains("Tap dice to roll")
    }

    @Test
    fun statusTextShowsSelectTokenWhenMustMove() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    canRoll = false,
                    mustSelectToken = true,
                    diceValue = 6,
                    movableTokenIds = listOf(0, 1)
                )
            )
        }

        composeTestRule.onNodeWithTag("statusText")
            .assertTextContains("Select a token to move")
    }

    // ============================================================
    // Test: After dice roll, valid tokens highlight
    // ============================================================

    @Test
    fun selectableTokensHaveHighlightAccessibility() {
        val player = createTestPlayer(
            id = "player1",
            name = "Test Player",
            color = LudoColor.RED,
            tokens = listOf(
                Token(0, TokenState.HOME, 0),
                Token(1, TokenState.ACTIVE, 10),
                Token(2, TokenState.HOME, 0),
                Token(3, TokenState.HOME, 0)
            )
        )

        val gameState = createTestGameState(
            players = listOf(player, createTestPlayer("player2", "Player 2", LudoColor.BLUE)),
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )

        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    gameState = gameState,
                    isMyTurn = true,
                    canRoll = false,
                    mustSelectToken = true,
                    diceValue = 6,
                    movableTokenIds = listOf(0, 1) // Tokens 0 and 1 are selectable
                )
            )
        }

        // Selectable token should have accessibility description
        composeTestRule.onNodeWithContentDescription("RED token 0 - tap to move").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("RED token 1 - tap to move").assertIsDisplayed()
    }

    // ============================================================
    // Test: Clicking a highlighted token triggers move
    // ============================================================

    @Test
    fun clickingSelectableTokenTriggersCallback() {
        var clickedTokenId: Int? = null

        val player = createTestPlayer(
            id = "player1",
            name = "Test Player",
            color = LudoColor.RED,
            tokens = listOf(
                Token(0, TokenState.HOME, 0),
                Token(1, TokenState.ACTIVE, 10),
                Token(2, TokenState.HOME, 0),
                Token(3, TokenState.HOME, 0)
            )
        )

        val gameState = createTestGameState(
            players = listOf(player, createTestPlayer("player2", "Player 2", LudoColor.BLUE)),
            diceValue = 6,
            canRollDice = false,
            mustSelectToken = true
        )

        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    gameState = gameState,
                    isMyTurn = true,
                    canRoll = false,
                    mustSelectToken = true,
                    diceValue = 6,
                    movableTokenIds = listOf(0, 1)
                ),
                onTokenClick = { tokenId -> clickedTokenId = tokenId }
            )
        }

        // Click on selectable token
        composeTestRule.onNodeWithTag("token_red_0").performClick()

        // Verify callback was triggered
        assertEquals(0, clickedTokenId)
    }

    // ============================================================
    // Test: Invalid token click does nothing
    // ============================================================

    @Test
    fun clickingNonSelectableTokenDoesNotTriggerCallback() {
        var clickedTokenId: Int? = null

        val player = createTestPlayer(
            id = "player1",
            name = "Test Player",
            color = LudoColor.RED,
            tokens = listOf(
                Token(0, TokenState.HOME, 0),
                Token(1, TokenState.HOME, 0),
                Token(2, TokenState.HOME, 0),
                Token(3, TokenState.HOME, 0)
            )
        )

        val gameState = createTestGameState(
            players = listOf(player, createTestPlayer("player2", "Player 2", LudoColor.BLUE)),
            diceValue = 3, // Not 6, so can't leave home
            canRollDice = false,
            mustSelectToken = false // No tokens can move
        )

        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    gameState = gameState,
                    isMyTurn = true,
                    canRoll = false,
                    mustSelectToken = false,
                    diceValue = 3,
                    movableTokenIds = emptyList() // No movable tokens
                ),
                onTokenClick = { tokenId -> clickedTokenId = tokenId }
            )
        }

        // Click on non-selectable token
        composeTestRule.onNodeWithTag("token_red_0").performClick()

        // Verify callback was NOT triggered (token is not selectable)
        // The click should be ignored by the token's clickable modifier
        assertEquals(null, clickedTokenId)
    }

    // ============================================================
    // Test: 'Player disconnected' banner appears
    // ============================================================

    @Test
    fun disconnectedPlayerShowsDisconnectIndicator() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    disconnectedPlayers = setOf("player2")
                )
            )
        }

        // Verify disconnect icon is shown for player2
        composeTestRule.onNodeWithTag("disconnectedIcon_player2").assertIsDisplayed()
    }

    @Test
    fun connectedPlayerDoesNotShowDisconnectIndicator() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    disconnectedPlayers = emptySet()
                )
            )
        }

        // Verify disconnect icon is NOT shown
        composeTestRule.onNodeWithTag("disconnectedIcon_player1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("disconnectedIcon_player2").assertDoesNotExist()
    }

    // ============================================================
    // Test: Turn timer is visible and updates (AFK warning)
    // ============================================================

    @Test
    fun afkWarningCardIsDisplayedWhenAfkWarningIsTrue() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    afkWarning = true,
                    afkCountdown = 10
                )
            )
        }

        composeTestRule.onNodeWithTag("afkWarningCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("afkCountdown").assertTextContains("10 seconds")
    }

    @Test
    fun afkWarningUpdatesCountdown() {
        val uiStateHolder = mutableStateOf(
            createTestUiState(
                afkWarning = true,
                afkCountdown = 10
            )
        )

        composeTestRule.setContent {
            TestLudoGameContent(uiState = uiStateHolder.value)
        }

        // Initially shows 10 seconds
        composeTestRule.onNodeWithTag("afkCountdown").assertTextContains("10 seconds")

        // Update countdown
        uiStateHolder.value = createTestUiState(
            afkWarning = true,
            afkCountdown = 5
        )

        composeTestRule.waitForIdle()

        // Now shows 5 seconds
        composeTestRule.onNodeWithTag("afkCountdown").assertTextContains("5 seconds")
    }

    @Test
    fun afkWarningIsNotDisplayedWhenNoWarning() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    afkWarning = false,
                    afkCountdown = null
                )
            )
        }

        composeTestRule.onNodeWithTag("afkWarningCard").assertDoesNotExist()
    }

    @Test
    fun statusTextShowsAfkCountdownWhenWarning() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    afkWarning = true,
                    afkCountdown = 8
                )
            )
        }

        composeTestRule.onNodeWithTag("statusText")
            .assertTextContains("Move in 8 seconds!")
    }

    // ============================================================
    // Test: Win dialog appears
    // ============================================================

    @Test
    fun winDialogIsDisplayedWhenShowWinDialogIsTrue() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    showWinDialog = true,
                    winnerName = "You"
                )
            )
        }

        composeTestRule.onNodeWithTag("winDialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("winDialogTitle").assertTextContains("You Win!")
    }

    @Test
    fun winDialogShowsGameOverWhenOtherPlayerWins() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    showWinDialog = true,
                    winnerName = "Player 2"
                )
            )
        }

        composeTestRule.onNodeWithTag("winDialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("winDialogTitle").assertTextContains("Game Over")
    }

    // ============================================================
    // Test: Game controls card visibility
    // ============================================================

    @Test
    fun gameControlsCardIsDisplayed() {
        composeTestRule.setContent {
            TestLudoGameContent(uiState = createTestUiState())
        }

        composeTestRule.onNodeWithTag("gameControls").assertIsDisplayed()
    }

    @Test
    fun diceClickTriggersRollCallback() {
        var rollClicked = false

        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    isMyTurn = true,
                    canRoll = true,
                    isRolling = false
                ),
                onRollDice = { rollClicked = true }
            )
        }

        composeTestRule.onNodeWithTag("rollDiceButton").performClick()

        assertTrue(rollClicked)
    }

    // ============================================================
    // Test: Loading state
    // ============================================================

    @Test
    fun loadingIndicatorIsShownWhenLoading() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    gameState = null,
                    isLoading = true
                )
            )
        }

        composeTestRule.onNodeWithText("Loading game...").assertIsDisplayed()
    }

    // ============================================================
    // Test: Error state
    // ============================================================

    @Test
    fun errorMessageIsShownWhenNoGameState() {
        composeTestRule.setContent {
            TestLudoGameContent(
                uiState = createTestUiState(
                    gameState = null,
                    isLoading = false
                )
            )
        }

        composeTestRule.onNodeWithText("Failed to load game").assertIsDisplayed()
    }
}
