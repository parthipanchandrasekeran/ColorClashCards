package com.parthipan.colorclashcards.ui.snl

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.audio.LocalSoundManager
import com.parthipan.colorclashcards.audio.SoundEffect
import com.parthipan.colorclashcards.ui.components.CelebrationOverlay

/**
 * Online Snake & Ladder game screen with multiplayer sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnlOnlineGameScreen(
    roomId: String,
    isHost: Boolean,
    onBackClick: () -> Unit,
    viewModel: SnlOnlineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val soundManager = LocalSoundManager.current

    // Initialize on first composition
    LaunchedEffect(roomId) {
        viewModel.initialize(roomId, isHost)
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
            val isWinner = uiState.gameState?.winner?.id == uiState.localPlayerId
            if (isWinner) soundManager.play(SoundEffect.WIN_FANFARE)
            else soundManager.play(SoundEffect.LOSE_SOUND)
        }
    }

    // Background music
    DisposableEffect(Unit) {
        soundManager.startMusic()
        onDispose { soundManager.stopMusic() }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val gameState = uiState.gameState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Snake & Ladder Online",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("snlTurnIndicator")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(SnlBoardColors.getPlayerColor(gameState.currentPlayer.colorIndex))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isMyTurn) "Your turn" else "${gameState.currentPlayer.name}'s turn",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.testTag("snlTurnLabel")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (gameState != null) {
                        SnlBoardColors.getPlayerColor(gameState.currentPlayer.colorIndex)
                    } else {
                        SnlBoardColors.Primary
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Failed to load game")
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Players row
                        SnlPlayersRow(
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

                            Box(
                                modifier = Modifier
                                    .size(boardSize)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(2.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                                    .testTag("snlOnlineBoard")
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

                        // Controls - reuse the shared compact controls
                        SnlCompactControls(
                            diceValue = uiState.diceValue,
                            isRolling = uiState.isRolling,
                            canRoll = uiState.canRoll,
                            isHumanTurn = uiState.isMyTurn,
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
        }
    }

    // Win celebration overlay
    if (uiState.showWinDialog) {
        val isWinner = gameState?.winner?.id == uiState.localPlayerId
        val winnerName = uiState.winnerName ?: "Unknown"

        CelebrationOverlay(
            isWinner = isWinner,
            title = if (isWinner) "You Win!" else "Game Over",
            subtitle = if (isWinner) "Congratulations! You won the game!" else "$winnerName wins!",
            winnerColor = SnlBoardColors.Primary,
            secondaryAction = "Exit" to onBackClick
        )
    }

    // Game ended dialog
    if (uiState.gameEnded && !uiState.showWinDialog) {
        SnlGameEndedDialog(
            reason = uiState.endReason ?: "Game ended",
            onDismiss = onBackClick
        )
    }
}

@Composable
private fun SnlGameEndedDialog(
    reason: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("snlGameEndedDialog"),
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
