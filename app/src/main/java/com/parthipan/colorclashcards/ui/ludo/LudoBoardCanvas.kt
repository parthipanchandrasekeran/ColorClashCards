package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import com.parthipan.colorclashcards.game.ludo.engine.LudoBoard
import com.parthipan.colorclashcards.game.ludo.model.LudoColor

/**
 * Classic Ludo Board Canvas (15x15 grid)
 *
 * QUADRANT LAYOUT:
 * - Top-left: GREEN home
 * - Top-right: YELLOW home
 * - Bottom-left: RED home
 * - Bottom-right: BLUE home
 */
@Composable
fun LudoBoardCanvas(
    modifier: Modifier = Modifier
) {
    // Single source of truth for board size - measured during layout phase
    var measuredBoardSize by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Measure size during layout phase (not draw phase) for consistency
            .onSizeChanged { size ->
                // Use minDimension for square board
                val newSize = minOf(size.width, size.height).toFloat()
                if (measuredBoardSize != newSize) {
                    measuredBoardSize = newSize
                }
            }
    ) {
        // Use measuredBoardSize from layout phase for perfect consistency
        // Fall back to size.minDimension only if not yet measured
        val canvasBoardSize = if (measuredBoardSize > 0f) measuredBoardSize else size.minDimension
        val cellSize = canvasBoardSize / 15f

        // Draw board background
        drawRect(
            color = LudoBoardColors.BoardBackground,
            size = Size(canvasBoardSize, canvasBoardSize)
        )

        // Draw home bases (corners) - CLASSIC LAYOUT
        drawHomeBase(LudoColor.GREEN, 0f, 0f, cellSize)           // Top-left
        drawHomeBase(LudoColor.YELLOW, 9 * cellSize, 0f, cellSize) // Top-right
        drawHomeBase(LudoColor.RED, 0f, 9 * cellSize, cellSize)    // Bottom-left
        drawHomeBase(LudoColor.BLUE, 9 * cellSize, 9 * cellSize, cellSize) // Bottom-right

        // Draw the track (cross-shaped path)
        drawTrack(cellSize)

        // Draw home lanes (finish stretches)
        drawHomeLane(LudoColor.GREEN, cellSize)
        drawHomeLane(LudoColor.YELLOW, cellSize)
        drawHomeLane(LudoColor.RED, cellSize)
        drawHomeLane(LudoColor.BLUE, cellSize)

        // Draw center finish area
        drawCenterFinish(cellSize)

        // Draw safe cells (stars) and start cells (arrows)
        drawSafeCells(cellSize)

        // Draw grid border
        drawTrackGridLines(cellSize)
    }
}

/**
 * Draw a home base (6x6 area in a corner).
 */
private fun DrawScope.drawHomeBase(
    color: LudoColor,
    x: Float,
    y: Float,
    cellSize: Float
) {
    val baseSize = 6 * cellSize
    val playerColor = LudoBoardColors.getColor(color)
    val lightColor = LudoBoardColors.getLightColor(color)

    // Draw colored background
    drawRect(
        color = playerColor,
        topLeft = Offset(x, y),
        size = Size(baseSize, baseSize)
    )

    // Draw inner white area for tokens
    val innerPadding = cellSize * 0.8f
    val innerSize = baseSize - (innerPadding * 2)

    drawRect(
        color = Color.White,
        topLeft = Offset(x + innerPadding, y + innerPadding),
        size = Size(innerSize, innerSize)
    )

    // Draw token circles (home positions) based on HOME_SLOTS_BY_COLOR
    val tokenRadius = cellSize * 0.5f
    val homeSlots = LudoBoard.HOME_SLOTS_BY_COLOR[color] ?: return

    homeSlots.forEach { (row, col) ->
        val pos = Offset(col * cellSize + cellSize / 2, row * cellSize + cellSize / 2)
        // Draw token holder circle
        drawCircle(
            color = lightColor,
            radius = tokenRadius,
            center = pos
        )
        drawCircle(
            color = playerColor,
            radius = tokenRadius,
            center = pos,
            style = Stroke(width = 2f)
        )
    }

    // Draw border
    drawRect(
        color = LudoBoardColors.TrackBorder,
        topLeft = Offset(x, y),
        size = Size(baseSize, baseSize),
        style = Stroke(width = 2f)
    )
}

/**
 * Draw the cross-shaped track around the board.
 */
private fun DrawScope.drawTrack(cellSize: Float) {
    // Top vertical arm (columns 6-8, rows 0-5)
    for (row in 0 until 6) {
        for (col in 6 until 9) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Left horizontal arm (columns 0-5, rows 6-8)
    for (row in 6 until 9) {
        for (col in 0 until 6) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Right horizontal arm (columns 9-14, rows 6-8)
    for (row in 6 until 9) {
        for (col in 9 until 15) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Bottom vertical arm (columns 6-8, rows 9-14)
    for (row in 9 until 15) {
        for (col in 6 until 9) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }
}

/**
 * Draw a single track cell.
 */
private fun DrawScope.drawTrackCell(x: Float, y: Float, cellSize: Float) {
    drawRect(
        color = LudoBoardColors.TrackWhite,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize)
    )
    drawRect(
        color = LudoBoardColors.TrackBorder,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize),
        style = Stroke(width = 1f)
    )
}

/**
 * Draw the colored home lane (finish stretch) for a player.
 */
private fun DrawScope.drawHomeLane(color: LudoColor, cellSize: Float) {
    val playerColor = LudoBoardColors.getColor(color)
    val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color] ?: return

    laneCells.forEach { (row, col) ->
        drawColoredCell(col * cellSize, row * cellSize, cellSize, playerColor)
    }
}

/**
 * Draw a colored cell.
 */
private fun DrawScope.drawColoredCell(x: Float, y: Float, cellSize: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize)
    )
    drawRect(
        color = LudoBoardColors.TrackBorder,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize),
        style = Stroke(width = 1f)
    )
}

/**
 * Draw the center finish area with triangles.
 * Center area is cells (6,6) to (8,8) - a 3x3 grid.
 * Center point is at cell (7,7) center = (7.5, 7.5) in cell units.
 */
private fun DrawScope.drawCenterFinish(cellSize: Float) {
    // Center area boundaries (in pixels)
    val left = 6 * cellSize
    val top = 6 * cellSize
    val right = 9 * cellSize
    val bottom = 9 * cellSize

    // Exact center of the 3x3 center area
    val centerX = left + (right - left) / 2  // = 7.5 * cellSize
    val centerY = top + (bottom - top) / 2   // = 7.5 * cellSize

    // Draw four triangles pointing to center - matching classic layout
    // GREEN: left triangle (apex at center, base on left edge)
    drawTriangle(
        centerX, centerY,
        left, top,
        left, bottom,
        LudoBoardColors.getColor(LudoColor.GREEN)
    )

    // YELLOW: top triangle (apex at center, base on top edge)
    drawTriangle(
        centerX, centerY,
        left, top,
        right, top,
        LudoBoardColors.getColor(LudoColor.YELLOW)
    )

    // BLUE: right triangle (apex at center, base on right edge)
    drawTriangle(
        centerX, centerY,
        right, top,
        right, bottom,
        LudoBoardColors.getColor(LudoColor.BLUE)
    )

    // RED: bottom triangle (apex at center, base on bottom edge)
    drawTriangle(
        centerX, centerY,
        left, bottom,
        right, bottom,
        LudoBoardColors.getColor(LudoColor.RED)
    )
}

/**
 * Draw a triangle with given apex and two base points.
 */
private fun DrawScope.drawTriangle(
    apexX: Float, apexY: Float,
    baseX1: Float, baseY1: Float,
    baseX2: Float, baseY2: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(apexX, apexY)
        lineTo(baseX1, baseY1)
        lineTo(baseX2, baseY2)
        close()
    }

    drawPath(path = path, color = color, style = Fill)
    drawPath(path = path, color = LudoBoardColors.TrackBorder, style = Stroke(width = 2f))
}

/**
 * Draw safe cells (star positions) and start cells (arrows) on the track.
 */
private fun DrawScope.drawSafeCells(cellSize: Float) {
    // Start cells (colored with arrow/star)
    LudoColor.entries.forEach { color ->
        val startCell = LudoBoard.START_CELL_BY_COLOR[color] ?: return@forEach
        val (row, col) = startCell
        drawStartingCell(col * cellSize, row * cellSize, cellSize, LudoBoardColors.getColor(color))
    }

    // Additional safe cells (gray stars) - 8 positions after each start
    val starIndices = listOf(8, 21, 34, 47)
    starIndices.forEach { index ->
        val cell = LudoBoard.getRingCell(index) ?: return@forEach
        val (row, col) = cell
        drawSafeStarCell(col * cellSize, row * cellSize, cellSize)
    }
}

/**
 * Draw a starting cell with star indicator.
 */
private fun DrawScope.drawStartingCell(x: Float, y: Float, cellSize: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize)
    )

    // Draw small star indicator
    val centerX = x + cellSize / 2
    val centerY = y + cellSize / 2
    val starRadius = cellSize * 0.3f

    drawStar(centerX, centerY, starRadius, Color.White)

    drawRect(
        color = LudoBoardColors.TrackBorder,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize),
        style = Stroke(width = 1f)
    )
}

/**
 * Draw a safe cell with star.
 */
private fun DrawScope.drawSafeStarCell(x: Float, y: Float, cellSize: Float) {
    val centerX = x + cellSize / 2
    val centerY = y + cellSize / 2
    val starRadius = cellSize * 0.35f

    drawStar(centerX, centerY, starRadius, LudoBoardColors.SafeCell)
}

/**
 * Draw a 5-pointed star.
 */
private fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color) {
    val path = Path()
    val outerRadius = radius
    val innerRadius = radius * 0.5f
    val points = 5

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = Math.PI / 2 - (i * Math.PI / points)
        val x = cx + (r * kotlin.math.cos(angle)).toFloat()
        val y = cy - (r * kotlin.math.sin(angle)).toFloat()

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(path = path, color = color, style = Fill)
    drawPath(path = path, color = LudoBoardColors.TrackBorder, style = Stroke(width = 1f))
}

/**
 * Draw grid lines for the track areas.
 */
private fun DrawScope.drawTrackGridLines(cellSize: Float) {
    val boardSize = 15 * cellSize
    drawRect(
        color = LudoBoardColors.TrackBorder,
        topLeft = Offset(0f, 0f),
        size = Size(boardSize, boardSize),
        style = Stroke(width = 3f)
    )
}

// ==================== BOARD POSITION UTILITIES ====================

/**
 * Data class representing a position on the Ludo board grid.
 */
data class BoardPosition(
    val column: Int,
    val row: Int
)

/**
 * Utility object for converting between game positions and board coordinates.
 */
object LudoBoardPositions {

    /**
     * Get the board grid position for a token based on its relative position and color.
     */
    fun getGridPosition(relativePosition: Int, color: LudoColor): BoardPosition? {
        if (relativePosition < 0) return null
        if (relativePosition >= LudoBoard.FINISH_POSITION) return null

        // Lane positions (51-56)
        if (relativePosition > LudoBoard.RING_END) {
            val laneIndex = relativePosition - LudoBoard.LANE_START
            val cell = LudoBoard.getLaneCell(color, laneIndex) ?: return null
            return BoardPosition(cell.second, cell.first)
        }

        // Ring positions (0-50)
        val absoluteIndex = LudoBoard.toAbsolutePosition(relativePosition, color)
        if (absoluteIndex < 0) return null

        val cell = LudoBoard.getRingCell(absoluteIndex) ?: return null
        return BoardPosition(cell.second, cell.first)
    }

    /**
     * Get the home base token positions for a color.
     */
    fun getHomeBasePositions(color: LudoColor): List<BoardPosition> {
        val slots = LudoBoard.HOME_SLOTS_BY_COLOR[color] ?: return emptyList()
        return slots.map { (row, col) -> BoardPosition(col, row) }
    }

    /**
     * Get the center finish position.
     */
    fun getFinishPosition(): BoardPosition {
        return BoardPosition(LudoBoard.CENTER_CELL.second, LudoBoard.CENTER_CELL.first)
    }
}
