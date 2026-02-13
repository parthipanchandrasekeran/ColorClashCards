package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.parthipan.colorclashcards.audio.LocalSoundManager
import com.parthipan.colorclashcards.audio.SoundEffect
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Animated dice component for Ludo.
 *
 * @param value Current dice value (1-6), null if not rolled yet
 * @param isRolling Whether the dice is currently rolling
 * @param canRoll Whether the dice can be clicked to roll
 * @param size Size of the dice
 * @param onRoll Callback when dice is clicked to roll
 */
@Composable
fun LudoDiceView(
    value: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    size: Dp = 64.dp,
    onRoll: () -> Unit = {}
) {
    // Animation for rolling
    var displayValue by remember { mutableIntStateOf(value ?: 1) }
    val infiniteTransition = rememberInfiniteTransition(label = "dice_roll")

    // X-axis rotation animation when rolling
    val rotationX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationX"
    )

    // Y-axis rotation (500ms period, non-synced with X for natural tumble)
    val rotationY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationY"
    )

    // Scale animation when rolling
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Pulse animation for canRoll state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Sound & haptic feedback
    val view = LocalView.current
    val soundManager = LocalSoundManager.current

    // Particle burst state
    var showParticleBurst by remember { mutableStateOf(false) }
    val burstProgress = remember { Animatable(0f) }

    // Update display value rapidly when rolling, trigger burst on stop
    LaunchedEffect(isRolling) {
        if (isRolling) {
            while (true) {
                displayValue = Random.nextInt(1, 7)
                delay(80)
            }
        } else if (value != null) {
            displayValue = value
            // Enhanced haptic on dice landing
            soundManager.performHapticIfEnabled(view, HapticFeedbackConstants.VIRTUAL_KEY)
            // Trigger particle burst
            showParticleBurst = true
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
            showParticleBurst = false
        }
    }

    // Update display value when value changes
    LaunchedEffect(value) {
        if (!isRolling && value != null) {
            displayValue = value
        }
    }

    val currentRotationX = if (isRolling) rotationX else 0f
    val currentRotationY = if (isRolling) rotationY else 0f
    val currentScale = when {
        isRolling -> scale
        canRoll -> pulseScale
        else -> 1f
    }

    Box(
        modifier = Modifier
            .size(size + 16.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect when can roll
        if (canRoll && !isRolling) {
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Particle burst on roll result
        if (showParticleBurst) {
            val progress = burstProgress.value
            Canvas(
                modifier = Modifier.size(size + 16.dp)
            ) {
                val center = this.center
                val burstRadius = (size.toPx() * 0.8f) * progress
                val particleCount = 12
                val particleAlpha = (1f - progress).coerceAtLeast(0f)
                for (i in 0 until particleCount) {
                    val angle = (i * 360f / particleCount) + (progress * 30f)
                    val rad = Math.toRadians(angle.toDouble())
                    val px = center.x + burstRadius * kotlin.math.cos(rad).toFloat()
                    val py = center.y + burstRadius * kotlin.math.sin(rad).toFloat()
                    val dotColor = when (i % 4) {
                        0 -> Color(0xFFE53935) // Red
                        1 -> Color(0xFF1E88E5) // Blue
                        2 -> Color(0xFF43A047) // Green
                        else -> Color(0xFFFFB300) // Yellow
                    }
                    drawCircle(
                        color = dotColor.copy(alpha = particleAlpha * 0.8f),
                        radius = 3f * (1f - progress * 0.5f),
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Dice body
        Box(
            modifier = Modifier
                .size(size)
                .scale(currentScale)
                .graphicsLayer {
                    this.rotationX = currentRotationX
                    this.rotationY = currentRotationY
                    cameraDistance = 12f * density
                }
                .shadow(
                    elevation = if (isRolling) 8.dp else 4.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF5F5F5)
                        )
                    )
                )
                .clickable(
                    enabled = canRoll && !isRolling,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        soundManager.performHapticIfEnabled(view, HapticFeedbackConstants.VIRTUAL_KEY)
                        soundManager.play(SoundEffect.DICE_ROLL)
                        onRoll()
                    }
                )
                .testTag("rollDiceButton")
                .semantics {
                    contentDescription = when {
                        isRolling -> "Dice rolling"
                        canRoll -> "Tap to roll dice"
                        value != null -> "Dice showing $value"
                        else -> "Dice"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            DiceFace(
                value = displayValue,
                size = size
            )
        }
    }
}

/**
 * Draws the dots on the dice face.
 */
@Composable
private fun DiceFace(
    value: Int,
    size: Dp
) {
    Canvas(modifier = Modifier.size(size * 0.8f)) {
        val canvasSize = size.toPx() * 0.8f
        val dotRadius = canvasSize / 10
        val padding = canvasSize / 5
        val center = canvasSize / 2

        val dotColor = Color(0xFF212121)

        // Dot positions based on dice value
        val positions = when (value) {
            1 -> listOf(
                Offset(center, center)
            )
            2 -> listOf(
                Offset(padding, padding),
                Offset(canvasSize - padding, canvasSize - padding)
            )
            3 -> listOf(
                Offset(padding, padding),
                Offset(center, center),
                Offset(canvasSize - padding, canvasSize - padding)
            )
            4 -> listOf(
                Offset(padding, padding),
                Offset(canvasSize - padding, padding),
                Offset(padding, canvasSize - padding),
                Offset(canvasSize - padding, canvasSize - padding)
            )
            5 -> listOf(
                Offset(padding, padding),
                Offset(canvasSize - padding, padding),
                Offset(center, center),
                Offset(padding, canvasSize - padding),
                Offset(canvasSize - padding, canvasSize - padding)
            )
            6 -> listOf(
                Offset(padding, padding),
                Offset(canvasSize - padding, padding),
                Offset(padding, center),
                Offset(canvasSize - padding, center),
                Offset(padding, canvasSize - padding),
                Offset(canvasSize - padding, canvasSize - padding)
            )
            else -> emptyList()
        }

        positions.forEach { position ->
            // Shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.2f),
                radius = dotRadius,
                center = Offset(position.x + 1f, position.y + 1f)
            )
            // Dot
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = position
            )
            // Inner highlight for 3D inset look
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = dotRadius * 0.45f,
                center = Offset(position.x - dotRadius * 0.2f, position.y - dotRadius * 0.2f)
            )
        }
    }
}

/**
 * Compact dice indicator showing the last rolled value.
 */
@Composable
fun DiceValueIndicator(
    value: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .shadow(2.dp, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (value != null) {
            DiceFace(value = value, size = size)
        }
    }
}

/**
 * Dice with label showing who should roll.
 */
@Composable
fun DiceWithLabel(
    value: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    playerName: String,
    playerColor: Color,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        LudoDiceView(
            value = value,
            isRolling = isRolling,
            canRoll = canRoll,
            onRoll = onRoll
        )
    }
}

/**
 * Dice with a circular timer ring showing remaining turn time.
 *
 * @param value Current dice value (1-6), null if not rolled yet
 * @param isRolling Whether the dice is currently rolling
 * @param canRoll Whether the dice can be clicked to roll
 * @param timerProgress Progress from 1.0 (full) to 0.0 (empty)
 * @param remainingSeconds Seconds remaining (for display)
 * @param showTimer Whether to show the timer ring
 * @param isWarning Whether timer is in warning state (< 10 seconds)
 * @param size Size of the dice
 * @param onRoll Callback when dice is clicked to roll
 */
@Composable
fun DiceWithTimer(
    value: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    timerProgress: Float,
    remainingSeconds: Int,
    showTimer: Boolean,
    isWarning: Boolean = false,
    size: Dp = 56.dp,
    onRoll: () -> Unit = {}
) {
    val warningColor = Color(0xFFE53935) // Red
    val normalColor = MaterialTheme.colorScheme.primary
    val timerColor = if (isWarning) warningColor else normalColor

    // Animated progress for smooth countdown
    val animatedProgress by animateFloatAsState(
        targetValue = timerProgress,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "timer_progress"
    )

    Box(
        modifier = Modifier
            .size(size + 24.dp)
            .testTag("diceTimerContainer"),
        contentAlignment = Alignment.Center
    ) {
        // Timer ring (drawn behind dice)
        if (showTimer) {
            Canvas(
                modifier = Modifier
                    .size(size + 20.dp)
                    .testTag("diceTimerRing")
            ) {
                val strokeWidth = 4.dp.toPx()
                val radius = (this.size.minDimension - strokeWidth) / 2
                val center = this.center

                // Background track
                drawCircle(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Progress arc (starts at top, goes clockwise)
                drawArc(
                    color = timerColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }

            // Timer text (shown when warning)
            if (isWarning && remainingSeconds <= 10) {
                Text(
                    text = "$remainingSeconds",
                    style = MaterialTheme.typography.labelSmall,
                    color = warningColor,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .testTag("turnTimerText")
                )
            }
        }

        // Dice
        LudoDiceView(
            value = value,
            isRolling = isRolling,
            canRoll = canRoll,
            size = size,
            onRoll = onRoll
        )
    }
}
