package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.Token
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/** Token diameter as a fraction of cell size. */
private const val TOKEN_SIZE_RATIO = 0.7f

/**
 * Renders a single Ludo token with animations.
 *
 * @param token The token data
 * @param color The player's color
 * @param isSelectable Whether this token can be selected (for move)
 * @param isSelected Whether this token is currently selected
 * @param cellSize Size of a board cell for positioning
 * @param boardPosition Position on the board grid
 * @param stackOffset Offset within cell when multiple tokens share position (x, y in dp)
 * @param stackScale Scale factor for stacked tokens (smaller when stacked)
 * @param onClick Callback when token is clicked
 */
@Composable
fun LudoTokenView(
    token: Token,
    color: LudoColor,
    isSelectable: Boolean,
    isSelected: Boolean = false,
    cellSize: Dp,
    boardPosition: BoardPosition,
    stackOffset: Pair<Dp, Dp> = Pair(0.dp, 0.dp),
    stackScale: Float = 1f,
    onClick: () -> Unit = {}
) {
    val tokenColor = LudoBoardColors.getColor(color)
    val darkColor = LudoBoardColors.getDarkColor(color)
    val lightColor = LudoBoardColors.getLightColor(color)

    // Pulse animation for selectable tokens
    val infiniteTransition = rememberInfiniteTransition(label = "token_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Scale for selection state, combined with stack scale
    val baseScale = if (isSelectable) pulseScale else 1f
    val scale = baseScale * stackScale

    // Calculate pixel offset from board position, including stack offset
    val xOffset = (boardPosition.column * cellSize.value + cellSize.value / 2).dp + stackOffset.first
    val yOffset = (boardPosition.row * cellSize.value + cellSize.value / 2).dp + stackOffset.second
    val tokenSize = cellSize * TOKEN_SIZE_RATIO * stackScale

    Box(
        modifier = Modifier
            .offset { IntOffset(xOffset.roundToPx() - (tokenSize / 2).roundToPx(), yOffset.roundToPx() - (tokenSize / 2).roundToPx()) }
            .size(tokenSize)
            .scale(scale)
            .clickable(
                enabled = isSelectable,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("token_${color.name.lowercase()}_${token.id}")
            .semantics {
                contentDescription = when {
                    isSelectable -> "${color.name} token ${token.id} - tap to move"
                    else -> "${color.name} token ${token.id}"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(tokenSize)) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Draw glow effect for selectable tokens
            if (isSelectable) {
                drawCircle(
                    color = LudoBoardColors.TokenHighlight.copy(alpha = glowAlpha),
                    radius = radius * 1.3f,
                    center = center
                )
            }

            // Draw shadow
            drawCircle(
                color = LudoBoardColors.TokenShadow,
                radius = radius,
                center = center.copy(x = center.x + 2f, y = center.y + 2f)
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

            // Draw border
            drawCircle(
                color = LudoBoardColors.TokenBorder,
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Draw inner circle (token detail)
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = radius * 0.5f,
                center = center
            )

            // Draw highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = radius * 0.2f,
                center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f)
            )

            // Draw selection indicator
            if (isSelected) {
                drawCircle(
                    color = LudoBoardColors.TokenHighlight,
                    radius = radius * 1.1f,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

/**
 * Renders all tokens for a player on the board.
 * Handles stacking when multiple tokens occupy the same cell.
 *
 * @param tokens List of player's tokens
 * @param color Player's color
 * @param selectableTokenIds IDs of tokens that can be moved
 * @param cellSize Size of a board cell
 * @param onTokenClick Callback when a token is clicked
 */
@Composable
fun PlayerTokensView(
    tokens: List<Token>,
    color: LudoColor,
    selectableTokenIds: List<Int>,
    cellSize: Dp,
    onTokenClick: (Int) -> Unit
) {
    // Group tokens by their board position to detect stacking
    val tokensByPosition = tokens
        .mapNotNull { token ->
            val position = getTokenBoardPosition(token, color)
            if (position != null) Pair(token, position) else null
        }
        .groupBy { it.second }

    // Render each token with appropriate offset if stacked
    tokensByPosition.forEach { (position, tokensAtPosition) ->
        val stackCount = tokensAtPosition.size
        val (offsets, stackScale) = calculateStackOffsets(stackCount, cellSize)

        tokensAtPosition.forEachIndexed { index, (token, _) ->
            val offset = offsets.getOrElse(index) { Pair(0.dp, 0.dp) }

            LudoTokenView(
                token = token,
                color = color,
                isSelectable = token.id in selectableTokenIds,
                cellSize = cellSize,
                boardPosition = position,
                stackOffset = offset,
                stackScale = stackScale,
                onClick = { onTokenClick(token.id) }
            )
        }
    }
}

/**
 * Preview composable for a single token.
 */
@Composable
fun TokenPreview(
    color: LudoColor = LudoColor.RED,
    isSelectable: Boolean = false,
    size: Dp = 40.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = size.toPx() / 2
        val center = Offset(size.toPx() / 2, size.toPx() / 2)

        val tokenColor = LudoBoardColors.getColor(color)
        val darkColor = LudoBoardColors.getDarkColor(color)
        val lightColor = LudoBoardColors.getLightColor(color)

        // Gradient
        val gradient = Brush.radialGradient(
            colors = listOf(lightColor, tokenColor, darkColor),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
            radius = radius * 1.5f
        )

        drawCircle(brush = gradient, radius = radius, center = center)
        drawCircle(color = LudoBoardColors.TokenBorder, radius = radius, center = center, style = Stroke(width = 2f))
        drawCircle(color = Color.White.copy(alpha = 0.4f), radius = radius * 0.5f, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = radius * 0.2f,
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f))
    }
}
