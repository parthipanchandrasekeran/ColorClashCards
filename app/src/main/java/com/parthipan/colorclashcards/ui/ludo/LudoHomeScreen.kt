package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoHomeScreen(
    onBackClick: () -> Unit,
    onStartOfflineGame: (botCount: Int, difficulty: String) -> Unit = { _, _ -> },
    onPlayOnline: () -> Unit = {}
) {
    var showSetup by remember { mutableStateOf(false) }
    var botCount by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableStateOf("normal") }

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
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("ludoHomeScreen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showSetup) {
                // Main menu
                LudoMainMenu(
                    onPlayVsComputer = { showSetup = true },
                    onPlayOnline = onPlayOnline
                )
            } else {
                // Setup screen
                LudoSetupScreen(
                    botCount = botCount,
                    onBotCountChange = { botCount = it },
                    difficulty = difficulty,
                    onDifficultyChange = { difficulty = it },
                    onStartGame = {
                        onStartOfflineGame(botCount, difficulty)
                    },
                    onBack = { showSetup = false }
                )
            }
        }
    }
}

@Composable
private fun LudoMainMenu(
    onPlayVsComputer: () -> Unit,
    onPlayOnline: () -> Unit
) {
    // Board Preview
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        LudoBoardPreviewLarge(
            modifier = Modifier.fillMaxSize()
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Ludo",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("ludoTitle")
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Classic Board Game",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Race your tokens around the board and be the first to get all four home!",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(modifier = Modifier.height(48.dp))

    // Play buttons
    Button(
        onClick = onPlayVsComputer,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("playVsComputerButton"),
        colors = ButtonDefaults.buttonColors(containerColor = LudoBoardColors.Green),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Play vs Computer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onPlayOnline,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("playOnlineButton"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Play Online",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LudoSetupScreen(
    botCount: Int,
    onBotCountChange: (Int) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "Game Setup",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Number of opponents
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

            // Show player color preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Players: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                val colors = when (botCount) {
                    1 -> listOf(LudoBoardColors.Red, LudoBoardColors.Yellow)
                    2 -> listOf(LudoBoardColors.Red, LudoBoardColors.Blue, LudoBoardColors.Green)
                    else -> listOf(LudoBoardColors.Red, LudoBoardColors.Blue, LudoBoardColors.Green, LudoBoardColors.Yellow)
                }
                colors.forEachIndexed { index, color ->
                    if (index > 0) Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Difficulty
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

            Text(
                text = when (difficulty) {
                    "easy" -> "Random moves - great for learning"
                    "hard" -> "Strategic AI - a real challenge"
                    else -> "Balanced gameplay"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Start button
    Button(
        onClick = onStartGame,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LudoBoardColors.Green),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "Start Game",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

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
