package com.parthipan.colorclashcards.ui.ludo

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
import androidx.compose.ui.unit.sp
import com.parthipan.colorclashcards.ui.components.GradientButton
import com.parthipan.colorclashcards.ui.components.StaggeredEntrance
import com.parthipan.colorclashcards.ui.components.floatingShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoHomeScreen(
    onBackClick: () -> Unit,
    onStartOfflineGame: (botCount: Int, difficulty: String, color: String) -> Unit = { _, _, _ -> },
    onPlayOnline: () -> Unit = {}
) {
    var showSetup by remember { mutableStateOf(false) }
    var botCount by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableStateOf("normal") }
    var selectedColor by remember { mutableStateOf("RED") }

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
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("ludoBackButton")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LudoBoardColors.Green,
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
                        LudoBoardColors.Red,
                        LudoBoardColors.Blue,
                        LudoBoardColors.Green,
                        LudoBoardColors.Yellow
                    ),
                    shapeType = "circle"
                )
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("ludoHomeScreen"),
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
                        LudoMainMenu(
                            onPlayVsComputer = { showSetup = true },
                            onPlayOnline = onPlayOnline
                        )
                    } else {
                        LudoSetupScreen(
                            botCount = botCount,
                            onBotCountChange = { botCount = it },
                            difficulty = difficulty,
                            onDifficultyChange = { difficulty = it },
                            selectedColor = selectedColor,
                            onColorChange = { selectedColor = it },
                            onStartGame = {
                                onStartOfflineGame(botCount, difficulty, selectedColor)
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
private fun LudoMainMenu(
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
                    ambientColor = LudoBoardColors.Green.copy(alpha = 0.3f),
                    spotColor = LudoBoardColors.Green.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            LudoBoardPreviewLarge(
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    StaggeredEntrance(index = 1) {
        Text(
            text = "Ludo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("ludoTitle")
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
            text = "Race your tokens around the board and be the first to get all four home!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    Spacer(modifier = Modifier.height(48.dp))

    // Play buttons - gradient
    StaggeredEntrance(index = 4) {
        GradientButton(
            onClick = onPlayVsComputer,
            gradientColors = listOf(LudoBoardColors.Green, LudoBoardColors.GreenDark),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("playVsComputerButton")
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

    StaggeredEntrance(index = 5) {
        GradientButton(
            onClick = onPlayOnline,
            gradientColors = listOf(LudoBoardColors.Blue, LudoBoardColors.BlueDark),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("playOnlineButton")
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
private fun LudoSetupScreen(
    botCount: Int,
    onBotCountChange: (Int) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    selectedColor: String,
    onColorChange: (String) -> Unit,
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

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive color picker with animated selection
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
                    val colorOptions = listOf(
                        "RED" to LudoBoardColors.Red,
                        "BLUE" to LudoBoardColors.Blue,
                        "GREEN" to LudoBoardColors.Green,
                        "YELLOW" to LudoBoardColors.Yellow
                    )
                    colorOptions.forEachIndexed { index, (name, color) ->
                        if (index > 0) Spacer(modifier = Modifier.width(12.dp))
                        val isSelected = selectedColor == name
                        val animatedSize by animateFloatAsState(
                            targetValue = if (isSelected) 36f else 28f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "color_size_$name"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onColorChange(name) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(animatedSize.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isSelected) Modifier
                                            .border(2.dp, Color.White, CircleShape)
                                            .border(3.dp, Color.DarkGray, CircleShape)
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = CircleShape,
                                                ambientColor = color.copy(alpha = 0.5f),
                                                spotColor = color.copy(alpha = 0.5f)
                                            )
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
                                text = name.lowercase().replaceFirstChar { it.uppercase() },
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

    // Difficulty with color tints per level
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
                    "easy" -> LudoBoardColors.Green
                    "hard" -> LudoBoardColors.Red
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = when (difficulty) {
                        "easy" -> "Random moves - great for learning"
                        "hard" -> "Strategic AI - a real challenge"
                        else -> "Balanced gameplay"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = descColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Start button as GradientButton
    StaggeredEntrance(index = 3) {
        GradientButton(
            onClick = onStartGame,
            gradientColors = listOf(LudoBoardColors.Green, LudoBoardColors.GreenDark),
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

    StaggeredEntrance(index = 4) {
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
