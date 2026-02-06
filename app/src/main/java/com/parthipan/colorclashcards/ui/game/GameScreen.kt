package com.parthipan.colorclashcards.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.parthipan.colorclashcards.game.model.GamePhase
import com.parthipan.colorclashcards.game.model.GameState
import com.parthipan.colorclashcards.game.model.Player
import com.parthipan.colorclashcards.game.model.PlayDirection
import com.parthipan.colorclashcards.game.model.RoundEndReason
import com.parthipan.colorclashcards.game.model.TurnPhase
import com.parthipan.colorclashcards.ui.components.ConfettiOverlay
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

    // Get humanPlayerId from ViewModel (stable after game starts)
    val humanPlayerId = viewModel.humanPlayerId

    // Derive values directly from the collected uiState (simple approach, recomputed on each render)
    val gameState = uiState.gameState
    val humanPlayer = gameState?.getPlayer(humanPlayerId)

    val isHumanTurn = gameState != null &&
        gameState.gamePhase == GamePhase.PLAYING &&
        gameState.currentPlayer.id == humanPlayerId &&
        !uiState.isProcessingBotTurn

    // Memoize playableCards to avoid recalculating on every render
    val playableCards = remember(
        gameState?.topCard?.id,
        gameState?.currentColor,
        humanPlayer?.hand?.map { it.id }
    ) {
        if (gameState != null && humanPlayer != null) {
            val topCard = gameState.topCard
            if (topCard != null) {
                humanPlayer.hand.filter { it.canPlayOn(topCard, gameState.currentColor) }
            } else emptyList()
        } else emptyList()
    }

    val canCallLastCard = humanPlayer?.needsLastCardCall ?: false

    // Scoreboard sheet state
    var showScoreboard by remember { mutableStateOf(false) }
    val scoreboardSheetState = rememberModalBottomSheetState()

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
                    uiState.gameState?.let { state ->
                        // Round timer
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = state.roundTimeFormatted,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Direction indicator
                        val rotation by animateFloatAsState(
                            targetValue = if (state.direction == PlayDirection.CLOCKWISE) 0f else 180f,
                            label = "direction"
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Direction",
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.surface
                        )

                        // Scoreboard button
                        IconButton(onClick = { showScoreboard = true }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Scores",
                                tint = MaterialTheme.colorScheme.surface
                            )
                        }
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
        // Detect orientation
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                uiState.gameState?.let { state ->
                    if (isLandscape) {
                        GameScreenLandscapeContent(
                            gameState = state,
                            humanPlayer = humanPlayer,
                            humanPlayerId = humanPlayerId,
                            isHumanTurn = isHumanTurn,
                            playableCards = playableCards,
                            canCallLastCard = canCallLastCard,
                            uiState = uiState,
                            onDrawCard = { viewModel.drawCard() },
                            onPlayCard = { card -> viewModel.playCard(card) },
                            onPlayDrawnCard = { viewModel.playDrawnCard() },
                            onKeepDrawnCard = { viewModel.keepDrawnCard() },
                            onCallLastCard = { viewModel.callLastCard() }
                        )
                    } else {
                        GameScreenPortraitContent(
                            gameState = state,
                            humanPlayer = humanPlayer,
                            humanPlayerId = humanPlayerId,
                            isHumanTurn = isHumanTurn,
                            playableCards = playableCards,
                            canCallLastCard = canCallLastCard,
                            uiState = uiState,
                            onDrawCard = { viewModel.drawCard() },
                            onPlayCard = { card -> viewModel.playCard(card) },
                            onPlayDrawnCard = { viewModel.playDrawnCard() },
                            onKeepDrawnCard = { viewModel.keepDrawnCard() },
                            onCallLastCard = { viewModel.callLastCard() }
                        )
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
                // Confetti on human win
                ConfettiOverlay(trigger = uiState.winnerName == "You")
            }

            // Round summary dialog
            if (uiState.showRoundSummary) {
                uiState.gameState?.let { state ->
                    RoundSummaryDialog(
                        currentRound = state.currentRound,
                        roundWinner = state.roundWinner,
                        roundPoints = state.roundPoints,
                        roundEndReason = state.roundEndReason,
                        players = state.players,
                        humanPlayerId = humanPlayerId,
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
                    val isHumanMatchWinner = state.players
                        .sortedByDescending { it.totalScore }
                        .firstOrNull()?.id == humanPlayerId
                    FinalResultsDialog(
                        players = state.players,
                        humanPlayerId = humanPlayerId,
                        onPlayNewMatch = { viewModel.startNewMatch() },
                        onExit = {
                            viewModel.dismissFinalResults()
                            onBackClick()
                        }
                    )
                    // Confetti on human match win
                    ConfettiOverlay(trigger = isHumanMatchWinner)
                }
            }

            // Scoreboard bottom sheet
            if (showScoreboard) {
                uiState.gameState?.let { state ->
                    ScoreboardSheet(
                        players = state.players,
                        currentRound = state.currentRound,
                        totalRounds = GameState.TOTAL_ROUNDS,
                        humanPlayerId = humanPlayerId,
                        sheetState = scoreboardSheetState,
                        onDismiss = { showScoreboard = false }
                    )
                }
            }
        }
    }
}

/**
 * Portrait layout: Vertical arrangement with hand at bottom.
 */
@Composable
private fun GameScreenPortraitContent(
    gameState: GameState,
    humanPlayer: Player?,
    humanPlayerId: String,
    isHumanTurn: Boolean,
    playableCards: List<Card>,
    canCallLastCard: Boolean,
    uiState: GameUiState,
    onDrawCard: () -> Unit,
    onPlayCard: (Card) -> Unit,
    onPlayDrawnCard: () -> Unit,
    onKeepDrawnCard: () -> Unit,
    onCallLastCard: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top section: Players info
        PlayersInfoBar(
            players = gameState.players,
            currentPlayerId = gameState.currentPlayer.id,
            humanPlayerId = humanPlayerId,
            modifier = Modifier.padding(8.dp)
        )

        // Middle section: Game area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GameTableArea(
                gameState = gameState,
                isHumanTurn = isHumanTurn,
                uiState = uiState,
                onDrawCard = onDrawCard,
                onPlayDrawnCard = onPlayDrawnCard,
                onKeepDrawnCard = onKeepDrawnCard,
                isLandscape = false
            )
        }

        // Bottom section: Player's hand and Last Card button
        PlayerHandSection(
            humanPlayer = humanPlayer,
            playableCards = playableCards,
            isHumanTurn = isHumanTurn,
            canCallLastCard = canCallLastCard,
            canPlayDrawnCard = uiState.canPlayDrawnCard,
            onPlayCard = onPlayCard,
            onCallLastCard = onCallLastCard,
            isLandscape = false
        )
    }
}

/**
 * Landscape layout: Horizontal arrangement with hand on right side.
 * - Left: Opponents info (vertical)
 * - Center: Game table area (wider)
 * - Right: Player's hand (vertical scroll)
 */
@Composable
private fun GameScreenLandscapeContent(
    gameState: GameState,
    humanPlayer: Player?,
    humanPlayerId: String,
    isHumanTurn: Boolean,
    playableCards: List<Card>,
    canCallLastCard: Boolean,
    uiState: GameUiState,
    onDrawCard: () -> Unit,
    onPlayCard: (Card) -> Unit,
    onPlayDrawnCard: () -> Unit,
    onKeepDrawnCard: () -> Unit,
    onCallLastCard: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        // Calculate right panel width: ~32% of screen, min 140dp, max 200dp
        val rightPanelWidth = (screenWidth * 0.32f).coerceIn(140.dp, 200.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            // Left section: Opponents info (vertical layout)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(100.dp)
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Players",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                gameState.players.forEach { player ->
                    key(player.id) {
                        val isCurrentPlayer = player.id == gameState.currentPlayer.id
                        val isHuman = player.id == humanPlayerId
                        PlayerInfoCardCompact(
                            name = if (isHuman) "You" else player.name,
                            cardCount = player.cardCount,
                            isCurrentPlayer = isCurrentPlayer
                        )
                    }
                }
            }

            // Center section: Game table area (wider)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                GameTableArea(
                    gameState = gameState,
                    isHumanTurn = isHumanTurn,
                    uiState = uiState,
                    onDrawCard = onDrawCard,
                    onPlayDrawnCard = onPlayDrawnCard,
                    onKeepDrawnCard = onKeepDrawnCard,
                    isLandscape = true
                )
            }

            // Visual separator between table and hand panel
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.2f)
            )

            // Right section: Player's hand (fixed percentage width)
            PlayerHandSectionLandscape(
                humanPlayer = humanPlayer,
                playableCards = playableCards,
                isHumanTurn = isHumanTurn,
                canCallLastCard = canCallLastCard,
                canPlayDrawnCard = uiState.canPlayDrawnCard,
                onPlayCard = onPlayCard,
                onCallLastCard = onCallLastCard,
                panelWidth = rightPanelWidth
            )
        }
    }
}

/**
 * Shared game table area for both orientations.
 * Contains: turn indicator, draw pile, discard pile, messages, play/keep buttons.
 */
@Composable
private fun GameTableArea(
    gameState: GameState,
    isHumanTurn: Boolean,
    uiState: GameUiState,
    onDrawCard: () -> Unit,
    onPlayDrawnCard: () -> Unit,
    onKeepDrawnCard: () -> Unit,
    isLandscape: Boolean
) {
    // Compute hasDrawnThisTurn: true if player has already drawn this turn
    // (turnPhase is DREW_CARD or we're showing play/keep buttons)
    val hasDrawnThisTurn = gameState.turnPhase == TurnPhase.DREW_CARD || uiState.canPlayDrawnCard

    // canDraw: Can draw once per turn (even with playable cards)
    // - Must be player's turn
    // - Must be in PLAY_OR_DRAW phase (haven't drawn yet)
    val canDraw = isHumanTurn && !hasDrawnThisTurn && gameState.turnPhase == TurnPhase.PLAY_OR_DRAW

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isLandscape) 16.dp else 0.dp)
    ) {
        // Sudden death warning
        if (gameState.suddenDeathActive) {
            SuddenDeathWarning(
                secondsRemaining = gameState.suddenDeathSecondsRemaining
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Current turn indicator with timer
        CurrentTurnIndicator(
            playerName = gameState.currentPlayer.name,
            isHumanTurn = isHumanTurn,
            isProcessing = uiState.isProcessingBotTurn,
            turnSecondsRemaining = if (isHumanTurn) gameState.turnSecondsRemaining else null
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 16.dp))

        // Discard pile and draw pile
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 48.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Draw pile with stacked cards
            // UNO rules: Can only draw if player has NO playable cards and hasn't drawn yet
            StackedDrawPile(
                cardsRemaining = gameState.deck.size,
                canDraw = canDraw,
                mustDraw = gameState.turnPhase == TurnPhase.MUST_DRAW && isHumanTurn,
                drawCount = if (gameState.turnPhase == TurnPhase.MUST_DRAW) gameState.pendingDrawCount else 1,
                onClick = onDrawCard
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
                    .padding(top = if (isLandscape) 8.dp else 16.dp)
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
                modifier = Modifier.padding(top = if (isLandscape) 8.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onPlayDrawnCard,
                    colors = ButtonDefaults.buttonColors(containerColor = CardGreen),
                    modifier = Modifier.height(48.dp) // Ensure touch target >= 48dp
                ) {
                    Text("Play It")
                }
                TextButton(
                    onClick = onKeepDrawnCard,
                    modifier = Modifier.height(48.dp) // Ensure touch target >= 48dp
                ) {
                    Text("Keep It")
                }
            }
        }
    }
}

/**
 * Player hand section for portrait mode (auto-scaling row at bottom).
 * All cards are always visible without scrolling.
 */
@Composable
private fun PlayerHandSection(
    humanPlayer: Player?,
    playableCards: List<Card>,
    isHumanTurn: Boolean,
    canCallLastCard: Boolean,
    canPlayDrawnCard: Boolean,
    onPlayCard: (Card) -> Unit,
    onCallLastCard: () -> Unit,
    isLandscape: Boolean
) {
    // State for card grouping toggle (default: ON)
    var isGrouped by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(16.dp)
    ) {
        // Header row with title, group toggle, and Last Card button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title and group toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Your Hand (${humanPlayer?.cardCount ?: 0} cards)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Group toggle button
                TextButton(
                    onClick = { isGrouped = !isGrouped },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isGrouped) "Group: ON" else "Group: OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGrouped) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            Button(
                onClick = onCallLastCard,
                enabled = canCallLastCard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardRed,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier.height(48.dp) // Ensure touch target >= 48dp
            ) {
                Text("Last Card!")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Player's hand (auto-scaling, no scroll)
        PlayerHandAutoScale(
            hand = humanPlayer?.hand ?: emptyList(),
            playableCards = playableCards,
            isMyTurn = isHumanTurn && !canPlayDrawnCard,
            onCardClick = onPlayCard,
            isGrouped = isGrouped
        )
    }
}

/**
 * Player hand section for landscape mode (auto-scaling column on right side).
 * All cards are always visible without scrolling.
 * Features: header with card count, auto-scaling hand, Last Card button at bottom.
 */
@Composable
private fun PlayerHandSectionLandscape(
    humanPlayer: Player?,
    playableCards: List<Card>,
    isHumanTurn: Boolean,
    canCallLastCard: Boolean,
    canPlayDrawnCard: Boolean,
    onPlayCard: (Card) -> Unit,
    onCallLastCard: () -> Unit,
    panelWidth: androidx.compose.ui.unit.Dp = 160.dp
) {
    val cardCount = humanPlayer?.cardCount ?: 0

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(panelWidth)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with card count
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Hand",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$cardCount card${if (cardCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Player's hand (auto-scaling, no scroll)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PlayerHandVerticalAutoScale(
                hand = humanPlayer?.hand ?: emptyList(),
                playableCards = playableCards,
                isMyTurn = isHumanTurn && !canPlayDrawnCard,
                onCardClick = onPlayCard
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Last Card button (always visible at bottom)
        Button(
            onClick = onCallLastCard,
            enabled = canCallLastCard,
            colors = ButtonDefaults.buttonColors(
                containerColor = CardRed,
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Ensure touch target >= 48dp
        ) {
            Text(
                text = "Last Card!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Compact player info card for landscape sidebar.
 */
@Composable
private fun PlayerInfoCardCompact(
    name: String,
    cardCount: Int,
    isCurrentPlayer: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentPlayer -> CardYellow.copy(alpha = 0.3f)
                else -> Color.White.copy(alpha = 0.1f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrentPlayer) 2.dp else 0.dp,
                color = if (isCurrentPlayer) CardYellow else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isCurrentPlayer) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentPlayer) Color.Black else Color.White,
                maxLines = 1
            )
            Text(
                text = "$cardCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentPlayer) Color.Black else Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * Auto-scaling vertical player hand for landscape mode.
 * All cards are always visible without scrolling.
 * Cards scale down proportionally when there are many cards.
 */
@Composable
private fun PlayerHandVerticalAutoScale(
    hand: List<Card>,
    playableCards: List<Card>,
    isMyTurn: Boolean,
    onCardClick: (Card) -> Unit
) {
    // Pre-compute playable card IDs for O(1) lookup
    val playableCardIds = remember(playableCards) {
        playableCards.map { it.id }.toSet()
    }

    // Base dimensions (aspect ratio 2.5:3.5 means height = width * 1.4)
    val baseCardWidthDp = 70f
    val baseCardHeightDp = 98f  // 70 * 1.4
    val baseSpacingDp = 4f
    val minScale = 0.5f
    val handSize = hand.size.coerceAtLeast(1)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val availableHeightDp = maxHeight.value

        // Calculate required height at base size
        val requiredHeight = baseCardHeightDp * handSize + baseSpacingDp * (handSize - 1)

        // Calculate scale factor based on height
        val scaleFactor = if (requiredHeight > availableHeightDp) {
            (availableHeightDp / requiredHeight).coerceIn(minScale, 1f)
        } else {
            1f
        }

        // Apply scale to get actual sizes
        val actualCardWidth = (baseCardWidthDp * scaleFactor).dp
        val actualCardHeight = (baseCardHeightDp * scaleFactor).dp
        val actualSpacing = (baseSpacingDp * scaleFactor).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(actualSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            hand.forEach { card ->
                key(card.id) {
                    val isPlayable = isMyTurn && card.id in playableCardIds

                    Box(
                        modifier = Modifier
                            .size(width = actualCardWidth, height = actualCardHeight)
                            .offset(x = if (isPlayable) (-8 * scaleFactor).dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GameCardView(
                            card = card,
                            modifier = Modifier,
                            faceDown = false,
                            isPlayable = isPlayable,
                            onClick = if (isPlayable) { { onCardClick(card) } } else null,
                            fillParentSize = true
                        )
                    }
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
            key(player.id) {
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
}

@Composable
private fun PlayerInfoCard(
    name: String,
    cardCount: Int,
    isCurrentPlayer: Boolean
) {
    // Cache brush objects to avoid recreating them on every recomposition
    val glowBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                CardYellow.copy(alpha = 0.6f),
                CardYellow.copy(alpha = 0.3f),
                Color.Transparent
            )
        )
    }
    val currentPlayerBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(CardYellow, CardYellow.copy(alpha = 0.7f), CardYellow)
        )
    }
    val normalBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    }

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
                        drawCircle(brush = glowBrush, radius = size.maxDimension)
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
                    brush = if (isCurrentPlayer) currentPlayerBorderBrush else normalBorderBrush,
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
    isProcessing: Boolean,
    turnSecondsRemaining: Int? = null
) {
    // Memoize computed values to avoid recalculation
    val isLowTime = remember(turnSecondsRemaining) {
        turnSecondsRemaining != null && turnSecondsRemaining <= 5
    }
    val glowColor = remember(isLowTime, isHumanTurn) {
        when {
            isLowTime -> CardRed
            isHumanTurn -> CardGreen
            else -> Color.Black
        }
    }

    Box(
        modifier = Modifier
            .then(
                if (isHumanTurn) {
                    Modifier.drawBehind {
                        // Glow effect for human turn
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.4f),
                                    glowColor.copy(alpha = 0.1f),
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
                ambientColor = glowColor,
                spotColor = glowColor
            )
            .background(
                brush = if (isHumanTurn) {
                    if (isLowTime) {
                        Brush.linearGradient(
                            colors = listOf(CardRed, CardRed.copy(alpha = 0.85f))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(CardGreen, CardGreen.copy(alpha = 0.85f))
                        )
                    }
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
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), glowColor))
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
            // Turn timer for human player
            if (turnSecondsRemaining != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isLowTime) Color.White else Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${turnSecondsRemaining}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowTime) CardRed else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SuddenDeathWarning(
    secondsRemaining: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(CardRed, CardRed.copy(alpha = 0.8f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, CardYellow, Color.White)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "FINAL MINUTE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Box(
                modifier = Modifier
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${secondsRemaining}s",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CardRed
                )
            }
        }
    }
}

/**
 * Felt table background with subtle texture pattern.
 * Optimized: Reduced draw operations and cached pattern data.
 */
@Composable
private fun FeltTableBackground() {
    // Pre-compute pattern positions once
    val patternData = remember {
        val random = Random(12345L)
        FeltPatternData(
            circles = List(50) { i ->
                FeltCircle(
                    xFraction = random.nextFloat(),
                    yFraction = random.nextFloat(),
                    radiusFraction = random.nextFloat() * 0.003f + 0.001f,
                    alpha = random.nextFloat() * 0.03f + 0.01f,
                    isLight = i % 2 == 0
                )
            },
            lines = List(20) {
                val startX = random.nextFloat()
                val startY = random.nextFloat()
                FeltLine(
                    startXFraction = startX,
                    startYFraction = startY,
                    endXFraction = startX + (random.nextFloat() - 0.5f) * 0.04f,
                    endYFraction = startY + (random.nextFloat() - 0.5f) * 0.04f
                )
            }
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Base felt color gradient
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1B4D3E),
                    Color(0xFF0D3B2E),
                    Color(0xFF082A20)
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.maxDimension * 0.8f
            )
        )

        // Draw cached texture pattern
        val textureColor = Color.White
        val darkTextureColor = Color.Black

        patternData.circles.forEach { circle ->
            drawCircle(
                color = if (circle.isLight) textureColor.copy(alpha = circle.alpha)
                        else darkTextureColor.copy(alpha = circle.alpha + 0.02f),
                radius = circle.radiusFraction * size.maxDimension,
                center = Offset(circle.xFraction * size.width, circle.yFraction * size.height)
            )
        }

        patternData.lines.forEach { line ->
            drawLine(
                color = textureColor.copy(alpha = 0.02f),
                start = Offset(line.startXFraction * size.width, line.startYFraction * size.height),
                end = Offset(line.endXFraction * size.width, line.endYFraction * size.height),
                strokeWidth = 0.5f
            )
        }

        // Vignette effect
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.maxDimension * 0.7f
            )
        )
    }
}

// Data classes for cached pattern
private data class FeltPatternData(
    val circles: List<FeltCircle>,
    val lines: List<FeltLine>
)

private data class FeltCircle(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val alpha: Float,
    val isLight: Boolean
)

private data class FeltLine(
    val startXFraction: Float,
    val startYFraction: Float,
    val endXFraction: Float,
    val endYFraction: Float
)

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

    val cardWidth = 70.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Stacked face-down cards underneath (offset for depth effect)
            repeat(3) { index ->
                GameCardView(
                    card = dummyCard,
                    modifier = Modifier
                        .offset(x = ((2 - index) * 3).dp, y = ((2 - index) * 3).dp)
                        .width(cardWidth),
                    faceDown = true,
                    isPlayable = false,
                    onClick = null
                )
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
                        } else Modifier
                    )
            ) {
                GameCardView(
                    card = dummyCard,
                    modifier = Modifier.width(cardWidth),
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

            // Top card with rotation + scale-in animation on card change
            AnimatedContent(
                targetState = topCard,
                contentKey = { it?.id },
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(dampingRatio = 0.6f)
                    ) + fadeIn(animationSpec = tween(200)))
                        .togetherWith(fadeOut(animationSpec = tween(100)))
                },
                label = "discard_card_anim"
            ) { card ->
                if (card != null) {
                    Box(
                        modifier = Modifier
                            .rotate(rotation)
                            .shadow(8.dp, RoundedCornerShape(14.dp))
                            .size(width = 95.dp, height = 133.dp)
                    ) {
                        GameCardView(
                            card = card,
                            modifier = Modifier,
                            faceDown = false,
                            isPlayable = false,
                            onClick = null,
                            fillParentSize = true
                        )
                    }
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

// Discrete scale steps for ROW mode (shrink-only), min is 0.68
private val DISCRETE_SCALE_STEPS_ROW = listOf(1.00f, 0.90f, 0.80f, 0.70f, 0.68f)

// Fixed scale for OVERLAP mode
private const val OVERLAP_MODE_SCALE = 0.68f

// Color order for grouping: RED, YELLOW, GREEN, BLUE, WILD (wild last)
private val COLOR_ORDER = mapOf(
    CardColor.RED to 0,
    CardColor.YELLOW to 1,
    CardColor.GREEN to 2,
    CardColor.BLUE to 3,
    CardColor.WILD to 4
)

// Card type order within color group: NUMBER first, then actions, then wild types
private val TYPE_ORDER = mapOf(
    CardType.NUMBER to 0,
    CardType.SKIP to 1,
    CardType.REVERSE to 2,
    CardType.DRAW_TWO to 3,
    CardType.WILD_COLOR to 4,
    CardType.WILD_DRAW_FOUR to 5
)

/**
 * Comparator for sorting cards by color group, then by type, then by number.
 * Order: RED -> YELLOW -> GREEN -> BLUE -> WILD
 * Within each color: Numbers (0-9) -> SKIP -> REVERSE -> DRAW_TWO
 * Wild group: WILD_COLOR -> WILD_DRAW_FOUR
 */
private val cardGroupComparator = Comparator<Card> { a, b ->
    // First compare by color
    val colorCompare = (COLOR_ORDER[a.color] ?: 99) - (COLOR_ORDER[b.color] ?: 99)
    if (colorCompare != 0) return@Comparator colorCompare

    // Then compare by type
    val typeCompare = (TYPE_ORDER[a.type] ?: 99) - (TYPE_ORDER[b.type] ?: 99)
    if (typeCompare != 0) return@Comparator typeCompare

    // For NUMBER cards, compare by number value
    if (a.type == CardType.NUMBER && b.type == CardType.NUMBER) {
        return@Comparator (a.number ?: 0) - (b.number ?: 0)
    }

    0
}

/**
 * Auto-scaling player hand for portrait mode.
 * MODE A: SHRINK first, then OVERLAP only if still not enough space.
 *
 * 1) If all cards fit normally -> show normal size (ROW mode)
 * 2) If they don't fit -> shrink uniformly (all cards same size, ROW mode)
 * 3) If they still don't fit at minScale (0.68) -> overlap uniformly (OVERLAP mode)
 */
@Composable
private fun PlayerHandAutoScale(
    hand: List<Card>,
    playableCards: List<Card>,
    isMyTurn: Boolean,
    onCardClick: (Card) -> Unit,
    isGrouped: Boolean = true
) {
    // Sort cards by color group when grouping is enabled (UI-only, doesn't affect game state)
    val displayHand = remember(hand, isGrouped) {
        if (isGrouped) {
            hand.sortedWith(cardGroupComparator)
        } else {
            hand
        }
    }

    // Build list of group boundary indices (where color changes) for adding separators
    val groupBoundaries = remember(displayHand, isGrouped) {
        if (!isGrouped || displayHand.size <= 1) {
            emptySet()
        } else {
            val boundaries = mutableSetOf<Int>()
            for (i in 1 until displayHand.size) {
                if (displayHand[i].color != displayHand[i - 1].color) {
                    boundaries.add(i)
                }
            }
            boundaries
        }
    }

    val playableCardIds = remember(playableCards) {
        playableCards.map { it.id }.toSet()
    }

    // Track selected/tapped card for bringing to front in overlap mode
    var selectedCardId by remember { mutableStateOf<String?>(null) }

    // Base dimensions (aspect ratio 2.5:3.5)
    val baseCardWidthDp = 70f
    val baseCardHeightDp = 98f
    val baseSpacingDp = 4f
    val groupGapDp = 6f // Extra gap between color groups
    val handSize = displayHand.size.coerceAtLeast(1)
    val numGroupGaps = groupBoundaries.size

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val availableWidthPx = (maxWidth.value * density.density).toInt()

        // Try to find a scale that fits in ROW mode (with spacing + group gaps)
        var rowModeScale: Float? = null
        for (scale in DISCRETE_SCALE_STEPS_ROW) {
            val cardWidthPx = (baseCardWidthDp * scale * density.density).toInt()
            val spacingPx = (baseSpacingDp * scale * density.density).toInt().coerceAtLeast(1)
            val groupGapPx = (groupGapDp * scale * density.density).toInt()
            // Total = cards + normal spacing + extra group gaps
            val totalWidthNeeded = cardWidthPx * handSize +
                spacingPx * (handSize - 1) +
                groupGapPx * numGroupGaps
            if (totalWidthNeeded <= availableWidthPx) {
                rowModeScale = scale
                break
            }
        }

        val isOverlapMode = rowModeScale == null
        val finalScale = rowModeScale ?: OVERLAP_MODE_SCALE

        // Compute card dimensions (pixel-snapped for crispness)
        val cardWidthPx = (baseCardWidthDp * finalScale * density.density).toInt()
        val cardHeightPx = (baseCardHeightDp * finalScale * density.density).toInt()
        val spacingPx = (baseSpacingDp * finalScale * density.density).toInt().coerceAtLeast(1)
        val groupGapPx = (groupGapDp * finalScale * density.density).toInt()

        val cardWidth = with(density) { cardWidthPx.toDp() }
        val cardHeight = with(density) { cardHeightPx.toDp() }
        val spacing = with(density) { spacingPx.toDp() }
        val groupGap = with(density) { groupGapPx.toDp() }

        // For overlap mode, calculate step (how much each card is offset from the previous)
        val overlapStep = if (isOverlapMode && handSize > 1) {
            (availableWidthPx - cardWidthPx).toFloat() / (handSize - 1)
        } else {
            0f
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isOverlapMode) {
                // ROW MODE: Cards fit with spacing, with extra gaps between color groups
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    displayHand.forEachIndexed { index, card ->
                        key(card.id) {
                            val isPlayable = isMyTurn && card.id in playableCardIds
                            val isGroupBoundary = index in groupBoundaries

                            // Add extra gap before group boundary
                            if (isGroupBoundary) {
                                Spacer(modifier = Modifier.width(groupGap))
                            } else if (index > 0) {
                                Spacer(modifier = Modifier.width(spacing))
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = cardWidth, height = cardHeight)
                                    .offset(y = if (isPlayable) (-8 * finalScale).dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GameCardView(
                                    card = card,
                                    modifier = Modifier,
                                    faceDown = false,
                                    isPlayable = isPlayable,
                                    onClick = if (isPlayable) { { onCardClick(card) } } else null,
                                    fillParentSize = true,
                                    scaleFactor = finalScale
                                )
                            }
                        }
                    }
                }
            } else {
                // OVERLAP MODE: Custom Layout with zIndex for stacking
                val stepPx = overlapStep.toInt().coerceAtLeast(1)

                // Extra height for lift effect
                val liftHeightPx = with(density) { 16.dp.roundToPx() }

                Layout(
                    content = {
                        displayHand.forEachIndexed { index, card ->
                            key(card.id) {
                                val isPlayable = isMyTurn && card.id in playableCardIds
                                val isSelected = card.id == selectedCardId

                                // zIndex: selected card is on top, otherwise right cards over left
                                val cardZIndex = if (isSelected) 1000f else index.toFloat()

                                // Lift offset: selected cards lift more, playable cards lift slightly
                                val liftOffset = when {
                                    isSelected -> (-14).dp
                                    isPlayable -> (-8 * finalScale).dp
                                    else -> 0.dp
                                }

                                Box(
                                    modifier = Modifier
                                        .size(width = cardWidth, height = cardHeight)
                                        .zIndex(cardZIndex)
                                        .offset(y = liftOffset),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GameCardView(
                                        card = card,
                                        modifier = Modifier,
                                        faceDown = false,
                                        isPlayable = isPlayable,
                                        onClick = if (isPlayable) {
                                            {
                                                selectedCardId = card.id
                                                onCardClick(card)
                                            }
                                        } else {
                                            // Allow selecting non-playable cards to view them
                                            { selectedCardId = if (selectedCardId == card.id) null else card.id }
                                        },
                                        fillParentSize = true,
                                        scaleFactor = finalScale
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.wrapContentSize()
                ) { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints) }

                    // Total width needed for overlap layout
                    val totalWidth = if (placeables.isNotEmpty()) {
                        cardWidthPx + stepPx * (placeables.size - 1)
                    } else 0

                    // Height includes extra space for lift effect
                    layout(totalWidth.coerceAtLeast(0), cardHeightPx + liftHeightPx) {
                        placeables.forEachIndexed { index, placeable ->
                            val xOffset = stepPx * index
                            // Place at bottom of layout area, offset handles lift
                            placeable.place(xOffset, liftHeightPx)
                        }
                    }
                }
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
    roundEndReason: RoundEndReason,
    players: List<Player>,
    humanPlayerId: String,
    onStartNextRound: () -> Unit,
    onExit: () -> Unit
) {
    val isHumanWinner = roundWinner?.id == humanPlayerId
    val winnerDisplayName = if (isHumanWinner) "You" else roundWinner?.name ?: "Unknown"
    val isFinalRound = currentRound >= GameState.TOTAL_ROUNDS
    val isTimeout = roundEndReason == RoundEndReason.TIMEOUT

    AlertDialog(
        onDismissRequest = {},
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Round $currentRound Complete",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    color = if (isHumanWinner) CardGreen else CardBlue
                )
                if (isTimeout) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Time Expired",
                        style = MaterialTheme.typography.labelMedium,
                        color = CardRed
                    )
                }
            }
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
                            text = if (isTimeout) {
                                if (isHumanWinner) "You win by lowest hand!" else "$winnerDisplayName wins by lowest hand!"
                            } else {
                                if (isHumanWinner) "You won this round!" else "$winnerDisplayName won this round!"
                            },
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
 * Reusable scoreboard sheet that can be opened at any time during the game.
 * Shows current round, player scores, and highlights the leader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardSheet(
    players: List<Player>,
    currentRound: Int,
    totalRounds: Int,
    humanPlayerId: String,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit
) {
    // Sort players by total score descending
    val sortedPlayers = remember(players) {
        players.sortedByDescending { it.totalScore }
    }

    // Find leader(s) - could be a tie
    val highestScore = sortedPlayers.firstOrNull()?.totalScore ?: 0
    val leaderIds = remember(sortedPlayers) {
        sortedPlayers.filter { it.totalScore == highestScore }.map { it.id }.toSet()
    }
    val isTied = leaderIds.size > 1 && highestScore > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scores",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = CardBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Round $currentRound / $totalRounds",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = CardBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Tied indicator
            if (isTied) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = CardYellow.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tied for the lead!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CardYellow.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Player list
            sortedPlayers.forEachIndexed { index, player ->
                val isHuman = player.id == humanPlayerId
                val displayName = if (isHuman) "You" else player.name
                val isLeader = player.id in leaderIds && highestScore > 0

                ScoreboardPlayerRow(
                    rank = index + 1,
                    displayName = displayName,
                    totalScore = player.totalScore,
                    cardsRemaining = player.cardCount,
                    isLeader = isLeader,
                    isHuman = isHuman,
                    isTied = isTied && isLeader
                )

                if (index < sortedPlayers.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScoreboardPlayerRow(
    rank: Int,
    displayName: String,
    totalScore: Int,
    cardsRemaining: Int,
    isLeader: Boolean,
    isHuman: Boolean,
    isTied: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLeader && isHuman -> CardGreen.copy(alpha = 0.15f)
                isLeader -> CardYellow.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLeader) {
                    Modifier.border(
                        width = 2.dp,
                        color = if (isHuman) CardGreen else CardYellow,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        .size(32.dp)
                        .background(
                            color = when {
                                isLeader -> if (isHuman) CardGreen else CardYellow
                                rank == 2 -> Color.Gray.copy(alpha = 0.7f)
                                rank == 3 -> Color(0xFFCD7F32).copy(alpha = 0.7f) // Bronze
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isLeader || rank <= 3) Color.White
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isHuman || isLeader) FontWeight.Bold else FontWeight.Medium
                        )
                        if (isLeader && !isTied) {
                            Text(
                                text = "Leader",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isHuman) CardGreen else CardYellow,
                                modifier = Modifier
                                    .background(
                                        color = (if (isHuman) CardGreen else CardYellow).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "$cardsRemaining cards in hand",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Score
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$totalScore",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLeader) {
                        if (isHuman) CardGreen else CardYellow
                    } else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
