package com.parthipan.colorclashcards.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.parthipan.colorclashcards.audio.LocalSoundManager
import com.parthipan.colorclashcards.audio.SoundEffect
import com.parthipan.colorclashcards.ui.theme.Gold
import com.parthipan.colorclashcards.ui.theme.GoldLight
import kotlinx.coroutines.delay

/**
 * Fullscreen celebration overlay replacing AlertDialog-based win dialogs.
 *
 * @param isWinner Whether the local player won
 * @param title Main title text (e.g. "You Win!" or "Game Over")
 * @param subtitle Subtitle text (e.g. "Congratulations!" or "Bot wins!")
 * @param winnerColor The winner's color for the gradient background
 * @param rankings Optional ranked list of (rank, name) for Ludo
 * @param primaryAction Label and callback for primary button (e.g. "Play Again")
 * @param secondaryAction Label and callback for secondary button (e.g. "Exit")
 */
@Composable
fun CelebrationOverlay(
    isWinner: Boolean,
    title: String,
    subtitle: String,
    winnerColor: Color,
    rankings: List<Pair<String, String>>? = null,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
    tertiaryAction: Pair<String, () -> Unit>? = null
) {
    // Sound & haptic feedback
    val view = LocalView.current
    val soundManager = LocalSoundManager.current
    LaunchedEffect(Unit) {
        soundManager.performHapticIfEnabled(view, HapticFeedbackConstants.LONG_PRESS)
        if (isWinner) soundManager.play(SoundEffect.CELEBRATION)
    }

    // Entry animations
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    val bgAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "bg_alpha"
    )
    val trophyScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "trophy_scale"
    )
    val titleScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "title_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200),
        label = "content_alpha"
    )

    val gradientColors = if (isWinner) {
        listOf(
            winnerColor.copy(alpha = 0.95f),
            winnerColor.copy(alpha = 0.7f),
            Color.Black.copy(alpha = 0.5f)
        )
    } else {
        listOf(
            Color(0xFF2C2C2C).copy(alpha = 0.95f),
            Color(0xFF1A1A1A).copy(alpha = 0.95f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = bgAlpha }
            .background(
                brush = Brush.radialGradient(gradientColors)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Confetti for winners
        if (isWinner) {
            ConfettiOverlay(trigger = true)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Trophy icon with bounce
            if (isWinner) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Trophy",
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = trophyScale
                            scaleY = trophyScale
                        },
                    tint = Gold
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title with scale bounce
            Text(
                text = title,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isWinner) Color.White else Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = titleScale
                    scaleY = titleScale
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha }
            )

            // Rankings list with staggered entrance
            if (rankings != null && rankings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .graphicsLayer { alpha = contentAlpha }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rankings.forEachIndexed { index, (rank, name) ->
                        StaggeredEntrance(index = index + 2, delayPerItem = 150L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rank,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (index) {
                                        0 -> GoldLight
                                        else -> Color.White.copy(alpha = 0.8f)
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons
            Column(
                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                primaryAction?.let { (label, action) ->
                    GradientButton(
                        onClick = action,
                        gradientColors = if (isWinner) {
                            listOf(GoldLight, Gold)
                        } else {
                            listOf(winnerColor, winnerColor.copy(alpha = 0.7f))
                        },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }
                }

                secondaryAction?.let { (label, action) ->
                    TextButton(onClick = action) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                tertiaryAction?.let { (label, action) ->
                    TextButton(onClick = action) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
