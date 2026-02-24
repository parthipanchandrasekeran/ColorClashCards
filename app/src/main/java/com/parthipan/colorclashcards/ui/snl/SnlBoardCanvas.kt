package com.parthipan.colorclashcards.ui.snl

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.parthipan.colorclashcards.game.snl.engine.SnlBoard
import com.parthipan.colorclashcards.game.snl.model.SnlGameState
import com.parthipan.colorclashcards.game.snl.model.SnlPlayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val GRID_SIZE = 10

/**
 * SNL board canvas rendering the 10x10 grid with snakes, ladders, and player tokens.
 * Board renders with row 0 at the BOTTOM (square 1 bottom-left, square 100 top-left).
 */
@Composable
fun SnlBoardCanvas(
    gameState: SnlGameState,
    animatingPlayerId: String? = null,
    animFromPos: Int = 0,
    animToPos: Int = 0,
    animationProgress: Float = 0f,
    isAnimating: Boolean = false,
    modifier: Modifier = Modifier
) {
    val reusablePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val cellSize = size.minDimension / GRID_SIZE

        // Draw board background
        drawRect(color = SnlBoardColors.BoardBackground, size = size)

        // Draw grid cells with alternating colors and numbers
        drawGridCells(cellSize)

        // Draw ladders (behind snakes so snakes draw on top)
        drawLadders(gameState.ladders, cellSize, reusablePath)

        // Draw snakes
        drawSnakes(gameState.snakes, cellSize, reusablePath)

        // Draw player tokens
        drawTokens(gameState.players, animatingPlayerId, animFromPos, animToPos, animationProgress, isAnimating, cellSize)
    }
}

/**
 * Small preview board for the home screen (no tokens, just grid + snakes + ladders).
 */
@Composable
fun SnlBoardPreview(
    modifier: Modifier = Modifier
) {
    val reusablePath = remember { Path() }
    val snakes = remember { SnlBoard.CLASSIC_SNAKES }
    val ladders = remember { SnlBoard.CLASSIC_LADDERS }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val cellSize = size.minDimension / GRID_SIZE

        drawRect(color = SnlBoardColors.BoardBackground, size = size)
        drawGridCells(cellSize)
        drawLadders(ladders, cellSize, reusablePath)
        drawSnakes(snakes, cellSize, reusablePath)
    }
}

// ==================== GRID ====================

private fun DrawScope.drawGridCells(cellSize: Float) {
    val textPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#5D4037")
        textSize = cellSize * 0.28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    for (row in 0 until GRID_SIZE) {
        for (col in 0 until GRID_SIZE) {
            val square = SnlBoard.getSquare(row, col)
            // Screen Y: row 0 (bottom) maps to screen bottom
            val screenRow = GRID_SIZE - 1 - row
            val x = col * cellSize
            val y = screenRow * cellSize

            // Alternate cell colors (checkerboard)
            val isLight = (row + col) % 2 == 0
            val cellColor = when {
                square == 100 -> SnlBoardColors.Square100
                square == 1 -> SnlBoardColors.Square1
                isLight -> SnlBoardColors.SquareLight
                else -> SnlBoardColors.SquareDark
            }

            drawRect(
                color = cellColor,
                topLeft = Offset(x, y),
                size = Size(cellSize, cellSize)
            )

            // Grid line
            drawRect(
                color = SnlBoardColors.GridLine,
                topLeft = Offset(x, y),
                size = Size(cellSize, cellSize),
                style = Stroke(width = 0.5f)
            )

            // Square number
            drawContext.canvas.nativeCanvas.drawText(
                "$square",
                x + cellSize / 2,
                y + cellSize * 0.35f,
                textPaint
            )
        }
    }
}

// ==================== SNAKES ====================

private fun DrawScope.drawSnakes(snakes: Map<Int, Int>, cellSize: Float, path: Path) {
    snakes.forEach { (head, tail) ->
        val headCenter = squareToScreenCenter(head, cellSize)
        val tailCenter = squareToScreenCenter(tail, cellSize)
        drawSnake(headCenter, tailCenter, cellSize, path)
    }
}

private fun DrawScope.drawSnake(from: Offset, to: Offset, cellSize: Float, path: Path) {
    val strokeWidth = cellSize * 0.15f

    // Draw wavy snake body using a series of curves
    path.reset()
    path.moveTo(from.x, from.y)

    val dx = to.x - from.x
    val dy = to.y - from.y
    val segments = 4
    val amplitude = cellSize * 0.3f

    for (i in 0 until segments) {
        val t1 = (i + 0.5f) / segments
        val t2 = (i + 1f) / segments
        val midX = from.x + dx * t1
        val midY = from.y + dy * t1
        val endX = from.x + dx * t2
        val endY = from.y + dy * t2

        // Alternate the curve direction for wavy effect
        val perpX = -dy / kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val perpY = dx / kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val sign = if (i % 2 == 0) 1f else -1f

        path.quadraticTo(
            midX + perpX * amplitude * sign,
            midY + perpY * amplitude * sign,
            endX,
            endY
        )
    }

    // Draw shadow
    drawPath(
        path = path,
        color = Color.Black.copy(alpha = 0.2f),
        style = Stroke(width = strokeWidth + 2f)
    )

    // Draw snake body
    drawPath(
        path = path,
        color = SnlBoardColors.SnakeBody,
        style = Stroke(width = strokeWidth)
    )

    // Draw snake body inner (lighter line)
    drawPath(
        path = path,
        color = SnlBoardColors.SnakeBody.copy(alpha = 0.5f),
        style = Stroke(width = strokeWidth * 0.4f)
    )

    // Snake head (circle at start)
    drawCircle(
        color = SnlBoardColors.SnakeBodyDark,
        radius = cellSize * 0.15f,
        center = from
    )
    // Eyes
    drawCircle(
        color = SnlBoardColors.SnakeEye,
        radius = cellSize * 0.04f,
        center = Offset(from.x - cellSize * 0.06f, from.y - cellSize * 0.04f)
    )
    drawCircle(
        color = SnlBoardColors.SnakeEye,
        radius = cellSize * 0.04f,
        center = Offset(from.x + cellSize * 0.06f, from.y - cellSize * 0.04f)
    )
}

// ==================== LADDERS ====================

private fun DrawScope.drawLadders(ladders: Map<Int, Int>, cellSize: Float, path: Path) {
    ladders.forEach { (bottom, top) ->
        val bottomCenter = squareToScreenCenter(bottom, cellSize)
        val topCenter = squareToScreenCenter(top, cellSize)
        drawLadder(bottomCenter, topCenter, cellSize)
    }
}

private fun DrawScope.drawLadder(from: Offset, to: Offset, cellSize: Float) {
    val railWidth = cellSize * 0.25f
    val railStroke = cellSize * 0.06f

    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

    // Perpendicular unit vector for rail offset
    val perpX = -dy / length
    val perpY = dx / length

    val leftStart = Offset(from.x + perpX * railWidth, from.y + perpY * railWidth)
    val leftEnd = Offset(to.x + perpX * railWidth, to.y + perpY * railWidth)
    val rightStart = Offset(from.x - perpX * railWidth, from.y - perpY * railWidth)
    val rightEnd = Offset(to.x - perpX * railWidth, to.y - perpY * railWidth)

    // Shadow
    drawLine(
        color = Color.Black.copy(alpha = 0.15f),
        start = Offset(leftStart.x + 2, leftStart.y + 2),
        end = Offset(leftEnd.x + 2, leftEnd.y + 2),
        strokeWidth = railStroke + 2f
    )
    drawLine(
        color = Color.Black.copy(alpha = 0.15f),
        start = Offset(rightStart.x + 2, rightStart.y + 2),
        end = Offset(rightEnd.x + 2, rightEnd.y + 2),
        strokeWidth = railStroke + 2f
    )

    // Left rail
    drawLine(
        color = SnlBoardColors.LadderRailDark,
        start = leftStart,
        end = leftEnd,
        strokeWidth = railStroke
    )

    // Right rail
    drawLine(
        color = SnlBoardColors.LadderRailDark,
        start = rightStart,
        end = rightEnd,
        strokeWidth = railStroke
    )

    // Rungs
    val rungCount = (length / (cellSize * 0.5f)).toInt().coerceIn(3, 10)
    for (i in 1 until rungCount) {
        val t = i.toFloat() / rungCount
        val rungLeft = Offset(
            leftStart.x + (leftEnd.x - leftStart.x) * t,
            leftStart.y + (leftEnd.y - leftStart.y) * t
        )
        val rungRight = Offset(
            rightStart.x + (rightEnd.x - rightStart.x) * t,
            rightStart.y + (rightEnd.y - rightStart.y) * t
        )

        drawLine(
            color = SnlBoardColors.LadderRung,
            start = rungLeft,
            end = rungRight,
            strokeWidth = railStroke * 0.8f
        )
    }
}

// ==================== TOKENS ====================

private fun DrawScope.drawTokens(
    players: List<SnlPlayer>,
    animatingPlayerId: String?,
    animFromPos: Int,
    animToPos: Int,
    animationProgress: Float,
    isAnimating: Boolean,
    cellSize: Float
) {
    // Group players by position for stacking
    val onBoardPlayers = players.filter { it.isOnBoard || (isAnimating && it.id == animatingPlayerId) }

    // Calculate positions (with animation offset for animating player)
    data class TokenInfo(val player: SnlPlayer, val center: Offset)

    val tokenInfos = onBoardPlayers.map { player ->
        val center = if (isAnimating && player.id == animatingPlayerId && animFromPos > 0) {
            val fromCenter = squareToScreenCenter(animFromPos, cellSize)
            val toCenter = squareToScreenCenter(animToPos, cellSize)
            Offset(
                fromCenter.x + (toCenter.x - fromCenter.x) * animationProgress,
                fromCenter.y + (toCenter.y - fromCenter.y) * animationProgress
            )
        } else if (player.position > 0) {
            squareToScreenCenter(player.position, cellSize)
        } else {
            null
        }
        center?.let { TokenInfo(player, it) }
    }.filterNotNull()

    // Group by approximate position for stacking offsets
    val grouped = tokenInfos.groupBy { info ->
        // Round to nearest cell center to group tokens on same square
        val cx = (info.center.x / cellSize).toInt()
        val cy = (info.center.y / cellSize).toInt()
        cx to cy
    }

    grouped.forEach { (_, tokensAtPos) ->
        val count = tokensAtPos.size
        tokensAtPos.forEachIndexed { index, info ->
            val offset = getStackOffset(index, count, cellSize)
            drawPlayerToken(
                center = Offset(info.center.x + offset.x, info.center.y + offset.y),
                colorIndex = info.player.colorIndex,
                cellSize = cellSize,
                isAnimating = isAnimating && info.player.id == animatingPlayerId
            )
        }
    }
}

private fun DrawScope.drawPlayerToken(
    center: Offset,
    colorIndex: Int,
    cellSize: Float,
    isAnimating: Boolean
) {
    val radius = cellSize * 0.28f
    val color = SnlBoardColors.getPlayerColor(colorIndex)
    val darkColor = SnlBoardColors.getPlayerColorDark(colorIndex)

    // Shadow
    drawCircle(
        color = SnlBoardColors.TokenShadow,
        radius = radius + 1f,
        center = Offset(center.x + 1f, center.y + 2f)
    )

    // Token body
    drawCircle(
        color = color,
        radius = radius,
        center = center
    )

    // Inner highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = radius * 0.5f,
        center = Offset(center.x - radius * 0.15f, center.y - radius * 0.15f)
    )

    // Border
    drawCircle(
        color = darkColor,
        radius = radius,
        center = center,
        style = Stroke(width = 2f)
    )
}

private fun getStackOffset(index: Int, count: Int, cellSize: Float): Offset {
    if (count <= 1) return Offset.Zero
    val offsetAmount = cellSize * 0.18f
    return when (count) {
        2 -> when (index) {
            0 -> Offset(-offsetAmount, 0f)
            else -> Offset(offsetAmount, 0f)
        }
        3 -> when (index) {
            0 -> Offset(0f, -offsetAmount)
            1 -> Offset(-offsetAmount, offsetAmount * 0.5f)
            else -> Offset(offsetAmount, offsetAmount * 0.5f)
        }
        else -> when (index) {
            0 -> Offset(-offsetAmount, -offsetAmount)
            1 -> Offset(offsetAmount, -offsetAmount)
            2 -> Offset(-offsetAmount, offsetAmount)
            else -> Offset(offsetAmount, offsetAmount)
        }
    }
}

// ==================== COORDINATE HELPERS ====================

/**
 * Convert a board square (1-100) to screen pixel center.
 * Row 0 (squares 1-10) renders at the BOTTOM of the canvas.
 */
private fun DrawScope.squareToScreenCenter(square: Int, cellSize: Float): Offset {
    val (row, col) = SnlBoard.getRowCol(square)
    val screenRow = GRID_SIZE - 1 - row
    return Offset(
        col * cellSize + cellSize / 2,
        screenRow * cellSize + cellSize / 2
    )
}
