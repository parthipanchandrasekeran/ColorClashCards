package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState
import kotlinx.coroutines.delay

/**
 * Data class representing token animation state.
 */
data class TokenAnimationState(
    val selectedTokenId: Int? = null,
    val previewPath: List<BoardPosition> = emptyList(),
    val isAnimating: Boolean = false,
    val animatingTokenId: Int? = null,
    val animationProgress: Float = 0f
)

/**
 * Enhanced token view with premium animations:
 * - Glow effect and bounce animation for selectable tokens
 * - Selection state with path preview
 * - Smooth movement animation
 */
@Composable
fun PremiumTokenView(
    token: Token,
    color: LudoColor,
    isSelectable: Boolean,
    isSelected: Boolean = false,
    isAnimating: Boolean = false,
    animationProgress: Float = 0f,
    fromPosition: BoardPosition? = null,
    toPosition: BoardPosition? = null,
    cellSize: Dp,
    boardPosition: BoardPosition,
    stackOffset: Pair<Dp, Dp> = Pair(0.dp, 0.dp),
    stackScale: Float = 1f,
    onClick: () -> Unit = {}
) {
    val tokenColor = LudoBoardColors.getColor(color)
    val darkColor = LudoBoardColors.getDarkColor(color)
    val lightColor = LudoBoardColors.getLightColor(color)

    // Infinite transition for selectable animations
    val infiniteTransition = rememberInfiniteTransition(label = "token_premium")

    // Bounce animation (scale pulse) for selectable tokens
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Glow alpha animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Shadow offset animation for "floating" effect
    val shadowOffset by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shadow"
    )

    // Calculate current position (interpolate during animation)
    val currentPosition = if (isAnimating && fromPosition != null && toPosition != null) {
        val fromX = fromPosition.column.toFloat()
        val fromY = fromPosition.row.toFloat()
        val toX = toPosition.column.toFloat()
        val toY = toPosition.row.toFloat()

        // Ease-out interpolation for smooth deceleration
        val easedProgress = 1f - (1f - animationProgress) * (1f - animationProgress)

        BoardPosition(
            column = (fromX + (toX - fromX) * easedProgress).toInt(),
            row = (fromY + (toY - fromY) * easedProgress).toInt()
        )
    } else {
        boardPosition
    }

    // For smooth pixel-perfect animation
    val animatedX = if (isAnimating && fromPosition != null && toPosition != null) {
        val fromX = fromPosition.column * cellSize.value + cellSize.value / 2
        val toX = toPosition.column * cellSize.value + cellSize.value / 2
        val easedProgress = 1f - (1f - animationProgress) * (1f - animationProgress)
        fromX + (toX - fromX) * easedProgress
    } else {
        boardPosition.column * cellSize.value + cellSize.value / 2 + stackOffset.first.value
    }

    val animatedY = if (isAnimating && fromPosition != null && toPosition != null) {
        val fromY = fromPosition.row * cellSize.value + cellSize.value / 2
        val toY = toPosition.row * cellSize.value + cellSize.value / 2
        val easedProgress = 1f - (1f - animationProgress) * (1f - animationProgress)
        fromY + (toY - fromY) * easedProgress
    } else {
        boardPosition.row * cellSize.value + cellSize.value / 2 + stackOffset.second.value
    }

    val tokenSize = cellSize * 0.7f * stackScale
    val baseScale = when {
        isAnimating -> 1.1f // Slightly larger during movement
        isSelectable -> bounceScale
        isSelected -> 1.15f
        else -> 1f
    }
    val scale = baseScale * stackScale

    val currentShadowOffset = if (isSelectable) shadowOffset else 2f

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (animatedX.dp - tokenSize / 2).roundToPx(),
                    (animatedY.dp - tokenSize / 2).roundToPx()
                )
            }
            .size(tokenSize)
            .scale(scale)
            .clickable(
                enabled = isSelectable && !isAnimating,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("token_${token.id}")
            .then(
                if (isSelectable) Modifier.testTag("tokenSelectable_${token.id}") else Modifier
            )
            .semantics {
                contentDescription = when {
                    isAnimating -> "${color.name} token ${token.id} moving"
                    isSelectable -> "${color.name} token ${token.id} - tap to select"
                    isSelected -> "${color.name} token ${token.id} selected"
                    else -> "${color.name} token ${token.id}"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(tokenSize)) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Draw outer glow for selectable tokens
            if (isSelectable) {
                // Multiple glow rings for premium effect
                drawCircle(
                    color = tokenColor.copy(alpha = glowAlpha * 0.3f),
                    radius = radius * 1.6f,
                    center = center
                )
                drawCircle(
                    color = tokenColor.copy(alpha = glowAlpha * 0.5f),
                    radius = radius * 1.35f,
                    center = center
                )
                drawCircle(
                    color = LudoBoardColors.TokenHighlight.copy(alpha = glowAlpha),
                    radius = radius * 1.2f,
                    center = center
                )
            }

            // Draw selection ring
            if (isSelected && !isAnimating) {
                drawCircle(
                    color = Color.White,
                    radius = radius * 1.25f,
                    center = center,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = tokenColor,
                    radius = radius * 1.2f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // Draw shadow (dynamic for selectable)
            drawCircle(
                color = LudoBoardColors.TokenShadow.copy(alpha = if (isSelectable) 0.4f else 0.3f),
                radius = radius,
                center = center.copy(
                    x = center.x + currentShadowOffset,
                    y = center.y + currentShadowOffset
                )
            )

            // Draw main token body with gradient
            val gradient = Brush.radialGradient(
                colors = listOf(lightColor, tokenColor, darkColor),
                center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                radius = radius * 1.5f
            )

            drawCircle(
                brush = gradient,
                radius = radius,
                center = center,
                style = Fill
            )

            // Draw border (thicker when selected)
            drawCircle(
                color = if (isSelected || isSelectable) Color.White else LudoBoardColors.TokenBorder,
                radius = radius,
                center = center,
                style = Stroke(width = if (isSelected || isSelectable) 3f else 2f)
            )

            // Draw inner circle (token detail)
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = radius * 0.5f,
                center = center
            )

            // Draw highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = radius * 0.2f,
                center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f)
            )
        }
    }
}

/**
 * Path preview overlay showing dotted squares along the token's movement path.
 */
@Composable
fun PathPreviewOverlay(
    pathPositions: List<BoardPosition>,
    tokenColor: LudoColor,
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    if (pathPositions.isEmpty()) return

    val color = LudoBoardColors.getColor(tokenColor)

    // Animate path appearance
    val infiniteTransition = rememberInfiniteTransition(label = "path_preview")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "path_alpha"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("pathPreview")
    ) {
        val cellSizePx = cellSize.toPx()
        val dotRadius = cellSizePx * 0.15f
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        pathPositions.forEachIndexed { index, position ->
            val centerX = position.column * cellSizePx + cellSizePx / 2
            val centerY = position.row * cellSizePx + cellSizePx / 2

            // Draw dotted square outline
            val squareSize = cellSizePx * 0.6f
            val topLeft = Offset(centerX - squareSize / 2, centerY - squareSize / 2)

            drawRoundRect(
                color = color.copy(alpha = alpha * (0.5f + 0.5f * (index + 1) / pathPositions.size)),
                topLeft = topLeft,
                size = Size(squareSize, squareSize),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 2f, pathEffect = pathEffect)
            )

            // Draw small dot at center
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(centerX, centerY)
            )

            // Draw step number for the final position
            if (index == pathPositions.lastIndex) {
                drawCircle(
                    color = color.copy(alpha = 0.8f),
                    radius = cellSizePx * 0.2f,
                    center = Offset(centerX, centerY)
                )
            }
        }
    }
}

/**
 * Calculate the path positions for a token's movement.
 * Returns list of BoardPositions from current position to destination.
 *
 * @param token The token to move
 * @param color Token's color
 * @param diceValue Number of steps to move
 * @return List of intermediate positions (not including start, including destination)
 */
fun calculatePathPositions(
    token: Token,
    color: LudoColor,
    diceValue: Int
): List<BoardPosition> {
    if (diceValue <= 0) return emptyList()

    val positions = mutableListOf<BoardPosition>()
    var currentPos = token.position

    // Handle token leaving home
    if (token.state == TokenState.HOME) {
        if (diceValue == 6) {
            // Token exits to starting position (relative 0)
            LudoBoardPositions.getGridPosition(0, color)?.let {
                positions.add(it)
            }
        }
        return positions
    }

    // Calculate each step of the path
    for (step in 1..diceValue) {
        val nextPos = currentPos + 1

        // Check if entering home stretch or finishing
        if (nextPos > 56) {
            // Would overshoot - invalid move (shouldn't happen if validation is correct)
            break
        }

        val boardPos = if (nextPos >= 51) {
            // In home stretch (positions 51-56)
            getHomeStretchBoardPosition(nextPos - 51, color)
        } else {
            LudoBoardPositions.getGridPosition(nextPos, color)
        }

        boardPos?.let { positions.add(it) }
        currentPos = nextPos
    }

    return positions
}

/**
 * Get board position for home stretch.
 */
private fun getHomeStretchBoardPosition(index: Int, color: LudoColor): BoardPosition {
    return when (color) {
        LudoColor.RED -> BoardPosition(7, 1 + index)
        LudoColor.BLUE -> BoardPosition(13 - index, 7)
        LudoColor.GREEN -> BoardPosition(7, 13 - index)
        LudoColor.YELLOW -> BoardPosition(1 + index, 7)
    }
}

/**
 * Animated token that handles movement animation internally.
 */
@Composable
fun AnimatedToken(
    token: Token,
    color: LudoColor,
    isSelectable: Boolean,
    isSelected: Boolean,
    cellSize: Dp,
    boardPosition: BoardPosition,
    targetPosition: BoardPosition?,
    onAnimationComplete: () -> Unit,
    onClick: () -> Unit
) {
    var isAnimating by remember { mutableStateOf(false) }
    var animProgress by remember { mutableStateOf(0f) }
    var fromPos by remember { mutableStateOf<BoardPosition?>(null) }

    // Trigger animation when target changes
    LaunchedEffect(targetPosition) {
        if (targetPosition != null && targetPosition != boardPosition) {
            fromPos = boardPosition
            isAnimating = true
            animProgress = 0f

            // Animate over 600ms (under 700ms requirement)
            val animationDuration = 600L
            val startTime = System.currentTimeMillis()

            while (animProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                animProgress = (elapsed.toFloat() / animationDuration).coerceAtMost(1f)
                delay(16) // ~60fps
            }

            isAnimating = false
            fromPos = null
            onAnimationComplete()
        }
    }

    PremiumTokenView(
        token = token,
        color = color,
        isSelectable = isSelectable && !isAnimating,
        isSelected = isSelected,
        isAnimating = isAnimating,
        animationProgress = animProgress,
        fromPosition = fromPos,
        toPosition = targetPosition,
        cellSize = cellSize,
        boardPosition = if (isAnimating) fromPos ?: boardPosition else boardPosition,
        onClick = onClick
    )
}
