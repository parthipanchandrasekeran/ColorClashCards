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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.ui.components.CelebrationOverlay
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoPlayer
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/**
 * Online Ludo game screen with multiplayer synchronization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoOnlineGameScreen(
    roomId: String,
    isHost: Boolean,
    onBackClick: () -> Unit,
    viewModel: LudoOnlineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Initialize on first composition
    LaunchedEffect(roomId) {
        viewModel.initialize(roomId, isHost)
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Show messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val gameState = uiState.gameState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ludo Online",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.leaveGame()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave Game"
                        )
                    }
                },
                actions = {
                    if (gameState != null) {
                        // Current turn indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("turnIndicator")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(LudoBoardColors.getColor(gameState.currentPlayer.color))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isMyTurn) "Your turn" else "${gameState.currentPlayer.name}'s turn",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.testTag("turnLabel")
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                        // All players in a single row at top
                        PlayersRow(
                            players = gameState.players,
                            currentTurnPlayerId = gameState.currentTurnPlayerId,
                            localPlayerId = uiState.localPlayerId,
                            disconnectedPlayers = uiState.disconnectedPlayers,
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

                            OnlineLudoBoardWithInteraction(
                                gameState = gameState,
                                selectableTokenIds = uiState.movableTokenIds,
                                selectedTokenId = uiState.selectedTokenId,
                                previewPath = uiState.previewPath,
                                isTokenAnimating = uiState.isTokenAnimating,
                                animatingTokenId = uiState.animatingTokenId,
                                animationProgress = uiState.animationProgress,
                                localPlayerId = uiState.localPlayerId,
                                boardSize = boardSize,
                                onTokenClick = { tokenId ->
                                    viewModel.moveToken(tokenId)
                                },
                                onBoardClick = {
                                    viewModel.clearTokenSelection()
                                }
                            )
                        }

                        // Compact dice controls at bottom with timer ring
                        CompactGameControls(
                            diceValue = uiState.diceValue,
                            isRolling = uiState.isRolling,
                            canRoll = uiState.canRoll,
                            mustSelectToken = uiState.mustSelectToken,
                            isMyTurn = uiState.isMyTurn,
                            afkWarning = uiState.afkWarning,
                            afkCountdown = uiState.afkCountdown,
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
                    AfkWarningCard(countdown = uiState.afkCountdown ?: 0)
                }
            }
        }
    }

    // Win celebration overlay
    if (uiState.showWinDialog) {
        val isWinner = uiState.winnerName == "You" ||
                       gameState?.winner?.id == uiState.localPlayerId
        val winnerName = uiState.winnerName ?: "Unknown"

        CelebrationOverlay(
            isWinner = isWinner,
            title = if (isWinner) "You Win!" else "Game Over",
            subtitle = if (isWinner) "Congratulations! You won the game!" else "$winnerName wins!",
            winnerColor = LudoBoardColors.Blue,
            secondaryAction = "Exit" to onBackClick
        )
    }

    // Game ended dialog
    if (uiState.gameEnded && !uiState.showWinDialog) {
        GameEndedDialog(
            reason = uiState.endReason ?: "Game ended",
            onDismiss = onBackClick
        )
    }
}

/**
 * Token with its player context, used for cross-player stacking.
 */
private data class OnlineTokenWithContext(
    val token: Token,
    val color: LudoColor,
    val playerId: String,
    val position: BoardPosition,
    val homeCenterCellUnits: Pair<Float, Float>? = null
)

/**
 * Ludo board with premium token interaction for online play.
 * Groups all tokens by position for correct cross-player stacking.
 */
@Composable
private fun OnlineLudoBoardWithInteraction(
    gameState: com.parthipan.colorclashcards.game.ludo.model.LudoGameState,
    selectableTokenIds: List<Int>,
    selectedTokenId: Int?,
    previewPath: List<BoardPosition>,
    isTokenAnimating: Boolean,
    animatingTokenId: Int?,
    animationProgress: Float = 0f,
    localPlayerId: String,
    boardSize: Dp,
    onTokenClick: (Int) -> Unit,
    onBoardClick: () -> Unit
) {
    val cellSizeDp = boardSize / 15
    val localPlayer = gameState.players.find { it.id == localPlayerId }

    // Collect all tokens from all players with their positions
    val allTokensWithContext = remember(gameState) {
        gameState.players.flatMap { player ->
            player.tokens.mapNotNull { token ->
                val position = getTokenBoardPosition(token, player.color)
                if (position != null) {
                    val homeCenter = if (token.state == TokenState.HOME) {
                        LudoBoardPositions.getHomeSlotOffset(player.color, token.id)
                    } else null
                    OnlineTokenWithContext(token, player.color, player.id, position, homeCenter)
                } else null
            }
        }
    }

    // Group tokens by position for stacking
    // Sort so local player's tokens render last (on top) for easier tapping
    val tokensByPosition = remember(gameState, localPlayerId) {
        allTokensWithContext.groupBy { it.position }
            .mapValues { (_, tokens) ->
                tokens.sortedBy { if (it.playerId == localPlayerId) 1 else 0 }
            }
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
        LudoBoardCanvas(modifier = Modifier.fillMaxSize())

        // Draw destination marker if a token is selected (show only final cell)
        if (selectedTokenId != null && previewPath.isNotEmpty() && localPlayer != null) {
            PathPreviewOverlay(
                pathPositions = listOf(previewPath.last()),
                tokenColor = localPlayer.color,
                cellSize = cellSizeDp
            )
        }

        // Draw tokens grouped by position with stacking offsets
        tokensByPosition.forEach { (position, tokensAtPosition) ->
            val stackCount = tokensAtPosition.size
            val (offsets, stackScale) = calculateStackOffsets(stackCount, cellSizeDp)

            tokensAtPosition.forEachIndexed { index, tokenContext ->
                val offset = offsets.getOrElse(index) { Pair(0.dp, 0.dp) }
                val isLocalPlayer = tokenContext.playerId == localPlayerId
                val token = tokenContext.token
                val isSelected = token.id == selectedTokenId && isLocalPlayer
                val isThisTokenAnimating = isTokenAnimating && token.id == animatingTokenId && isLocalPlayer

                // Animate straight line from current position to destination
                val (fromPos, toPos, segmentProgress) = if (isThisTokenAnimating && previewPath.isNotEmpty()) {
                    Triple(position, previewPath.last(), animationProgress)
                } else {
                    Triple(null, null, 0f)
                }

                val homeCenterDp = tokenContext.homeCenterCellUnits?.let { (cx, cy) ->
                    Pair(cx * cellSizeDp.value, cy * cellSizeDp.value)
                }

                PremiumTokenView(
                    token = token,
                    color = tokenContext.color,
                    isSelectable = isLocalPlayer && token.id in selectableTokenIds,
                    isSelected = isSelected,
                    isAnimating = isThisTokenAnimating,
                    animationProgress = segmentProgress,
                    fromPosition = fromPos,
                    toPosition = toPos,
                    cellSize = cellSizeDp,
                    boardPosition = position,
                    stackOffset = offset,
                    stackScale = stackScale,
                    homeCenterDp = homeCenterDp,
                    onClick = {
                        if (isLocalPlayer) {
                            onTokenClick(token.id)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Horizontal row of all players shown as compact chips.
 * Single source of truth: currentTurnPlayerId determines which player is highlighted.
 */
@Composable
private fun PlayersRow(
    players: List<LudoPlayer>,
    currentTurnPlayerId: String,
    localPlayerId: String,
    disconnectedPlayers: Set<String>,
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
            val isDisconnected = player.id in disconnectedPlayers

            SharedPlayerChip(
                player = player,
                displayName = if (isLocalPlayer) "You" else player.name,
                isCurrentTurn = isCurrentTurn,
                isDisconnected = isDisconnected,
                modifier = Modifier.testTag("playerItem_${player.id}")
            )
        }
    }
}

/**
 * Compact game controls with dice and timer ring - reduced height, rounded card.
 */
@Composable
private fun CompactGameControls(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    mustSelectToken: Boolean,
    isMyTurn: Boolean,
    afkWarning: Boolean,
    afkCountdown: Int?,
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
            containerColor = if (afkWarning) {
                MaterialTheme.colorScheme.errorContainer
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
                showTimer = showTimer && isMyTurn,
                isWarning = isTimerWarning,
                size = 52.dp,
                onRoll = onRollDice
            )

            // Status text - takes remaining space
            Text(
                text = when {
                    afkWarning && afkCountdown != null -> "Move in $afkCountdown seconds!"
                    isRolling -> "Rolling..."
                    canRoll -> "Tap dice to roll"
                    mustSelectToken -> "Select a token to move"
                    isMyTurn -> "Waiting for dice roll..."
                    else -> "Waiting for other player..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (afkWarning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (afkWarning) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("statusText")
            )
        }
    }
}

/**
 * AFK warning card.
 */
@Composable
private fun AfkWarningCard(countdown: Int) {
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
 * Game ended dialog (for dropouts).
 */
@Composable
private fun GameEndedDialog(
    reason: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("gameEndedDialog"),
        title = {
            Text(
                text = "Game Ended",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gameEndedReason")
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Exit")
            }
        }
    )
}
