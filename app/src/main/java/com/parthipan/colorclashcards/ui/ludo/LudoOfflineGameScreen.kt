package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoPlayer
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/**
 * Offline Ludo game screen using local game engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoOfflineGameScreen(
    botCount: Int,
    difficulty: String,
    onBackClick: () -> Unit,
    viewModel: LudoOfflineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize game on first composition
    LaunchedEffect(Unit) {
        viewModel.initializeGame(botCount, difficulty)
    }

    val gameState = uiState.gameState

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
                    if (gameState != null) {
                        // Current turn indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(LudoBoardColors.getColor(gameState.currentPlayer.color))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isHumanTurn) "Your turn" else "${gameState.currentPlayer.name}'s turn",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (gameState != null) {
                        LudoBoardColors.getColor(gameState.currentPlayer.color)
                    } else {
                        LudoBoardColors.Green
                    },
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (gameState == null) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading game...")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // All players in a single row at top
                OfflineBotPlayersRow(
                    players = gameState.players,
                    currentTurnPlayerId = gameState.currentTurnPlayerId,
                    humanPlayerId = uiState.humanPlayerId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Game board
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val boardSize = minOf(maxWidth, maxHeight)

                    OfflineLudoBoardWithInteraction(
                        gameState = gameState,
                        selectableTokenIds = uiState.movableTokenIds,
                        selectedTokenId = uiState.selectedTokenId,
                        previewPath = uiState.previewPath,
                        isTokenAnimating = uiState.isTokenAnimating,
                        animatingTokenId = uiState.animatingTokenId,
                        humanPlayerId = uiState.humanPlayerId,
                        boardSize = boardSize,
                        onTokenClick = { tokenId ->
                            viewModel.selectToken(tokenId)
                        },
                        onBoardClick = {
                            viewModel.clearTokenSelection()
                        }
                    )
                }

                // Compact controls area at bottom with timer ring
                OfflineCompactControls(
                    diceValue = uiState.diceValue,
                    isRolling = uiState.isRolling,
                    canRoll = uiState.canRoll,
                    mustSelectToken = uiState.mustSelectToken,
                    isHumanTurn = uiState.isHumanTurn,
                    message = uiState.message,
                    timerProgress = uiState.timerProgress,
                    timerRemainingSeconds = uiState.timerRemainingSeconds,
                    showTimer = uiState.showTimer,
                    isTimerWarning = uiState.isTimerWarning,
                    onRollDice = { viewModel.rollDice() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Win dialog
    if (uiState.showWinDialog) {
        OfflineWinDialog(
            winnerName = uiState.winnerName ?: "Unknown",
            onDismiss = onBackClick,
            onPlayAgain = {
                viewModel.initializeGame(botCount, difficulty)
            }
        )
    }
}

/**
 * Ludo board with premium token interaction.
 */
@Composable
private fun OfflineLudoBoardWithInteraction(
    gameState: com.parthipan.colorclashcards.game.ludo.model.LudoGameState,
    selectableTokenIds: List<Int>,
    selectedTokenId: Int?,
    previewPath: List<BoardPosition>,
    isTokenAnimating: Boolean,
    animatingTokenId: Int?,
    humanPlayerId: String,
    boardSize: Dp,
    onTokenClick: (Int) -> Unit,
    onBoardClick: () -> Unit
) {
    val cellSizeDp = boardSize / 15
    val humanPlayer = gameState.players.find { it.id == humanPlayerId }

    Box(
        modifier = Modifier
            .size(boardSize)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color.DarkGray, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBoardClick
            )
            .testTag("ludoBoard")
    ) {
        // Draw the board
        LudoBoardCanvas(
            modifier = Modifier.fillMaxSize()
        )

        // Draw path preview if a token is selected
        if (selectedTokenId != null && previewPath.isNotEmpty() && humanPlayer != null) {
            PathPreviewOverlay(
                pathPositions = previewPath,
                tokenColor = humanPlayer.color,
                cellSize = cellSizeDp
            )
        }

        // Draw tokens for each player
        gameState.players.forEach { player ->
            val isHumanPlayer = player.id == humanPlayerId
            val tokenSelectableIds = if (isHumanPlayer) selectableTokenIds else emptyList()

            OfflinePremiumPlayerTokens(
                tokens = player.tokens,
                color = player.color,
                selectableTokenIds = tokenSelectableIds,
                selectedTokenId = if (isHumanPlayer) selectedTokenId else null,
                isAnimating = isTokenAnimating,
                animatingTokenId = if (isHumanPlayer) animatingTokenId else null,
                previewPath = if (isHumanPlayer) previewPath else emptyList(),
                cellSize = cellSizeDp,
                onTokenClick = { tokenId ->
                    if (isHumanPlayer) {
                        onTokenClick(tokenId)
                    }
                }
            )
        }
    }
}

/**
 * Premium token overlay with animations for a single player.
 */
@Composable
private fun OfflinePremiumPlayerTokens(
    tokens: List<Token>,
    color: LudoColor,
    selectableTokenIds: List<Int>,
    selectedTokenId: Int?,
    isAnimating: Boolean,
    animatingTokenId: Int?,
    previewPath: List<BoardPosition>,
    cellSize: Dp,
    onTokenClick: (Int) -> Unit
) {
    tokens.forEach { token ->
        val position = getOfflineTokenBoardPosition(token, color)
        if (position != null) {
            val isSelected = token.id == selectedTokenId
            val isThisTokenAnimating = isAnimating && token.id == animatingTokenId
            val targetPosition = if (isThisTokenAnimating && previewPath.isNotEmpty()) {
                previewPath.last()
            } else null

            PremiumTokenView(
                token = token,
                color = color,
                isSelectable = token.id in selectableTokenIds,
                isSelected = isSelected,
                isAnimating = isThisTokenAnimating,
                animationProgress = if (isThisTokenAnimating) 1f else 0f,
                fromPosition = if (isThisTokenAnimating) position else null,
                toPosition = targetPosition,
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
private fun getOfflineTokenBoardPosition(token: Token, color: LudoColor): BoardPosition? {
    return when (token.state) {
        TokenState.HOME -> {
            LudoBoardPositions.getHomeBasePositions(color).getOrNull(token.id)
        }
        TokenState.ACTIVE -> {
            LudoBoardPositions.getGridPosition(token.position, color)
        }
        TokenState.FINISHED -> {
            val center = LudoBoardPositions.getFinishPosition()
            when (color) {
                LudoColor.RED -> BoardPosition(center.column - 1, center.row - 1)
                LudoColor.BLUE -> BoardPosition(center.column + 1, center.row - 1)
                LudoColor.GREEN -> BoardPosition(center.column + 1, center.row + 1)
                LudoColor.YELLOW -> BoardPosition(center.column - 1, center.row + 1)
            }
        }
    }
}

/**
 * Compact game controls with dice, timer ring, and status - reduced height, horizontal layout.
 */
@Composable
private fun OfflineCompactControls(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    mustSelectToken: Boolean,
    isHumanTurn: Boolean,
    message: String?,
    timerProgress: Float,
    timerRemainingSeconds: Int,
    showTimer: Boolean,
    isTimerWarning: Boolean,
    onRollDice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gameControls"),
        colors = CardDefaults.cardColors(
            containerColor = if (isTimerWarning && isHumanTurn) {
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
            // Dice with timer ring
            DiceWithTimer(
                value = diceValue,
                isRolling = isRolling,
                canRoll = canRoll,
                timerProgress = timerProgress,
                remainingSeconds = timerRemainingSeconds,
                showTimer = showTimer && isHumanTurn,
                isWarning = isTimerWarning,
                size = 52.dp,
                onRoll = onRollDice
            )

            // Status text
            Text(
                text = message ?: when {
                    isTimerWarning && isHumanTurn -> "Hurry! ${timerRemainingSeconds}s left"
                    isRolling -> "Rolling..."
                    canRoll -> "Tap dice to roll"
                    mustSelectToken -> "Select a token to move"
                    isHumanTurn -> "Waiting for dice roll..."
                    else -> "Bot is thinking..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isTimerWarning && isHumanTurn) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isTimerWarning && isHumanTurn) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("statusText")
            )
        }
    }
}

/**
 * Horizontal row of all players shown as compact chips.
 */
@Composable
private fun OfflineBotPlayersRow(
    players: List<LudoPlayer>,
    currentTurnPlayerId: String,
    humanPlayerId: String,
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
            val isHumanPlayer = player.id == humanPlayerId

            OfflineBotPlayerChip(
                player = player,
                displayName = if (isHumanPlayer) "You" else player.name,
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
private fun OfflineBotPlayerChip(
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
                OfflineBotTokenCountBadge(
                    count = homeCount,
                    color = playerColor.copy(alpha = 0.4f)
                )
                OfflineBotTokenCountBadge(
                    count = activeCount,
                    color = playerColor
                )
                OfflineBotTokenCountBadge(
                    count = finishedCount,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

/**
 * Small badge showing token count.
 */
@Composable
private fun OfflineBotTokenCountBadge(
    count: Int,
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
 * Win dialog for offline game.
 */
@Composable
private fun OfflineWinDialog(
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
                text = "$winnerName wins!",
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
