package com.parthipan.colorclashcards.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.game.model.Card
import com.parthipan.colorclashcards.game.model.CardColor
import com.parthipan.colorclashcards.game.model.CardType
import com.parthipan.colorclashcards.game.model.TurnPhase
import com.parthipan.colorclashcards.ui.components.GameCardView
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(
    roomId: String,
    isHost: Boolean,
    onBackClick: () -> Unit,
    viewModel: OnlineGameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Initialize game
    LaunchedEffect(roomId, isHost) {
        viewModel.initialize(roomId, isHost)
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Online Game",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit"
                        )
                    }
                },
                actions = {
                    // Host indicator
                    if (uiState.isHost) {
                        Text(
                            text = "HOST",
                            style = MaterialTheme.typography.labelSmall,
                            color = CardYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBlue,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting to game...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (uiState.publicState == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Waiting for game to start...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top section: Players info
                    OnlinePlayersInfoBar(
                        players = uiState.players,
                        currentUserId = uiState.currentUserId,
                        modifier = Modifier.padding(8.dp)
                    )

                    // Middle section: Game area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Current turn indicator
                            OnlineTurnIndicator(
                                currentPlayer = uiState.players.find { it.isCurrentTurn },
                                isMyTurn = uiState.isMyTurn
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Discard pile and draw pile
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Draw pile
                                OnlineDrawPile(
                                    cardsRemaining = uiState.publicState?.drawPileCount ?: 0,
                                    canDraw = uiState.isMyTurn && uiState.turnPhase != TurnPhase.MUST_DRAW,
                                    mustDraw = uiState.turnPhase == TurnPhase.MUST_DRAW && uiState.isMyTurn,
                                    onClick = { viewModel.drawCard() }
                                )

                                // Discard pile with current color
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    uiState.topCard?.let { topCard ->
                                        GameCardView(
                                            card = topCard,
                                            modifier = Modifier.width(100.dp),
                                            faceDown = false,
                                            isPlayable = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OnlineColorIndicator(color = uiState.currentColor)
                                }
                            }

                            // Turn phase indicator
                            AnimatedVisibility(
                                visible = uiState.turnPhase == TurnPhase.MUST_DRAW && uiState.isMyTurn
                            ) {
                                Text(
                                    text = "You must draw cards!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CardRed,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    }

                    // Bottom section: Player's hand and Last Card button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                            .padding(16.dp)
                    ) {
                        // Last Card button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Hand (${uiState.myHand.size} cards)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { viewModel.callLastCard() },
                                enabled = uiState.myHand.size == 1 && uiState.isMyTurn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardRed,
                                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                                )
                            ) {
                                Text("Last Card!")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Player's hand
                        OnlinePlayerHand(
                            hand = uiState.myHand,
                            playableCards = uiState.playableCards,
                            isMyTurn = uiState.isMyTurn,
                            onCardClick = { card -> viewModel.playCard(card) }
                        )
                    }
                }
            }

            // Color picker dialog
            if (uiState.showColorPicker) {
                OnlineColorPickerDialog(
                    onColorSelected = { color -> viewModel.selectWildColor(color) },
                    onDismiss = { viewModel.cancelColorSelection() }
                )
            }

            // Win dialog
            uiState.winner?.let { winner ->
                OnlineWinDialog(
                    winnerName = winner.name,
                    isCurrentUserWinner = winner.id == uiState.currentUserId,
                    onExit = onBackClick
                )
            }
        }
    }
}

@Composable
private fun OnlinePlayersInfoBar(
    players: List<OnlinePlayerState>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        players.forEach { player ->
            val isMe = player.id == currentUserId

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        player.isCurrentTurn -> CardYellow.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier.border(
                    width = if (player.isCurrentTurn) 2.dp else 0.dp,
                    color = if (player.isCurrentTurn) CardYellow else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isMe) "You" else player.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (player.isCurrentTurn) FontWeight.Bold else FontWeight.Normal
                        )
                        if (player.isBot) {
                            Text(
                                text = "BOT",
                                style = MaterialTheme.typography.labelSmall,
                                color = CardBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${player.handSize} cards",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (player.handSize == 1 && !player.hasCalledLastCard) {
                        Text(
                            text = "LAST CARD!",
                            style = MaterialTheme.typography.labelSmall,
                            color = CardRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTurnIndicator(
    currentPlayer: OnlinePlayerState?,
    isMyTurn: Boolean
) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (isMyTurn) 10.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isMyTurn) CardGreen else Color.Black,
                spotColor = if (isMyTurn) CardGreen else Color.Black
            )
            .background(
                brush = if (isMyTurn) {
                    Brush.linearGradient(listOf(CardGreen, CardGreen.copy(alpha = 0.85f)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)))
                },
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 2.dp,
                color = if (isMyTurn) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isMyTurn && currentPlayer != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Text(
                text = when {
                    isMyTurn -> "YOUR TURN"
                    currentPlayer?.isBot == true -> "${currentPlayer.name} thinking..."
                    currentPlayer != null -> "${currentPlayer.name}'s turn"
                    else -> "Waiting..."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (isMyTurn) 17.sp else 15.sp,
                color = Color.White,
                letterSpacing = if (isMyTurn) 1.sp else 0.sp
            )
        }
    }
}

@Composable
private fun OnlineDrawPile(
    cardsRemaining: Int,
    canDraw: Boolean,
    mustDraw: Boolean,
    onClick: () -> Unit
) {
    val dummyCard = remember {
        Card(id = "draw-pile", color = CardColor.RED, type = CardType.NUMBER, number = 0)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Stacked cards effect
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .offset(x = ((1 - index) * 3).dp, y = ((1 - index) * 3).dp)
                        .width(65.dp)
                ) {
                    GameCardView(
                        card = dummyCard,
                        modifier = Modifier.width(65.dp),
                        faceDown = true,
                        isPlayable = false,
                        onClick = null
                    )
                }
            }

            // Top interactive card
            Box(
                modifier = Modifier
                    .then(
                        if (mustDraw) {
                            Modifier.border(
                                width = 3.dp,
                                brush = Brush.linearGradient(listOf(CardRed, Color.White, CardRed)),
                                shape = RoundedCornerShape(14.dp)
                            )
                        } else Modifier
                    )
            ) {
                GameCardView(
                    card = dummyCard,
                    modifier = Modifier.width(65.dp),
                    faceDown = true,
                    isPlayable = mustDraw,
                    onClick = if (canDraw || mustDraw) onClick else null
                )
            }

            // Count badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .background(
                        color = if (mustDraw) CardRed else CardBlue,
                        shape = CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (cardsRemaining > 99) "99+" else cardsRemaining.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (mustDraw) "Must draw!" else "Draw pile",
            style = MaterialTheme.typography.labelSmall,
            color = if (mustDraw) CardRed else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (mustDraw) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun OnlineColorIndicator(color: CardColor) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Current:",
            style = MaterialTheme.typography.labelSmall
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color.toComposeColor())
                .border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
        )
        Text(
            text = color.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OnlinePlayerHand(
    hand: List<Card>,
    playableCards: List<Card>,
    isMyTurn: Boolean,
    onCardClick: (Card) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((-16).dp)
    ) {
        hand.forEach { card ->
            val isPlayable = isMyTurn && card in playableCards

            Box(modifier = Modifier.offset(y = if (isPlayable) (-8).dp else 0.dp)) {
                GameCardView(
                    card = card,
                    modifier = Modifier.width(70.dp),
                    faceDown = false,
                    isPlayable = isPlayable,
                    onClick = if (isPlayable) {{ onCardClick(card) }} else null
                )
            }
        }
    }
}

@Composable
private fun OnlineColorPickerDialog(
    onColorSelected: (CardColor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose a Color",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CardColor.playableColors().forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor())
                            .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OnlineWinDialog(
    winnerName: String,
    isCurrentUserWinner: Boolean,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = if (isCurrentUserWinner) "You Won!" else "Game Over",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isCurrentUserWinner) {
                        "Congratulations! You played all your cards!"
                    } else {
                        "$winnerName won the game!"
                    },
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = CardBlue)
            ) {
                Text("Back to Home")
            }
        },
        dismissButton = {}
    )
}
