package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoGameState
import com.parthipan.colorclashcards.game.ludo.model.LudoPlayer
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/**
 * Main Ludo game screen with board, tokens, dice, and player info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoGameScreen(
    gameState: LudoGameState,
    localPlayerId: String,
    onBackClick: () -> Unit,
    viewModel: LudoGameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error messages
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Show info messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val currentPlayer = gameState.currentPlayer
    val isMyTurn = gameState.currentTurnPlayerId == localPlayerId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ludo",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Current turn indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(LudoBoardColors.getColor(currentPlayer.color))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMyTurn) "Your turn" else "${currentPlayer.name}'s turn",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LudoBoardColors.getColor(currentPlayer.color),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // All players in a single row at top
            OfflinePlayersRow(
                players = gameState.players,
                currentTurnPlayerId = gameState.currentTurnPlayerId,
                localPlayerId = localPlayerId,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Game board with tokens (long-press to toggle debug overlay)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val boardSize = minOf(maxWidth, maxHeight)

                LudoBoardWithTokens(
                    gameState = gameState,
                    selectableTokenIds = if (isMyTurn) uiState.movableTokenIds else emptyList(),
                    localPlayerId = localPlayerId,
                    boardSize = boardSize,
                    onTokenClick = { tokenId ->
                        viewModel.moveToken(tokenId)
                    }
                )
            }

            // Compact dice controls at bottom
            CompactDiceControls(
                diceValue = uiState.diceValue,
                isRolling = uiState.isRollingDice,
                canRoll = isMyTurn && uiState.canRollDice,
                mustSelectToken = uiState.mustSelectToken,
                isMyTurn = isMyTurn,
                onRollDice = { viewModel.rollDice() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    // Win dialog
    if (uiState.showWinDialog) {
        WinDialog(
            winnerName = uiState.winnerName ?: "Unknown",
            onDismiss = { viewModel.dismissWinDialog() },
            onPlayAgain = {
                viewModel.dismissWinDialog()
                // TODO: Implement play again
            }
        )
    }
}

/**
 * Ludo board with all tokens rendered on top.
 */
@Composable
fun LudoBoardWithTokens(
    gameState: LudoGameState,
    selectableTokenIds: List<Int>,
    localPlayerId: String,
    boardSize: Dp,
    onTokenClick: (Int) -> Unit
) {
    val cellSizeDp = boardSize / 15

    Box(
        modifier = Modifier
            .size(boardSize)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color.DarkGray, RoundedCornerShape(8.dp))
    ) {
        // Draw the board
        LudoBoardCanvas(
            modifier = Modifier.fillMaxSize()
        )

        // Draw tokens for each player
        gameState.players.forEach { player ->
            val isLocalPlayer = player.id == localPlayerId
            val tokenSelectableIds = if (isLocalPlayer) selectableTokenIds else emptyList()

            PlayerTokensOverlay(
                tokens = player.tokens,
                color = player.color,
                selectableTokenIds = tokenSelectableIds,
                cellSize = cellSizeDp,
                onTokenClick = { tokenId ->
                    if (isLocalPlayer) {
                        onTokenClick(tokenId)
                    }
                }
            )
        }
    }
}

/**
 * Overlay of tokens for a single player.
 */
@Composable
private fun PlayerTokensOverlay(
    tokens: List<Token>,
    color: LudoColor,
    selectableTokenIds: List<Int>,
    cellSize: Dp,
    onTokenClick: (Int) -> Unit
) {
    tokens.forEach { token ->
        val position = getTokenPosition(token, color)
        if (position != null) {
            LudoTokenView(
                token = token,
                color = color,
                isSelectable = token.id in selectableTokenIds,
                cellSize = cellSize,
                boardPosition = position,
                onClick = { onTokenClick(token.id) }
            )
        }
    }
}

/**
 * Get board position for a token.
 */
private fun getTokenPosition(token: Token, color: LudoColor): BoardPosition? {
    return when (token.state) {
        TokenState.HOME -> {
            LudoBoardPositions.getHomeBasePositions(color).getOrNull(token.id)
        }
        TokenState.ACTIVE -> {
            LudoBoardPositions.getGridPosition(token.position, color)
        }
        TokenState.FINISHED -> {
            // Offset finished tokens slightly based on ID to avoid overlap
            val center = LudoBoardPositions.getFinishPosition()
            val offset = when (token.id) {
                0 -> BoardPosition(center.column - 1, center.row - 1)
                1 -> BoardPosition(center.column + 1, center.row - 1)
                2 -> BoardPosition(center.column - 1, center.row + 1)
                3 -> BoardPosition(center.column + 1, center.row + 1)
                else -> center
            }
            offset
        }
    }
}

/**
 * Horizontal row of all players shown as compact chips.
 * Single source of truth: currentTurnPlayerId determines which player is highlighted.
 */
@Composable
private fun OfflinePlayersRow(
    players: List<LudoPlayer>,
    currentTurnPlayerId: String,
    localPlayerId: String,
    modifier: Modifier = Modifier
) {
    if (players.isEmpty()) return

    LazyRow(
        modifier = modifier.testTag("playersRow"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(players, key = { it.id }) { player ->
            val isCurrentTurn = player.id == currentTurnPlayerId
            val isLocalPlayer = player.id == localPlayerId

            OfflinePlayerChip(
                player = player,
                displayName = if (isLocalPlayer) "You" else player.name,
                isCurrentTurn = isCurrentTurn,
                modifier = Modifier.testTag("playerItem_${player.id}")
            )
        }
    }
}

/**
 * Compact player chip showing color, name, and token counts.
 */
@Composable
private fun OfflinePlayerChip(
    player: LudoPlayer,
    displayName: String,
    isCurrentTurn: Boolean,
    modifier: Modifier = Modifier
) {
    val playerColor = LudoBoardColors.getColor(player.color)
    val homeCount = player.tokens.count { it.state == TokenState.HOME }
    val activeCount = player.tokens.count { it.state == TokenState.ACTIVE }
    val finishedCount = player.tokens.count { it.state == TokenState.FINISHED }

    Card(
        modifier = modifier
            .then(
                if (isCurrentTurn) {
                    Modifier.border(2.dp, playerColor, RoundedCornerShape(12.dp))
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTurn) {
                playerColor.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentTurn) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Turn indicator + Color dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(playerColor)
                    .then(
                        if (isCurrentTurn) {
                            Modifier
                                .border(2.dp, Color.White, CircleShape)
                                .testTag("turnIndicator_${player.id}")
                        } else Modifier
                    )
            )

            // Name
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            // Token counts: H/A/F
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OfflineTokenCountBadge(
                    count = homeCount,
                    label = "H",
                    color = playerColor.copy(alpha = 0.4f)
                )
                OfflineTokenCountBadge(
                    count = activeCount,
                    label = "A",
                    color = playerColor
                )
                OfflineTokenCountBadge(
                    count = finishedCount,
                    label = "F",
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

/**
 * Small badge showing token count with label.
 */
@Composable
private fun OfflineTokenCountBadge(
    count: Int,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Compact dice controls with optional timer ring - reduced height, horizontal layout with rounded card.
 */
@Composable
private fun CompactDiceControls(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    mustSelectToken: Boolean,
    isMyTurn: Boolean,
    onRollDice: () -> Unit,
    modifier: Modifier = Modifier,
    timerProgress: Float = 1f,
    timerRemainingSeconds: Int = 30,
    showTimer: Boolean = false,
    isTimerWarning: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gameControls"),
        colors = CardDefaults.cardColors(
            containerColor = if (isTimerWarning && isMyTurn) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dice with optional timer ring
            DiceWithTimer(
                value = diceValue,
                isRolling = isRolling,
                canRoll = canRoll,
                timerProgress = timerProgress,
                remainingSeconds = timerRemainingSeconds,
                showTimer = showTimer && isMyTurn,
                isWarning = isTimerWarning,
                size = 52.dp,
                onRoll = onRollDice
            )

            // Status text - takes remaining space
            Text(
                text = when {
                    isTimerWarning && isMyTurn -> "Hurry! ${timerRemainingSeconds}s left"
                    isRolling -> "Rolling..."
                    canRoll -> "Tap dice to roll"
                    mustSelectToken -> "Select a token to move"
                    isMyTurn -> "Roll the dice to continue"
                    else -> "Waiting for other player..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isTimerWarning && isMyTurn) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isTimerWarning && isMyTurn) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("statusText")
            )
        }
    }
}

/**
 * Win dialog shown when a player wins.
 */
@Composable
fun WinDialog(
    winnerName: String,
    onDismiss: () -> Unit,
    onPlayAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Game Over!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = if (winnerName == "You") "You win!" else "$winnerName wins!",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onPlayAgain) {
                Text("Play Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Exit")
            }
        }
    )
}

/**
 * Preview/Demo version of the game board for the home screen.
 */
@Composable
fun LudoBoardPreviewLarge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color.DarkGray, RoundedCornerShape(12.dp))
    ) {
        LudoBoardCanvas(
            modifier = Modifier.fillMaxSize()
        )
    }
}
