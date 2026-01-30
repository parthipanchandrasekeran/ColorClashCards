package com.parthipan.colorclashcards.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parthipan.colorclashcards.game.model.Card
import com.parthipan.colorclashcards.game.model.CardColor
import com.parthipan.colorclashcards.game.model.CardType
import com.parthipan.colorclashcards.game.model.GameState
import com.parthipan.colorclashcards.game.model.Player
import com.parthipan.colorclashcards.game.model.PlayDirection
import com.parthipan.colorclashcards.game.model.TurnPhase
import com.parthipan.colorclashcards.ui.components.GameCardView
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    mode: String,
    botCount: Int,
    difficulty: String,
    onBackClick: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Start game when screen loads
    LaunchedEffect(Unit) {
        if (mode == "offline") {
            viewModel.startOfflineGame(botCount, difficulty)
        }
    }

    val topBarColor = if (mode == "offline") CardGreen else CardBlue

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (mode == "offline") "vs Computer" else "Online Game",
                            fontWeight = FontWeight.Bold
                        )
                        uiState.gameState?.let { state ->
                            Text(
                                text = "Round ${state.currentRound} of ${GameState.TOTAL_ROUNDS}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        }
                    }
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
                    // Direction indicator
                    uiState.gameState?.let { state ->
                        val rotation by animateFloatAsState(
                            targetValue = if (state.direction == PlayDirection.CLOCKWISE) 0f else 180f,
                            label = "direction"
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Direction",
                            modifier = Modifier
                                .rotate(rotation)
                                .padding(end = 8.dp),
                            tint = MaterialTheme.colorScheme.surface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Felt table background
            FeltTableBackground()
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                uiState.gameState?.let { gameState ->
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top section: Players info
                        PlayersInfoBar(
                            players = gameState.players,
                            currentPlayerId = gameState.currentPlayer.id,
                            humanPlayerId = viewModel.getHumanPlayer()?.id ?: "",
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
                                CurrentTurnIndicator(
                                    playerName = gameState.currentPlayer.name,
                                    isHumanTurn = viewModel.isHumanTurn(),
                                    isProcessing = uiState.isProcessingBotTurn
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Discard pile and draw pile
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Draw pile with stacked cards
                                    StackedDrawPile(
                                        cardsRemaining = gameState.deck.size,
                                        canDraw = viewModel.isHumanTurn() && !uiState.canPlayDrawnCard,
                                        mustDraw = gameState.turnPhase == TurnPhase.MUST_DRAW && viewModel.isHumanTurn(),
                                        drawCount = if (gameState.turnPhase == TurnPhase.MUST_DRAW) gameState.pendingDrawCount else 1,
                                        onClick = { viewModel.drawCard() }
                                    )

                                    // Discard pile with stacked cards and rotation
                                    StackedDiscardPile(
                                        topCard = gameState.topCard,
                                        currentColor = gameState.currentColor,
                                        discardCount = gameState.discardPile.size
                                    )
                                }

                                // Message display
                                AnimatedVisibility(
                                    visible = uiState.message != null,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 16.dp)
                                            .background(
                                                color = CardRed.copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = uiState.message ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Play/Keep buttons for drawn card
                                if (uiState.canPlayDrawnCard) {
                                    Row(
                                        modifier = Modifier.padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.playDrawnCard() },
                                            colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
                                        ) {
                                            Text("Play It")
                                        }
                                        TextButton(onClick = { viewModel.keepDrawnCard() }) {
                                            Text("Keep It")
                                        }
                                    }
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
                                    text = "Your Hand (${viewModel.getHumanPlayer()?.cardCount ?: 0} cards)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = { viewModel.callLastCard() },
                                    enabled = viewModel.canCallLastCard(),
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
                            PlayerHand(
                                hand = viewModel.getHumanPlayer()?.hand ?: emptyList(),
                                playableCards = viewModel.getPlayableCards(),
                                isMyTurn = viewModel.isHumanTurn() && !uiState.canPlayDrawnCard,
                                onCardClick = { card -> viewModel.playCard(card) }
                            )
                        }
                    }
                }
            }

            // Color picker dialog
            if (uiState.showColorPicker) {
                ColorPickerDialog(
                    onColorSelected = { color -> viewModel.selectWildColor(color) },
                    onDismiss = { viewModel.cancelColorPicker() }
                )
            }

            // Win dialog (legacy - keeping for backwards compatibility)
            if (uiState.showWinDialog) {
                WinDialog(
                    winnerName = uiState.winnerName ?: "Unknown",
                    isHumanWinner = uiState.winnerName == "You",
                    onPlayAgain = {
                        viewModel.dismissWinDialog()
                        viewModel.startOfflineGame(botCount, difficulty)
                    },
                    onExit = {
                        viewModel.dismissWinDialog()
                        onBackClick()
                    }
                )
            }

            // Round summary dialog
            if (uiState.showRoundSummary) {
                uiState.gameState?.let { state ->
                    RoundSummaryDialog(
                        currentRound = state.currentRound,
                        roundWinner = state.roundWinner,
                        roundPoints = state.roundPoints,
                        players = state.players,
                        humanPlayerId = viewModel.getHumanPlayer()?.id ?: "",
                        onStartNextRound = { viewModel.startNextRound() },
                        onExit = {
                            viewModel.dismissRoundSummary()
                            onBackClick()
                        }
                    )
                }
            }

            // Final results dialog
            if (uiState.showFinalResults) {
                uiState.gameState?.let { state ->
                    FinalResultsDialog(
                        players = state.players,
                        humanPlayerId = viewModel.getHumanPlayer()?.id ?: "",
                        onPlayNewMatch = { viewModel.startNewMatch() },
                        onExit = {
                            viewModel.dismissFinalResults()
                            onBackClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayersInfoBar(
    players: List<com.parthipan.colorclashcards.game.model.Player>,
    currentPlayerId: String,
    humanPlayerId: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        players.forEach { player ->
            val isCurrentPlayer = player.id == currentPlayerId
            val isHuman = player.id == humanPlayerId

            PlayerInfoCard(
                name = if (isHuman) "You" else player.name,
                cardCount = player.cardCount,
                isCurrentPlayer = isCurrentPlayer
            )
        }
    }
}

@Composable
private fun PlayerInfoCard(
    name: String,
    cardCount: Int,
    isCurrentPlayer: Boolean
) {
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Turn indicator ring (animated glow effect)
        if (isCurrentPlayer) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Draw glowing ring extending beyond bounds
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CardYellow.copy(alpha = 0.6f),
                                    CardYellow.copy(alpha = 0.3f),
                                    Color.Transparent
                                ),
                                radius = size.maxDimension
                            )
                        )
                    }
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCurrentPlayer -> CardYellow.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                }
            ),
            modifier = Modifier
                .border(
                    width = if (isCurrentPlayer) 2.dp else 1.dp,
                    brush = if (isCurrentPlayer) {
                        Brush.linearGradient(
                            colors = listOf(CardYellow, CardYellow.copy(alpha = 0.7f), CardYellow)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .shadow(
                    elevation = if (isCurrentPlayer) 8.dp else 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = if (isCurrentPlayer) CardYellow else Color.Black,
                    spotColor = if (isCurrentPlayer) CardYellow else Color.Black
                ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isCurrentPlayer) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentPlayer) Color.Black else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$cardCount cards",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentPlayer) Color.Black.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CurrentTurnIndicator(
    playerName: String,
    isHumanTurn: Boolean,
    isProcessing: Boolean
) {
    Box(
        modifier = Modifier
            .then(
                if (isHumanTurn) {
                    Modifier.drawBehind {
                        // Glow effect for human turn
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CardGreen.copy(alpha = 0.4f),
                                    CardGreen.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                radius = size.maxDimension
                            )
                        )
                    }
                } else Modifier
            )
            .shadow(
                elevation = if (isHumanTurn) 12.dp else 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = if (isHumanTurn) CardGreen else Color.Black,
                spotColor = if (isHumanTurn) CardGreen else Color.Black
            )
            .background(
                brush = if (isHumanTurn) {
                    Brush.linearGradient(
                        colors = listOf(CardGreen, CardGreen.copy(alpha = 0.85f))
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))
                    )
                },
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 2.dp,
                brush = if (isHumanTurn) {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), CardGreen))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.1f)))
                },
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Text(
                text = if (isHumanTurn) "YOUR TURN" else "$playerName's turn",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (isHumanTurn) 18.sp else 16.sp,
                color = Color.White,
                letterSpacing = if (isHumanTurn) 1.sp else 0.sp
            )
        }
    }
}

/**
 * Felt table background with subtle texture pattern.
 */
@Composable
private fun FeltTableBackground() {
    // Pre-compute pattern positions for performance
    val patternSeed = remember { 12345L }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Base felt color gradient
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1B4D3E), // Darker green center
                    Color(0xFF0D3B2E), // Even darker edges
                    Color(0xFF082A20)
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.maxDimension * 0.8f
            )
        )

        // Draw felt texture pattern (deterministic circles and lines)
        drawFeltTexture(patternSeed)
    }
}

private fun DrawScope.drawFeltTexture(seed: Long) {
    val random = Random(seed)
    val textureColor = Color.White.copy(alpha = 0.02f)
    val darkTextureColor = Color.Black.copy(alpha = 0.05f)

    // Draw small circles for grain texture
    repeat(150) { i ->
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val radius = random.nextFloat() * 3f + 1f
        val alpha = random.nextFloat() * 0.03f + 0.01f

        drawCircle(
            color = if (i % 2 == 0) textureColor.copy(alpha = alpha)
                    else darkTextureColor.copy(alpha = alpha),
            radius = radius,
            center = Offset(x, y)
        )
    }

    // Draw subtle fiber lines
    repeat(50) {
        val startX = random.nextFloat() * size.width
        val startY = random.nextFloat() * size.height
        val endX = startX + (random.nextFloat() - 0.5f) * 40f
        val endY = startY + (random.nextFloat() - 0.5f) * 40f

        drawLine(
            color = textureColor,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 0.5f
        )
    }

    // Add vignette effect at corners
    val vignetteColors = listOf(
        Color.Black.copy(alpha = 0.3f),
        Color.Transparent
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = vignetteColors.reversed(),
            center = Offset(size.width / 2, size.height / 2),
            radius = size.maxDimension * 0.7f
        )
    )
}

/**
 * Stacked draw pile with face-down cards using GameCardView and count badge.
 */
@Composable
private fun StackedDrawPile(
    cardsRemaining: Int,
    canDraw: Boolean,
    mustDraw: Boolean,
    drawCount: Int,
    onClick: () -> Unit
) {
    // Create a dummy card for face-down display
    val dummyCard = remember {
        Card(id = "draw-pile", color = CardColor.RED, type = CardType.NUMBER, number = 0)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Stacked face-down cards underneath (offset for depth effect)
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .offset(x = ((2 - index) * 3).dp, y = ((2 - index) * 3).dp)
                        .width(70.dp)
                ) {
                    GameCardView(
                        card = dummyCard,
                        modifier = Modifier.width(70.dp),
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
                            Modifier
                                .drawBehind {
                                    // Pulsing glow for must-draw state
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                CardRed.copy(alpha = 0.5f),
                                                CardRed.copy(alpha = 0.2f),
                                                Color.Transparent
                                            ),
                                            radius = size.maxDimension * 0.8f
                                        )
                                    )
                                }
                                .border(
                                    width = 3.dp,
                                    brush = Brush.linearGradient(
                                        listOf(CardRed, Color.White, CardRed)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                        } else Modifier
                    )
                    .clickable(enabled = canDraw || mustDraw) { onClick() }
            ) {
                GameCardView(
                    card = dummyCard,
                    modifier = Modifier.width(70.dp),
                    faceDown = true,
                    isPlayable = mustDraw,
                    onClick = if (canDraw || mustDraw) onClick else null
                )
            }

            // Count badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(32.dp)
                    .shadow(6.dp, CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (mustDraw) listOf(CardRed, CardRed.copy(alpha = 0.8f))
                            else listOf(CardBlue, CardBlue.copy(alpha = 0.8f))
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (cardsRemaining > 99) "99+" else cardsRemaining.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Label
        Box(
            modifier = Modifier
                .background(
                    color = if (mustDraw) CardRed.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (mustDraw) "Draw +$drawCount!" else "Draw",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = if (mustDraw) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Stacked discard pile with rotated top card and shadow cards.
 */
@Composable
private fun StackedDiscardPile(
    topCard: Card?,
    currentColor: CardColor,
    discardCount: Int
) {
    // Deterministic rotation based on card id
    val rotation = remember(topCard?.id) {
        topCard?.let { (it.id.hashCode() % 13) - 6f } ?: 0f
    }

    // Create shadow card rotations (deterministic)
    val shadowRotations = remember {
        listOf(-12f, 5f, -3f)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp, 170.dp)
        ) {
            // Shadow cards underneath (previous discards - gray placeholders)
            if (discardCount > 1) {
                repeat(minOf(3, discardCount - 1)) { index ->
                    Box(
                        modifier = Modifier
                            .offset(x = ((index - 1) * 4).dp, y = (index * 3).dp)
                            .rotate(shadowRotations[index])
                            .width(90.dp)
                            .shadow(3.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF4A4A4A).copy(alpha = 0.7f - index * 0.15f),
                                        Color(0xFF3A3A3A).copy(alpha = 0.6f - index * 0.15f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(14.dp)
                            )
                            .aspectRatio(2.5f / 3.5f)
                    )
                }
            }

            // Top card with rotation
            topCard?.let { card ->
                Box(
                    modifier = Modifier
                        .rotate(rotation)
                        .shadow(8.dp, RoundedCornerShape(14.dp))
                ) {
                    GameCardView(
                        card = card,
                        modifier = Modifier.width(95.dp),
                        faceDown = false,
                        isPlayable = false,
                        onClick = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        CurrentColorIndicator(color = currentColor)
    }
}

@Composable
private fun CurrentColorIndicator(color: CardColor) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Current:",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(color.toComposeColor())
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
        Text(
            text = color.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun PlayerHand(
    hand: List<Card>,
    playableCards: List<Card>,
    isMyTurn: Boolean,
    onCardClick: (Card) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((-14).dp)
    ) {
        hand.forEach { card ->
            val isPlayable = isMyTurn && card in playableCards

            Box(modifier = Modifier.offset(y = if (isPlayable) (-12).dp else 0.dp)) {
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
private fun ColorPickerDialog(
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
private fun WinDialog(
    winnerName: String,
    isHumanWinner: Boolean,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = if (isHumanWinner) "You Won!" else "Game Over",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = if (isHumanWinner) CardGreen else CardRed,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isHumanWinner) {
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
                onClick = onPlayAgain,
                colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
            ) {
                Text("Play Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("Exit")
            }
        }
    )
}

@Composable
private fun RoundSummaryDialog(
    currentRound: Int,
    roundWinner: Player?,
    roundPoints: Int,
    players: List<Player>,
    humanPlayerId: String,
    onStartNextRound: () -> Unit,
    onExit: () -> Unit
) {
    val isHumanWinner = roundWinner?.id == humanPlayerId
    val winnerDisplayName = if (isHumanWinner) "You" else roundWinner?.name ?: "Unknown"
    val isFinalRound = currentRound >= GameState.TOTAL_ROUNDS

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Round $currentRound Complete",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = if (isHumanWinner) CardGreen else CardBlue,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Round winner info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHumanWinner) CardGreen.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$winnerDisplayName won this round!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isHumanWinner) CardGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+$roundPoints points",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = CardYellow
                        )
                    }
                }

                // Scoreboard
                Text(
                    text = "Scoreboard",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        players
                            .sortedByDescending { it.totalScore }
                            .forEachIndexed { index, player ->
                                val isHuman = player.id == humanPlayerId
                                val displayName = if (isHuman) "You" else player.name
                                val isRoundWinner = player.id == roundWinner?.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isRoundWinner) CardYellow.copy(alpha = 0.1f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "${player.totalScore} pts",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) CardGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartNextRound,
                colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
            ) {
                Text(
                    text = if (isFinalRound) "View Final Results" else "Start Round ${currentRound + 1}"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("Exit Match")
            }
        }
    )
}

@Composable
private fun FinalResultsDialog(
    players: List<Player>,
    humanPlayerId: String,
    onPlayNewMatch: () -> Unit,
    onExit: () -> Unit
) {
    val sortedPlayers = players.sortedByDescending { it.totalScore }
    val matchWinner = sortedPlayers.firstOrNull()
    val isHumanWinner = matchWinner?.id == humanPlayerId

    AlertDialog(
        onDismissRequest = {},
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isHumanWinner) "Victory!" else "Match Complete",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    color = if (isHumanWinner) CardGreen else CardBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Final Results",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedPlayers.forEachIndexed { index, player ->
                    val isHuman = player.id == humanPlayerId
                    val displayName = if (isHuman) "You" else player.name
                    val isWinner = index == 0

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isWinner && isHuman -> CardGreen.copy(alpha = 0.2f)
                                isWinner -> CardYellow.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Rank badge
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            when (index) {
                                                0 -> CardYellow
                                                1 -> Color.Gray
                                                2 -> Color(0xFFCD7F32) // Bronze
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index < 3) Color.White
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (isWinner) {
                                        Text(
                                            text = "Match Winner",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CardGreen
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${player.totalScore}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWinner) CardGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPlayNewMatch,
                colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
            ) {
                Text("Play New Match")
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("Exit")
            }
        }
    )
}

/**
 * Extension to convert CardColor to Compose Color.
 */
fun CardColor.toComposeColor(): Color {
    return when (this) {
        CardColor.RED -> CardRed
        CardColor.GREEN -> CardGreen
        CardColor.BLUE -> CardBlue
        CardColor.YELLOW -> CardYellow
        CardColor.WILD -> Color.DarkGray
    }
}
