package com.parthipan.colorclashcards.ui.snl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.ui.components.GradientButton
import com.parthipan.colorclashcards.ui.components.StaggeredEntrance
import com.parthipan.colorclashcards.ui.components.FrostedPanel
import com.parthipan.colorclashcards.ui.components.floatingShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnlHomeScreen(
    onBackClick: () -> Unit,
    onStartOfflineGame: (botCount: Int, difficulty: String, colorIndex: Int, boardLayout: String) -> Unit = { _, _, _, _ -> },
    onPlayOnline: () -> Unit = {}
) {
    var showSetup by remember { mutableStateOf(false) }
    var botCount by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableStateOf("normal") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var boardLayout by remember { mutableStateOf("CLASSIC") }

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
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("snlBackButton")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SnlBoardColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .floatingShapes(
                    colors = listOf(
                        SnlBoardColors.SnakeBody,
                        SnlBoardColors.LadderRail,
                        SnlBoardColors.Accent,
                        SnlBoardColors.PlayerColors[1]
                    ),
                    shapeType = "circle"
                )
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("snlHomeScreen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = showSetup,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
                    } else {
                        (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300)))
                    }
                },
                label = "menuSetupTransition"
            ) { isSetup ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isSetup) {
                        SnlMainMenu(
                            onPlayVsComputer = { showSetup = true },
                            onPlayOnline = onPlayOnline
                        )
                    } else {
                        SnlSetupScreen(
                            botCount = botCount,
                            onBotCountChange = { botCount = it },
                            difficulty = difficulty,
                            onDifficultyChange = { difficulty = it },
                            selectedColorIndex = selectedColorIndex,
                            onColorChange = { selectedColorIndex = it },
                            boardLayout = boardLayout,
                            onBoardLayoutChange = { boardLayout = it },
                            onStartGame = {
                                onStartOfflineGame(botCount, difficulty, selectedColorIndex, boardLayout)
                            },
                            onBack = { showSetup = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnlMainMenu(
    onPlayVsComputer: () -> Unit,
    onPlayOnline: () -> Unit
) {
    // Board Preview with breathing animation
    val breatheTransition = rememberInfiniteTransition(label = "breathe")
    val breatheScale by breatheTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )

    StaggeredEntrance(index = 0) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(breatheScale)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = SnlBoardColors.Primary.copy(alpha = 0.3f),
                    spotColor = SnlBoardColors.Primary.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            SnlBoardPreview(
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    StaggeredEntrance(index = 1) {
        Text(
            text = "Snake & Ladder",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("snlTitle")
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    StaggeredEntrance(index = 2) {
        Text(
            text = "Classic Board Game",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    StaggeredEntrance(index = 3) {
        Text(
            text = "Roll the dice, climb ladders and avoid snakes. First to reach 100 wins!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    StaggeredEntrance(index = 4) {
        FrostedPanel(
            modifier = Modifier.fillMaxWidth(),
            tint = SnlBoardColors.Secondary
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Premium Match Modes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Shortcuts that actually launch modes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPlayVsComputer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Quick Solo")
                    }
                    OutlinedButton(
                        onClick = onPlayOnline,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Online Squad")
                    }
                }
                Text(
                    text = "Need more control? Use full setup below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(28.dp))

    StaggeredEntrance(index = 5) {
        GradientButton(
            onClick = onPlayVsComputer,
            gradientColors = listOf(SnlBoardColors.Primary, SnlBoardColors.PrimaryDark),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("snlPlayVsComputerButton")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Play vs Computer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    StaggeredEntrance(index = 6) {
        GradientButton(
            onClick = onPlayOnline,
            gradientColors = listOf(SnlBoardColors.PlayerColors[1], SnlBoardColors.PlayerColorsDark[1]),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("snlPlayOnlineButton")
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Play Online",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnlSetupScreen(
    botCount: Int,
    onBotCountChange: (Int) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    selectedColorIndex: Int,
    onColorChange: (Int) -> Unit,
    boardLayout: String,
    onBoardLayoutChange: (String) -> Unit,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    StaggeredEntrance(index = 0) {
        Text(
            text = "Game Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Number of opponents
    StaggeredEntrance(index = 1) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Number of Opponents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 2, 3).forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = botCount == count,
                            onClick = { onBotCountChange(count) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) {
                            Text("$count Bot${if (count > 1) "s" else ""}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color picker
                Text(
                    text = "Your Color",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colorNames = listOf("Red", "Blue", "Green", "Yellow")
                    (0..3).forEach { index ->
                        if (index > 0) Spacer(modifier = Modifier.width(12.dp))
                        val isSelected = selectedColorIndex == index
                        val color = SnlBoardColors.getPlayerColor(index)
                        val animatedSize by animateFloatAsState(
                            targetValue = if (isSelected) 36f else 28f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "color_size_$index"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onColorChange(index) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(animatedSize.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isSelected) Modifier
                                            .border(2.dp, Color.White, CircleShape)
                                            .border(3.dp, Color.DarkGray, CircleShape)
                                            .shadow(8.dp, CircleShape, ambientColor = color.copy(alpha = 0.5f), spotColor = color.copy(alpha = 0.5f))
                                        else Modifier
                                    )
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = colorNames[index],
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Board Layout
    StaggeredEntrance(index = 2) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Board Layout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("CLASSIC" to "Classic", "RANDOMIZED" to "Random").forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = boardLayout == value,
                            onClick = { onBoardLayoutChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2)
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (boardLayout) {
                        "RANDOMIZED" -> "Random snake and ladder positions each game"
                        else -> "Traditional snake and ladder positions"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Difficulty
    StaggeredEntrance(index = 3) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Difficulty",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("easy" to "Easy", "normal" to "Normal", "hard" to "Hard").forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = difficulty == value,
                            onClick = { onDifficultyChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val descColor = when (difficulty) {
                    "easy" -> SnlBoardColors.Primary
                    "hard" -> SnlBoardColors.Secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = when (difficulty) {
                        "easy" -> "Bots think slower"
                        "hard" -> "Bots think faster"
                        else -> "Balanced speed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = descColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    StaggeredEntrance(index = 4) {
        GradientButton(
            onClick = onStartGame,
            gradientColors = listOf(SnlBoardColors.Primary, SnlBoardColors.PrimaryDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Start Game",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    StaggeredEntrance(index = 5) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back")
        }
    }
}
