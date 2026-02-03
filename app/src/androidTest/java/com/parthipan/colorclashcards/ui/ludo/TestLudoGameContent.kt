package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoPlayer
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/**
 * Test version of Ludo game content that accepts state directly.
 * Used for UI testing without requiring a ViewModel.
 */
@Composable
fun TestLudoGameContent(
    uiState: LudoOnlineUiState,
    onRollDice: () -> Unit = {},
    onTokenClick: (Int) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val gameState = uiState.gameState

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading game...")
                        }
                    }
                }

                gameState == null -> {
                    // Error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Failed to load game")
                    }
                }

                else -> {
                    // Game content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Turn indicator header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LudoBoardColors.getColor(gameState.currentPlayer.color))
                                .padding(16.dp)
                                .testTag("turnIndicator")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isMyTurn) "Your turn" else "${gameState.currentPlayer.name}'s turn",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("turnLabel")
                            )
                        }

                        // Top players
                        TestPlayerInfoRow(
                            players = gameState.players.filter { it.color == LudoColor.RED || it.color == LudoColor.BLUE },
                            currentPlayerId = gameState.currentTurnPlayerId,
                            disconnectedPlayers = uiState.disconnectedPlayers,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        // Game board
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val boardSize = minOf(maxWidth, maxHeight)

                            TestLudoBoard(
                                gameState = gameState,
                                selectableTokenIds = uiState.movableTokenIds,
                                localPlayerId = uiState.localPlayerId,
                                boardSize = boardSize,
                                onTokenClick = onTokenClick
                            )
                        }

                        // Bottom players
                        TestPlayerInfoRow(
                            players = gameState.players.filter { it.color == LudoColor.YELLOW || it.color == LudoColor.GREEN },
                            currentPlayerId = gameState.currentTurnPlayerId,
                            disconnectedPlayers = uiState.disconnectedPlayers,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        // Controls
                        TestGameControls(
                            diceValue = uiState.diceValue,
                            isRolling = uiState.isRolling,
                            canRoll = uiState.canRoll,
                            mustSelectToken = uiState.mustSelectToken,
                            isMyTurn = uiState.isMyTurn,
                            afkWarning = uiState.afkWarning,
                            afkCountdown = uiState.afkCountdown,
                            onRollDice = onRollDice,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // AFK Warning overlay
            AnimatedVisibility(
                visible = uiState.afkWarning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    TestAfkWarningCard(countdown = uiState.afkCountdown ?: 0)
                }
            }
        }

        // Win dialog
        if (uiState.showWinDialog) {
            TestWinDialog(
                winnerName = uiState.winnerName ?: "Unknown",
                isWinner = uiState.winnerName == "You" ||
                           gameState?.winner?.id == uiState.localPlayerId,
                onDismiss = onBackClick
            )
        }
    }
}

/**
 * Test version of Ludo board.
 */
@Composable
private fun TestLudoBoard(
    gameState: com.parthipan.colorclashcards.game.ludo.model.LudoGameState,
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
            .background(Color(0xFFF5E6D3))
            .testTag("ludoBoard")
    ) {
        gameState.players.forEach { player ->
            val isLocalPlayer = player.id == localPlayerId
            val tokenSelectableIds = if (isLocalPlayer) selectableTokenIds else emptyList()

            TestPlayerTokens(
                tokens = player.tokens,
                color = player.color,
                selectableTokenIds = tokenSelectableIds,
                cellSize = cellSizeDp,
                onTokenClick = { tokenId ->
                    if (isLocalPlayer && tokenId in selectableTokenIds) {
                        onTokenClick(tokenId)
                    }
                }
            )
        }
    }
}

/**
 * Test version of player tokens.
 */
@Composable
private fun TestPlayerTokens(
    tokens: List<Token>,
    color: LudoColor,
    selectableTokenIds: List<Int>,
    cellSize: Dp,
    onTokenClick: (Int) -> Unit
) {
    tokens.forEach { token ->
        val position = getTestTokenPosition(token, color)
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
 * Get token position for test.
 */
private fun getTestTokenPosition(token: Token, color: LudoColor): BoardPosition? {
    return when (token.state) {
        TokenState.HOME -> {
            // Simple home positions for testing
            val baseRow = when (color) {
                LudoColor.RED -> 1
                LudoColor.BLUE -> 1
                LudoColor.GREEN -> 10
                LudoColor.YELLOW -> 10
            }
            val baseCol = when (color) {
                LudoColor.RED -> 1
                LudoColor.BLUE -> 10
                LudoColor.GREEN -> 10
                LudoColor.YELLOW -> 1
            }
            BoardPosition(baseCol + (token.id % 2), baseRow + (token.id / 2))
        }
        TokenState.ACTIVE -> {
            // Simple active position calculation
            BoardPosition(7, 7) // Center for simplicity
        }
        TokenState.FINISHED -> {
            BoardPosition(7, 7)
        }
    }
}

/**
 * Test player info row.
 */
@Composable
private fun TestPlayerInfoRow(
    players: List<LudoPlayer>,
    currentPlayerId: String,
    disconnectedPlayers: Set<String>,
    modifier: Modifier = Modifier
) {
    if (players.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        players.forEach { player ->
            TestPlayerInfoCard(
                player = player,
                isCurrentTurn = player.id == currentPlayerId,
                isDisconnected = player.id in disconnectedPlayers,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Test player card.
 */
@Composable
private fun TestPlayerInfoCard(
    player: LudoPlayer,
    isCurrentTurn: Boolean,
    isDisconnected: Boolean,
    modifier: Modifier = Modifier
) {
    val playerColor = LudoBoardColors.getColor(player.color)
    val finishedCount = player.tokens.count { it.state == TokenState.FINISHED }

    Card(
        modifier = modifier
            .padding(4.dp)
            .testTag("playerCard_${player.id}")
            .then(
                if (isCurrentTurn) {
                    Modifier.border(2.dp, playerColor, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDisconnected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                isCurrentTurn -> playerColor.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDisconnected) Color.Gray else playerColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        color = if (isDisconnected) Color.Gray else Color.Unspecified
                    )

                    if (isDisconnected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnected",
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("disconnectedIcon_${player.id}"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Text(
                    text = if (isDisconnected) "Reconnecting..." else "$finishedCount/4 home",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDisconnected) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
            }
        }
    }
}

/**
 * Test game controls.
 */
@Composable
private fun TestGameControls(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    mustSelectToken: Boolean,
    isMyTurn: Boolean,
    afkWarning: Boolean,
    afkCountdown: Int?,
    onRollDice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gameControls"),
        colors = CardDefaults.cardColors(
            containerColor = if (afkWarning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LudoDiceView(
                value = diceValue,
                isRolling = isRolling,
                canRoll = canRoll,
                size = 72.dp,
                onRoll = onRollDice
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    afkWarning && afkCountdown != null -> "Move in $afkCountdown seconds!"
                    isRolling -> "Rolling..."
                    canRoll -> "Tap dice to roll"
                    mustSelectToken -> "Select a token to move"
                    isMyTurn -> "Your turn"
                    else -> "Waiting for other player..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (afkWarning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                fontWeight = if (afkWarning) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.testTag("statusText")
            )
        }
    }
}

/**
 * Test AFK warning card.
 */
@Composable
private fun TestAfkWarningCard(countdown: Int) {
    Card(
        modifier = Modifier.testTag("afkWarningCard"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Are you still there?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your turn will be skipped in",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "$countdown seconds",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("afkCountdown")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Make a move to continue playing!",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Test win dialog.
 */
@Composable
private fun TestWinDialog(
    winnerName: String,
    isWinner: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("winDialog"),
        title = {
            Text(
                text = if (isWinner) "You Win!" else "Game Over",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("winDialogTitle")
            )
        },
        text = {
            Text(
                text = if (isWinner) {
                    "Congratulations! You won the game!"
                } else {
                    "$winnerName wins!"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Exit")
            }
        }
    )
}
