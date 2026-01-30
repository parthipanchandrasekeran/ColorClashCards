package com.parthipan.colorclashcards.ui.howtoplay

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToPlayScreen(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to Play") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBlue,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Objective
            RuleCard(
                title = "Objective",
                content = "Be the first player to get rid of all your cards!"
            )

            // Card Colors
            RuleCard(title = "Card Colors") {
                Column {
                    Text(
                        text = "The game uses four colors:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ColorChip(color = CardRed, label = "Red")
                        ColorChip(color = CardBlue, label = "Blue")
                        ColorChip(color = CardGreen, label = "Green")
                        ColorChip(color = CardYellow, label = "Yellow")
                    }
                }
            }

            // Basic Rules
            RuleCard(
                title = "Basic Rules",
                content = """
                    1. Each player starts with 7 cards
                    2. Match the top card by color or number
                    3. If you can't play, draw a card
                    4. Special cards have unique effects
                    5. Say "Last Card!" when you have one card left
                """.trimIndent()
            )

            // Special Cards
            RuleCard(title = "Special Cards") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecialCardRule(
                        name = "Skip",
                        description = "Next player loses their turn"
                    )
                    SpecialCardRule(
                        name = "Reverse",
                        description = "Changes direction of play"
                    )
                    SpecialCardRule(
                        name = "Draw Two",
                        description = "Next player draws 2 cards and skips turn"
                    )
                    SpecialCardRule(
                        name = "Wild",
                        description = "Can be played anytime, choose next color"
                    )
                    SpecialCardRule(
                        name = "Wild Draw Four",
                        description = "Choose color, next player draws 4 cards"
                    )
                }
            }

            // Tips
            RuleCard(
                title = "Pro Tips",
                content = """
                    - Save your Wild cards for emergencies
                    - Pay attention to what colors opponents need
                    - Use action cards strategically
                    - Don't forget to call "Last Card!"
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RuleCard(
    title: String,
    content: String? = null,
    customContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CardBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (content != null) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            customContent?.invoke()
        }
    }
}

@Composable
private fun RuleCard(
    title: String,
    content: @Composable () -> Unit
) {
    RuleCard(title = title, content = null, customContent = content)
}

@Composable
private fun ColorChip(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SpecialCardRule(
    name: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(CardYellow, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.first().toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
