package com.parthipan.colorclashcards.ui.ludo

import android.util.Log
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

/** Grid size for the Ludo board (15x15). */
private const val BOARD_GRID_SIZE = 15

/** Size of each home base in cells (6x6 corners). */
private const val HOME_SIZE_CELLS = 6

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
    // Reusable Path to avoid allocations during draw
    val reusablePath = remember { Path() }

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
        val cellSize = canvasBoardSize / BOARD_GRID_SIZE

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

        // Draw home lanes (finish stretches) - cells outside center area
        drawHomeLane(LudoColor.GREEN, cellSize, excludeCenter = true)
        drawHomeLane(LudoColor.YELLOW, cellSize, excludeCenter = true)
        drawHomeLane(LudoColor.RED, cellSize, excludeCenter = true)
        drawHomeLane(LudoColor.BLUE, cellSize, excludeCenter = true)

        // Draw center finish area (clean triangles, no grid lines)
        drawCenterFinish(cellSize, reusablePath)

        // Draw safe cells (stars) and start cells (arrows)
        drawSafeCells(cellSize, reusablePath)

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
    val baseSize = HOME_SIZE_CELLS * cellSize
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

    // Draw token holder circles using getHomeSlotOffset for exact alignment
    val tokenRadius = cellSize * 0.5f

    for (slotIndex in 0..3) {
        val (cx, cy) = LudoBoardPositions.getHomeSlotOffset(color, slotIndex)
        val pos = Offset(cx * cellSize, cy * cellSize)
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
 * Classic Ludo board track layout (15x15 grid):
 * - Top arm: rows 0-5, cols 6-8 (3 cells wide, 6 cells tall)
 * - Bottom arm: rows 9-14, cols 6-8 (3 cells wide, 6 cells tall)
 * - Left arm: rows 6-8, cols 0-5 (6 cells wide, 3 cells tall)
 * - Right arm: rows 6-8, cols 9-14 (6 cells wide, 3 cells tall)
 * - Center: rows 6-8, cols 6-8 (3x3 grid, covered by triangles)
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
        for (col in 9 until BOARD_GRID_SIZE) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Bottom vertical arm (columns 6-8, rows 9-14)
    for (row in 9 until BOARD_GRID_SIZE) {
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
private fun DrawScope.drawHomeLane(
    color: LudoColor,
    cellSize: Float,
    excludeCenter: Boolean = false,
    onlyCenterCells: Boolean = false
) {
    val playerColor = LudoBoardColors.getColor(color)
    val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color] ?: return

    // Center area is rows 6-8, cols 6-8
    fun isInCenterArea(row: Int, col: Int): Boolean {
        return row in 6..8 && col in 6..8
    }

    laneCells.forEach { (row, col) ->
        val inCenter = isInCenterArea(row, col)
        val shouldDraw = when {
            onlyCenterCells -> inCenter
            excludeCenter -> !inCenter
            else -> true
        }
        if (shouldDraw) {
            drawColoredCell(col * cellSize, row * cellSize, cellSize, playerColor)
        }
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
private fun DrawScope.drawCenterFinish(cellSize: Float, reusablePath: Path) {
    // Center area boundaries (in pixels)
    val left = HOME_SIZE_CELLS * cellSize
    val top = HOME_SIZE_CELLS * cellSize
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
        LudoBoardColors.getColor(LudoColor.GREEN),
        reusablePath
    )

    // YELLOW: top triangle (apex at center, base on top edge)
    drawTriangle(
        centerX, centerY,
        left, top,
        right, top,
        LudoBoardColors.getColor(LudoColor.YELLOW),
        reusablePath
    )

    // BLUE: right triangle (apex at center, base on right edge)
    drawTriangle(
        centerX, centerY,
        right, top,
        right, bottom,
        LudoBoardColors.getColor(LudoColor.BLUE),
        reusablePath
    )

    // RED: bottom triangle (apex at center, base on bottom edge)
    drawTriangle(
        centerX, centerY,
        left, bottom,
        right, bottom,
        LudoBoardColors.getColor(LudoColor.RED),
        reusablePath
    )
}

/**
 * Draw a triangle with given apex and two base points.
 */
private fun DrawScope.drawTriangle(
    apexX: Float, apexY: Float,
    baseX1: Float, baseY1: Float,
    baseX2: Float, baseY2: Float,
    color: Color,
    reusablePath: Path
) {
    reusablePath.reset()
    reusablePath.moveTo(apexX, apexY)
    reusablePath.lineTo(baseX1, baseY1)
    reusablePath.lineTo(baseX2, baseY2)
    reusablePath.close()

    drawPath(path = reusablePath, color = color, style = Fill)
    drawPath(path = reusablePath, color = LudoBoardColors.TrackBorder, style = Stroke(width = 2f))
}

/**
 * Draw safe cells (star positions) and start cells (arrows) on the track.
 */
private fun DrawScope.drawSafeCells(cellSize: Float, reusablePath: Path) {
    // Start cells (colored with arrow/star)
    LudoColor.entries.forEach { color ->
        val startCell = LudoBoard.START_CELL_BY_COLOR[color] ?: return@forEach
        val (row, col) = startCell
        drawStartingCell(col * cellSize, row * cellSize, cellSize, LudoBoardColors.getColor(color), reusablePath)
    }

    // Star positions derived from the canonical SAFE_CELLS, excluding start cells.
    // This ensures stars always match the game logic path definition.
    val startCells = LudoBoard.START_CELL_BY_COLOR.values.toSet()
    val starPositions = LudoBoard.SAFE_CELLS.filter { it !in startCells }

    starPositions.forEach { (row, col) ->
        // Guard: verify star position is on visible track (not in any home area)
        val isOnTrack = isPositionOnTrack(row, col)
        if (isOnTrack) {
            drawSafeStarCell(col * cellSize, row * cellSize, cellSize, reusablePath)
        }
    }
}

/**
 * Check if a position is on the visible track (cross-shaped arms).
 * Returns false if position is in a home area or outside the board.
 */
private fun isPositionOnTrack(row: Int, col: Int): Boolean {
    // Track arms:
    // - Top arm: rows 0-5, cols 6-8
    // - Bottom arm: rows 9-14, cols 6-8
    // - Left arm: rows 6-8, cols 0-5
    // - Right arm: rows 6-8, cols 9-14
    // - Center: rows 6-8, cols 6-8

    val inTopArm = row in 0..5 && col in 6..8
    val inBottomArm = row in 9..14 && col in 6..8
    val inLeftArm = row in 6..8 && col in 0..5
    val inRightArm = row in 6..8 && col in 9..14
    val inCenter = row in 6..8 && col in 6..8

    return inTopArm || inBottomArm || inLeftArm || inRightArm || inCenter
}

/**
 * Draw a starting cell with star indicator.
 */
private fun DrawScope.drawStartingCell(x: Float, y: Float, cellSize: Float, color: Color, reusablePath: Path) {
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize)
    )

    // Draw small star indicator
    val centerX = x + cellSize / 2
    val centerY = y + cellSize / 2
    val starRadius = cellSize * 0.3f

    drawStar(centerX, centerY, starRadius, Color.White, reusablePath)

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
private fun DrawScope.drawSafeStarCell(x: Float, y: Float, cellSize: Float, reusablePath: Path) {
    val centerX = x + cellSize / 2
    val centerY = y + cellSize / 2
    val starRadius = cellSize * 0.35f

    drawStar(centerX, centerY, starRadius, LudoBoardColors.SafeCell, reusablePath)
}

/**
 * Draw a 5-pointed star.
 */
private fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color, reusablePath: Path) {
    reusablePath.reset()
    val outerRadius = radius
    val innerRadius = radius * 0.5f
    val points = 5

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = Math.PI / 2 - (i * Math.PI / points)
        val x = cx + (r * kotlin.math.cos(angle)).toFloat()
        val y = cy - (r * kotlin.math.sin(angle)).toFloat()

        if (i == 0) {
            reusablePath.moveTo(x, y)
        } else {
            reusablePath.lineTo(x, y)
        }
    }
    reusablePath.close()

    drawPath(path = reusablePath, color = color, style = Fill)
    drawPath(path = reusablePath, color = LudoBoardColors.TrackBorder, style = Stroke(width = 1f))
}

/**
 * Draw grid lines for the track areas.
 */
private fun DrawScope.drawTrackGridLines(cellSize: Float) {
    val boardSize = BOARD_GRID_SIZE * cellSize
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
     *
     * IMPORTANT: Lane positions (52-57) are COLOR-SPECIFIC.
     * Each color has its own distinct lane cells leading to the center.
     * The color parameter MUST match the token's owning player's color.
     *
     * Position ranges:
     * - -1: HOME (not handled here, use getHomeBasePositions)
     * - 0-51: Ring/track (shared, uses absolute position)
     * - 52-56: Lane (color-specific, 5 cells before finish)
     * - 57: FINISHED (last lane cell = finish, use getFinishPosition for rendering)
     */
    fun getGridPosition(relativePosition: Int, color: LudoColor): BoardPosition? {
        if (relativePosition < 0) return null
        if (relativePosition >= LudoBoard.FINISH_POSITION) return null

        // Lane positions (52-57) - MUST use the token's color for correct lane
        if (relativePosition > LudoBoard.RING_END) {
            val laneIndex = relativePosition - LudoBoard.LANE_START

            // DEFENSIVE: Verify lane index is valid (0-5)
            if (laneIndex !in 0..5) {
                Log.e("LudoBoardPositions", "Invalid lane index $laneIndex for position $relativePosition")
                return null
            }

            // Get the lane cell for THIS color (not any other color)
            val cell = LudoBoard.getLaneCell(color, laneIndex)
            if (cell == null) {
                Log.e("LudoBoardPositions", "No lane cell for $color at index $laneIndex")
                return null
            }

            // DEFENSIVE: Verify this cell is NOT in another color's lane
            for (otherColor in LudoColor.entries) {
                if (otherColor != color) {
                    val otherLaneCells = LudoBoard.LANE_CELLS_BY_COLOR[otherColor] ?: continue
                    if (cell in otherLaneCells) {
                        // This should NEVER happen - lanes should be disjoint
                        val errorMsg = "$color lane cell $cell " +
                            "at index $laneIndex is also in ${otherColor}'s lane! " +
                            "This is a board configuration error."
                        Log.e("LudoBoardPositions", errorMsg)
                        throw IllegalStateException(errorMsg)
                    }
                }
            }

            return BoardPosition(cell.second, cell.first)
        }

        // Ring positions (0-51) - use absolute position for rendering
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
     * Get the center position of a home slot using exact fractions of the home square.
     *
     * Returns (x, y) in cellSize units (multiply by cellSize to get dp or px).
     * Uses 25%/75% of the 6-cell home square for a stable 2x2 layout:
     *   slot 0 = top-left, slot 1 = top-right,
     *   slot 2 = bottom-left, slot 3 = bottom-right.
     *
     * Same math for all four colors — only the home-square origin differs.
     */
    fun getHomeSlotOffset(color: LudoColor, slotIndex: Int): Pair<Float, Float> {
        require(slotIndex in 0..3) { "slotIndex must be 0..3" }

        // Home-square origin on the board grid (in cell units)
        val (originCol, originRow) = when (color) {
            LudoColor.GREEN  -> 0f to 0f
            LudoColor.YELLOW -> 9f to 0f
            LudoColor.RED    -> 0f to 9f
            LudoColor.BLUE   -> 9f to 9f
        }

        val homeSize = HOME_SIZE_CELLS.toFloat()

        // 2×2 slot grid inside the home square
        val col = slotIndex % 2
        val row = slotIndex / 2
        val xFraction = if (col == 0) 0.25f else 0.75f
        val yFraction = if (row == 0) 0.25f else 0.75f

        val x = originCol + homeSize * xFraction
        val y = originRow + homeSize * yFraction
        return x to y
    }

    /**
     * Get the finish position for a color (centroid of that color's triangle).
     * Each color's finished tokens go to their own triangle area in the center.
     */
    fun getFinishPosition(color: LudoColor): BoardPosition {
        return when (color) {
            LudoColor.RED -> BoardPosition(7, 8)    // Bottom triangle
            LudoColor.GREEN -> BoardPosition(6, 7)  // Left triangle
            LudoColor.YELLOW -> BoardPosition(7, 6) // Top triangle
            LudoColor.BLUE -> BoardPosition(8, 7)   // Right triangle
        }
    }

    /**
     * Get the center finish position (color-unaware fallback).
     */
    fun getFinishPosition(): BoardPosition {
        return BoardPosition(LudoBoard.CENTER_CELL.second, LudoBoard.CENTER_CELL.first)
    }
}
