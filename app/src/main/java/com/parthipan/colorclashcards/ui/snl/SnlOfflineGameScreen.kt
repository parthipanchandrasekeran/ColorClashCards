package com.parthipan.colorclashcards.ui.snl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.audio.LocalSoundManager
import com.parthipan.colorclashcards.audio.SoundEffect
import com.parthipan.colorclashcards.game.snl.model.SnlBoardLayout
import com.parthipan.colorclashcards.game.snl.model.SnlPlayer
import com.parthipan.colorclashcards.ui.components.CelebrationOverlay
import com.parthipan.colorclashcards.ui.ludo.DiceWithTimer

/**
 * Offline Snake & Ladder game screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnlOfflineGameScreen(
    botCount: Int,
    difficulty: String,
    colorIndex: Int = 0,
    boardLayout: String = "CLASSIC",
    onBackClick: () -> Unit,
    viewModel: SnlOfflineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val soundManager = LocalSoundManager.current

    // Initialize game on first composition
    LaunchedEffect(Unit) {
        val layout = try { SnlBoardLayout.valueOf(boardLayout) } catch (_: Exception) { SnlBoardLayout.CLASSIC }
        viewModel.initializeGame(botCount, difficulty, colorIndex, layout)
    }

    // Token move sounds
    LaunchedEffect(uiState.isAnimating) {
        if (uiState.isAnimating) {
            soundManager.play(SoundEffect.TOKEN_MOVE)
        }
    }

    // Win sounds
    LaunchedEffect(uiState.showWinDialog) {
        if (uiState.showWinDialog) {
            val isWinner = uiState.winnerName == "You"
            if (isWinner) soundManager.play(SoundEffect.WIN_FANFARE)
            else soundManager.play(SoundEffect.LOSE_SOUND)
        }
    }

    // Background music
    DisposableEffect(Unit) {
        soundManager.startMusic()
        onDispose { soundManager.stopMusic() }
    }

    val gameState = uiState.gameState

    val topBarColor by animateColorAsState(
        targetValue = if (gameState != null) {
            SnlBoardColors.getPlayerColor(gameState.currentPlayer.colorIndex)
        } else {
            SnlBoardColors.Primary
        },
        animationSpec = tween(500),
        label = "topBarColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Snake & Ladder",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(SnlBoardColors.getPlayerColor(gameState.currentPlayer.colorIndex))
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
                    containerColor = topBarColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (gameState == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading...")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Player chips row
                SnlPlayersRow(
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

                    Box(
                        modifier = Modifier
                            .size(boardSize)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                            .testTag("snlBoard")
                    ) {
                        SnlBoardCanvas(
                            gameState = gameState,
                            animatingPlayerId = uiState.animatingPlayerId,
                            animFromPos = uiState.animFromPos,
                            animToPos = uiState.animToPos,
                            animationProgress = uiState.animationProgress,
                            isAnimating = uiState.isAnimating,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Controls
                SnlCompactControls(
                    diceValue = uiState.diceValue,
                    isRolling = uiState.isRolling,
                    canRoll = uiState.canRoll,
                    isHumanTurn = uiState.isHumanTurn,
                    message = uiState.message,
                    timerProgress = uiState.timerProgress,
                    timerRemainingSeconds = uiState.timerRemainingSeconds,
                    showTimer = uiState.showTimer,
                    isTimerWarning = uiState.isTimerWarning,
                    onRollDice = { viewModel.rollDice() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }

    // Win celebration overlay
    if (uiState.showWinDialog) {
        val isHumanWinner = uiState.winnerName == "You"
        val winnerName = uiState.winnerName ?: "Unknown"

        CelebrationOverlay(
            isWinner = isHumanWinner,
            title = if (isHumanWinner) "You Win!" else "Game Over",
            subtitle = if (isHumanWinner) "Congratulations!" else "$winnerName wins!",
            winnerColor = SnlBoardColors.Primary,
            rankings = uiState.rankings,
            primaryAction = "Play Again" to {
                val layout = try { SnlBoardLayout.valueOf(boardLayout) } catch (_: Exception) { SnlBoardLayout.CLASSIC }
                viewModel.initializeGame(botCount, difficulty, colorIndex, layout)
            },
            secondaryAction = "Exit" to onBackClick
        )
    }
}

// ==================== SHARED COMPONENTS ====================

/**
 * Horizontal row of all SNL players shown as compact chips.
 */
@Composable
fun SnlPlayersRow(
    players: List<SnlPlayer>,
    currentTurnPlayerId: String,
    humanPlayerId: String? = null,
    localPlayerId: String? = null,
    disconnectedPlayers: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    if (players.isEmpty()) return

    LazyRow(
        modifier = modifier.testTag("snlPlayersRow"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(players, key = { it.id }) { player ->
            val isCurrentTurn = player.id == currentTurnPlayerId
            val isLocalPlayer = player.id == humanPlayerId || player.id == localPlayerId
            val isDisconnected = player.id in disconnectedPlayers

            SnlPlayerChip(
                player = player,
                displayName = if (isLocalPlayer) "You" else player.name,
                isCurrentTurn = isCurrentTurn,
                isDisconnected = isDisconnected,
                modifier = Modifier.testTag("snlPlayerItem_${player.id}")
            )
        }
    }
}

@Composable
private fun SnlPlayerChip(
    player: SnlPlayer,
    displayName: String,
    isCurrentTurn: Boolean,
    isDisconnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playerColor = SnlBoardColors.getPlayerColor(player.colorIndex)
    val bgColor = if (isCurrentTurn) {
        playerColor.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentTurn) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCurrentTurn) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (isDisconnected) Color.Gray else playerColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDisconnected) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (player.hasWon) "Finished!" else "Pos: ${if (player.position == 0) "Start" else "${player.position}"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Compact game controls with dice, timer ring, and status text.
 * Reused by both offline and online screens.
 */
@Composable
fun SnlCompactControls(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
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
            .testTag("snlGameControls")
            .then(
                if (canRoll && !isRolling) Modifier.clickable(onClick = onRollDice)
                else Modifier
            ),
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
            // Reuse Ludo's DiceWithTimer
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
                    canRoll -> "Tap to roll"
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
                    .testTag("snlStatusText")
            )
        }
    }
}
