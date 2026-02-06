package com.parthipan.colorclashcards.ui.gamehub

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.filled.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHubScreen(
    onNavigateToColorClash: () -> Unit,
    onNavigateToLudo: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignIn: () -> Unit = {}
) {
    val isSignedIn = FirebaseAuth.getInstance().currentUser != null
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Color Clash Cards",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (!isSignedIn) {
                        IconButton(onClick = onSignIn) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Sign In"
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        // Staggered entrance state
        var titleVisible by remember { mutableStateOf(false) }
        var card1Visible by remember { mutableStateOf(false) }
        var card2Visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            titleVisible = true
            delay(150)
            card1Visible = true
            delay(150)
            card2Visible = true
        }

        val titleAlpha by animateFloatAsState(
            targetValue = if (titleVisible) 1f else 0f,
            animationSpec = tween(400), label = "titleAlpha"
        )
        val titleOffset by animateFloatAsState(
            targetValue = if (titleVisible) 0f else 30f,
            animationSpec = tween(400), label = "titleOffset"
        )
        val card1Alpha by animateFloatAsState(
            targetValue = if (card1Visible) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow), label = "card1Alpha"
        )
        val card1Offset by animateFloatAsState(
            targetValue = if (card1Visible) 0f else 60f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "card1Offset"
        )
        val card2Alpha by animateFloatAsState(
            targetValue = if (card2Visible) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow), label = "card2Alpha"
        )
        val card2Offset by animateFloatAsState(
            targetValue = if (card2Visible) 0f else 60f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "card2Offset"
        )

        // G3: Floating background card shapes — slow drift
        val bgShapeTransition = rememberInfiniteTransition(label = "bg_shapes")
        val bgDrift by bgShapeTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bg_drift"
        )
        val bgShapeColor1 = CardRed.copy(alpha = 0.04f)
        val bgShapeColor2 = CardBlue.copy(alpha = 0.04f)
        val bgShapeColor3 = CardGreen.copy(alpha = 0.04f)
        val bgShapeColor4 = CardYellow.copy(alpha = 0.04f)

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
                .drawBehind {
                    val driftOffset = bgDrift * 20f
                    // 4 faint card-shaped rectangles at scattered positions
                    rotate(degrees = 15f + driftOffset * 0.3f, pivot = Offset(size.width * 0.1f, size.height * 0.15f)) {
                        drawRoundRect(
                            color = bgShapeColor1,
                            topLeft = Offset(size.width * 0.05f, size.height * 0.08f + driftOffset),
                            size = Size(60f, 90f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                        )
                    }
                    rotate(degrees = -10f - driftOffset * 0.2f, pivot = Offset(size.width * 0.85f, size.height * 0.25f)) {
                        drawRoundRect(
                            color = bgShapeColor2,
                            topLeft = Offset(size.width * 0.8f, size.height * 0.2f - driftOffset * 0.5f),
                            size = Size(50f, 75f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                    }
                    rotate(degrees = 25f - driftOffset * 0.4f, pivot = Offset(size.width * 0.7f, size.height * 0.7f)) {
                        drawRoundRect(
                            color = bgShapeColor3,
                            topLeft = Offset(size.width * 0.65f, size.height * 0.65f + driftOffset * 0.7f),
                            size = Size(55f, 80f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f)
                        )
                    }
                    rotate(degrees = -20f + driftOffset * 0.5f, pivot = Offset(size.width * 0.2f, size.height * 0.75f)) {
                        drawRoundRect(
                            color = bgShapeColor4,
                            topLeft = Offset(size.width * 0.15f, size.height * 0.7f - driftOffset * 0.3f),
                            size = Size(45f, 65f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                    }
                }
                .padding(paddingValues)
                .padding(16.dp)
                .testTag("gameHubScreen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose a Game",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleOffset
                    }
                    .testTag("chooseGameTitle")
            )
            Text(
                text = "Tap a card to play",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleOffset
                    }
            )

            // Game Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Color Clash Cards Game Card
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = card1Alpha
                        translationY = card1Offset
                    }
                ) {
                    GameCard(
                        title = "Color Clash",
                        subtitle = "Card Game",
                        description = "Match colors and numbers in this fast-paced card game!",
                        gradientColors = listOf(CardRed, CardBlue),
                        iconContent = {
                            ColorClashIcon()
                        },
                        onClick = onNavigateToColorClash,
                        testTag = "colorClashCard"
                    )
                }

                // Ludo Game Card
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = card2Alpha
                        translationY = card2Offset
                    }
                ) {
                    GameCard(
                        title = "Ludo",
                        subtitle = "Board Game",
                        description = "Classic board game - race your tokens to the finish!",
                        gradientColors = listOf(CardGreen, CardYellow),
                        iconContent = {
                            LudoIcon()
                        },
                        onClick = onNavigateToLudo,
                        testTag = "ludoCard"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameCard(
    title: String,
    subtitle: String,
    description: String,
    gradientColors: List<Color>,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cardScale"
    )

    // G1: Shimmer sweep animation
    val shimmerTransition = rememberInfiniteTransition(label = "card_shimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(scale)
            // G2: Colored shadow matching card gradient
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = gradientColors.first().copy(alpha = 0.4f),
                spotColor = gradientColors.first().copy(alpha = 0.4f)
            )
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(gradientColors)
                )
                .drawWithContent {
                    drawContent()
                    // G1: Diagonal shimmer sweep overlay
                    val sweepWidth = size.width * 0.3f
                    val sweepStart = size.width * shimmerX
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startX = sweepStart,
                            endX = sweepStart + sweepWidth
                        )
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Text content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorClashIcon() {
    // G4: Gentle scale pulse 1.0 → 1.06 over 2s
    val pulseTransition = rememberInfiniteTransition(label = "cc_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cc_scale"
    )

    // Simple card stack icon representation
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
    ) {
        // Stacked cards effect
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardYellow)
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(start = 8.dp, top = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardGreen)
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(start = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardBlue)
        ) {
            Text(
                text = "CC",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun LudoIcon() {
    // G4: Slight rotation wobble -2° → +2° over 3s
    val wobbleTransition = rememberInfiniteTransition(label = "ludo_wobble")
    val wobbleAngle by wobbleTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ludo_wobble_angle"
    )

    // Simple 2x2 grid representing Ludo board quadrants
    Column(
        modifier = Modifier.graphicsLayer { rotationZ = wobbleAngle },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardRed)
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBlue)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardYellow)
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardGreen)
            )
        }
    }
}
