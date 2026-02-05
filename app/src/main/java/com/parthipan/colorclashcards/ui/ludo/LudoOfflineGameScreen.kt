package com.parthipan.colorclashcards.ui.ludo

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
            rankings = uiState.rankings,
            onDismiss = onBackClick,
            onPlayAgain = {
                viewModel.initializeGame(botCount, difficulty)
            }
        )
    }
}

/**
 * Data class to hold token info with its player context for stacking calculations.
 *
 * @property homeCenterCellUnits For HOME tokens: absolute center (x, y) in cellSize
 *           units, computed via [LudoBoardPositions.getHomeSlotOffset]. When set,
 *           the renderer uses this instead of deriving position from [position].
 */
private data class TokenWithContext(
    val token: Token,
    val color: LudoColor,
    val playerId: String,
    val position: BoardPosition,
    val homeCenterCellUnits: Pair<Float, Float>? = null
)

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

    // Collect all tokens from all players with their positions
    // Use gameState as key to ensure recalculation when any token moves
    val allTokensWithContext = remember(gameState) {
        gameState.players.flatMap { player ->
            player.tokens.mapNotNull { token ->
                val position = getTokenBoardPosition(token, player.color)
                if (position != null) {
                    // HOME tokens: compute stable center from getHomeSlotOffset
                    val homeCenter = if (token.state == TokenState.HOME) {
                        LudoBoardPositions.getHomeSlotOffset(player.color, token.id)
                    } else null
                    TokenWithContext(token, player.color, player.id, position, homeCenter)
                } else null
            }
        }
    }

    // Group tokens by position for stacking
    val tokensByPosition = remember(gameState) {
        allTokensWithContext.groupBy { it.position }
    }

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

        // Draw tokens grouped by position with stacking offsets
        tokensByPosition.forEach { (position, tokensAtPosition) ->
            val stackCount = tokensAtPosition.size
            val (offsets, stackScale) = calculateStackOffsets(stackCount, cellSizeDp)

            tokensAtPosition.forEachIndexed { index, tokenContext ->
                val offset = offsets.getOrElse(index) { Pair(0.dp, 0.dp) }
                val isHumanPlayer = tokenContext.playerId == humanPlayerId
                val token = tokenContext.token
                val isSelected = token.id == selectedTokenId && isHumanPlayer
                val isThisTokenAnimating = isTokenAnimating && token.id == animatingTokenId && isHumanPlayer
                val targetPosition = if (isThisTokenAnimating && previewPath.isNotEmpty()) {
                    previewPath.last()
                } else null

                // For HOME tokens, pass absolute center in dp for precise positioning
                val homeCenterDp = tokenContext.homeCenterCellUnits?.let { (cx, cy) ->
                    Pair(cx * cellSizeDp.value, cy * cellSizeDp.value)
                }

                PremiumTokenView(
                    token = token,
                    color = tokenContext.color,
                    isSelectable = isHumanPlayer && token.id in selectableTokenIds,
                    isSelected = isSelected,
                    isAnimating = isThisTokenAnimating,
                    animationProgress = if (isThisTokenAnimating) 1f else 0f,
                    fromPosition = if (isThisTokenAnimating) position else null,
                    toPosition = targetPosition,
                    cellSize = cellSizeDp,
                    boardPosition = position,
                    stackOffset = offset,
                    stackScale = stackScale,
                    homeCenterDp = homeCenterDp,
                    onClick = {
                        if (isHumanPlayer) {
                            onTokenClick(token.id)
                        }
                    }
                )
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
                    isHumanTurn -> "Roll the dice to continue"
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

            SharedPlayerChip(
                player = player,
                displayName = if (isHumanPlayer) "You" else player.name,
                isCurrentTurn = isCurrentTurn,
                modifier = Modifier.testTag("playerItem_${player.id}")
            )
        }
    }
}

/**
 * Win dialog for offline game.
 */
@Composable
private fun OfflineWinDialog(
    winnerName: String,
    rankings: List<Pair<String, String>>? = null,
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (winnerName == "You") "You win!" else "$winnerName wins!",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (rankings != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    rankings.forEach { (rank, name) ->
                        Text(
                            text = "$rank  $name",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
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
