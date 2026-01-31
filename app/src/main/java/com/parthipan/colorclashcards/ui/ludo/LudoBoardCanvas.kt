package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.parthipan.colorclashcards.game.ludo.model.LudoColor

/**
 * Composable that draws the Ludo board using Canvas.
 * The board is a 15x15 grid with:
 * - 4 home bases (6x6) in corners
 * - Cross-shaped track in the middle
 * - Home stretches (colored paths to center)
 * - Center finish area
 */
@Composable
fun LudoBoardCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val boardSize = size.minDimension
        val cellSize = boardSize / 15f

        // Draw board background
        drawRect(
            color = LudoBoardColors.BoardBackground,
            size = Size(boardSize, boardSize)
        )

        // Draw home bases (corners)
        drawHomeBase(LudoColor.RED, 0f, 0f, cellSize)
        drawHomeBase(LudoColor.BLUE, 9 * cellSize, 0f, cellSize)
        drawHomeBase(LudoColor.YELLOW, 0f, 9 * cellSize, cellSize)
        drawHomeBase(LudoColor.GREEN, 9 * cellSize, 9 * cellSize, cellSize)

        // Draw the track (cross-shaped path)
        drawTrack(cellSize)

        // Draw home stretches
        drawHomeStretch(LudoColor.RED, cellSize)
        drawHomeStretch(LudoColor.BLUE, cellSize)
        drawHomeStretch(LudoColor.YELLOW, cellSize)
        drawHomeStretch(LudoColor.GREEN, cellSize)

        // Draw center finish area
        drawCenterFinish(cellSize)

        // Draw safe cells (stars)
        drawSafeCells(cellSize)

        // Draw grid lines for the track
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

    // Draw token circles (home positions)
    val tokenRadius = cellSize * 0.5f
    val positions = listOf(
        Offset(x + cellSize * 1.5f, y + cellSize * 1.5f),
        Offset(x + cellSize * 4.5f, y + cellSize * 1.5f),
        Offset(x + cellSize * 1.5f, y + cellSize * 4.5f),
        Offset(x + cellSize * 4.5f, y + cellSize * 4.5f)
    )

    positions.forEach { pos ->
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
    // Top vertical track (columns 6-8, rows 0-5)
    for (row in 0 until 6) {
        for (col in 6 until 9) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Left horizontal track (columns 0-5, rows 6-8)
    for (row in 6 until 9) {
        for (col in 0 until 6) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Right horizontal track (columns 9-14, rows 6-8)
    for (row in 6 until 9) {
        for (col in 9 until 15) {
            drawTrackCell(col * cellSize, row * cellSize, cellSize)
        }
    }

    // Bottom vertical track (columns 6-8, rows 9-14)
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
 * Draw the colored home stretch for a player.
 */
private fun DrawScope.drawHomeStretch(color: LudoColor, cellSize: Float) {
    val playerColor = LudoBoardColors.getColor(color)

    when (color) {
        LudoColor.RED -> {
            // Red home stretch: column 7, rows 1-6 (6 cells going down toward center)
            for (row in 1..6) {
                drawColoredCell(7 * cellSize, row * cellSize, cellSize, playerColor)
            }
        }
        LudoColor.BLUE -> {
            // Blue home stretch: row 7, columns 8-13 (6 cells going left toward center)
            for (col in 8..13) {
                drawColoredCell(col * cellSize, 7 * cellSize, cellSize, playerColor)
            }
        }
        LudoColor.YELLOW -> {
            // Yellow home stretch: row 7, columns 1-6 (6 cells going right toward center)
            for (col in 1..6) {
                drawColoredCell(col * cellSize, 7 * cellSize, cellSize, playerColor)
            }
        }
        LudoColor.GREEN -> {
            // Green home stretch: column 7, rows 8-13 (6 cells going up toward center)
            for (row in 8..13) {
                drawColoredCell(7 * cellSize, row * cellSize, cellSize, playerColor)
            }
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
 */
private fun DrawScope.drawCenterFinish(cellSize: Float) {
    val centerX = 7.5f * cellSize
    val centerY = 7.5f * cellSize

    // Draw four triangles pointing to center
    val triangles = listOf(
        Triple(LudoColor.RED, Offset(6 * cellSize, 6 * cellSize), Offset(9 * cellSize, 6 * cellSize)),
        Triple(LudoColor.BLUE, Offset(9 * cellSize, 6 * cellSize), Offset(9 * cellSize, 9 * cellSize)),
        Triple(LudoColor.GREEN, Offset(9 * cellSize, 9 * cellSize), Offset(6 * cellSize, 9 * cellSize)),
        Triple(LudoColor.YELLOW, Offset(6 * cellSize, 9 * cellSize), Offset(6 * cellSize, 6 * cellSize))
    )

    triangles.forEach { (color, point1, point2) ->
        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(point1.x, point1.y)
            lineTo(point2.x, point2.y)
            close()
        }

        drawPath(
            path = path,
            color = LudoBoardColors.getColor(color),
            style = Fill
        )

        drawPath(
            path = path,
            color = LudoBoardColors.TrackBorder,
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Draw safe cells (star positions) on the track.
 */
private fun DrawScope.drawSafeCells(cellSize: Float) {
    // Safe cells are at specific absolute positions
    // We'll mark them with stars
    val safeCellPositions = listOf(
        // Starting positions (colored)
        Pair(6, 1) to LudoColor.RED,      // Red start
        Pair(13, 6) to LudoColor.BLUE,    // Blue start
        Pair(8, 13) to LudoColor.GREEN,   // Green start
        Pair(1, 8) to LudoColor.YELLOW    // Yellow start
    )

    // Additional safe cells (gray stars) - matching TRACK_POSITIONS
    val graySafeCells = listOf(
        Pair(8, 5),   // Position 8 - after Red area
        Pair(9, 8),   // Position 21 - after Blue area
        Pair(6, 9),   // Position 34 - after Green area
        Pair(5, 6)    // Position 47 - after Yellow area
    )

    // Draw colored starting positions
    safeCellPositions.forEach { (pos, color) ->
        val (col, row) = pos
        drawStartingCell(col * cellSize, row * cellSize, cellSize, LudoBoardColors.getColor(color))
    }

    // Draw gray safe cells with star pattern
    graySafeCells.forEach { (col, row) ->
        drawSafeStarCell(col * cellSize, row * cellSize, cellSize)
    }
}

/**
 * Draw a starting cell with arrow.
 */
private fun DrawScope.drawStartingCell(x: Float, y: Float, cellSize: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize)
    )

    // Draw small star/arrow indicator
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
    // Border around the entire board
    val boardSize = 15 * cellSize
    drawRect(
        color = LudoBoardColors.TrackBorder,
        topLeft = Offset(0f, 0f),
        size = Size(boardSize, boardSize),
        style = Stroke(width = 3f)
    )
}

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
     * Returns null for HOME position (tokens in home base) or FINISHED position.
     *
     * @param relativePosition Token's relative position (0-56)
     * @param color Token's player color
     * @return BoardPosition for rendering, or null
     */
    fun getGridPosition(relativePosition: Int, color: LudoColor): BoardPosition? {
        // Home positions are handled separately
        if (relativePosition < 0) return null

        // Finished position
        if (relativePosition >= 57) return null

        // Home stretch positions (51-56)
        if (relativePosition > 50) {
            return getHomeStretchPosition(relativePosition - 51, color)
        }

        // Main track positions (0-50)
        return getMainTrackPosition(relativePosition, color)
    }

    /**
     * Get home stretch position (6 cells leading to center finish).
     *
     * Home stretches match the visual drawing:
     * - RED: column 7, rows 1-6 (enters from top, goes down toward center)
     * - BLUE: row 7, columns 13-8 (enters from right, goes left toward center)
     * - GREEN: column 7, rows 13-8 (enters from bottom, goes up toward center)
     * - YELLOW: row 7, columns 1-6 (enters from left, goes right toward center)
     *
     * @param index Home stretch position 0-5 (0 = entry, 5 = closest to center)
     */
    private fun getHomeStretchPosition(index: Int, color: LudoColor): BoardPosition {
        return when (color) {
            // RED: enters at (7,1), goes DOWN towards center
            // index 0-5 → rows 1,2,3,4,5,6
            LudoColor.RED -> BoardPosition(7, 1 + index)

            // BLUE: enters at (13,7), goes LEFT towards center
            // index 0-5 → columns 13,12,11,10,9,8
            LudoColor.BLUE -> BoardPosition(13 - index, 7)

            // GREEN: enters at (7,13), goes UP towards center
            // index 0-5 → rows 13,12,11,10,9,8
            LudoColor.GREEN -> BoardPosition(7, 13 - index)

            // YELLOW: enters at (1,7), goes RIGHT towards center
            // index 0-5 → columns 1,2,3,4,5,6
            LudoColor.YELLOW -> BoardPosition(1 + index, 7)
        }
    }

    /**
     * Get main track position. The track is 52 cells going clockwise.
     * Position 0 for RED is absolute position 0.
     */
    private fun getMainTrackPosition(relativePosition: Int, color: LudoColor): BoardPosition {
        // Convert relative position to absolute (0-51)
        val startOffset = when (color) {
            LudoColor.RED -> 0
            LudoColor.BLUE -> 13
            LudoColor.GREEN -> 26
            LudoColor.YELLOW -> 39
        }

        val absolutePosition = (relativePosition + startOffset) % 52

        return absolutePositionToGrid(absolutePosition)
    }

    /**
     * Convert absolute track position (0-51) to grid coordinates.
     * Track goes clockwise starting from red's starting position.
     */
    private fun absolutePositionToGrid(position: Int): BoardPosition {
        return TRACK_POSITIONS.getOrElse(position) { BoardPosition(7, 7) }
    }

    /**
     * Complete track positions for 52 cells going clockwise around the cross-shaped track.
     *
     * Standard Ludo board layout (15x15 grid):
     * - RED home: top-left (rows 0-5, cols 0-5)
     * - BLUE home: top-right (rows 0-5, cols 9-14)
     * - GREEN home: bottom-right (rows 9-14, cols 9-14)
     * - YELLOW home: bottom-left (rows 9-14, cols 0-5)
     *
     * Track moves CLOCKWISE: RED (0) → BLUE (13) → GREEN (26) → YELLOW (39) → RED
     * Each section is 13 positions (52 total / 4 players).
     */
    private val TRACK_POSITIONS = listOf(
        // Section 1: RED (0-12) - exits going UP column 6, then RIGHT along row 0 towards BLUE
        BoardPosition(6, 1),   // 0 - Red start (exit from Red home)
        BoardPosition(6, 0),   // 1 - going up
        BoardPosition(7, 0),   // 2 - turn right along top
        BoardPosition(8, 0),   // 3
        BoardPosition(8, 1),   // 4 - turn down
        BoardPosition(8, 2),   // 5
        BoardPosition(8, 3),   // 6
        BoardPosition(8, 4),   // 7
        BoardPosition(8, 5),   // 8 - safe cell (star)
        BoardPosition(9, 6),   // 9 - turn right into horizontal arm
        BoardPosition(10, 6),  // 10
        BoardPosition(11, 6),  // 11
        BoardPosition(12, 6),  // 12

        // Section 2: BLUE (13-25) - exits going RIGHT along row 6, then DOWN towards GREEN
        BoardPosition(13, 6),  // 13 - Blue start (exit from Blue home)
        BoardPosition(14, 6),  // 14
        BoardPosition(14, 7),  // 15 - turn down
        BoardPosition(14, 8),  // 16 - turn left
        BoardPosition(13, 8),  // 17
        BoardPosition(12, 8),  // 18
        BoardPosition(11, 8),  // 19
        BoardPosition(10, 8),  // 20
        BoardPosition(9, 8),   // 21 - safe cell (star)
        BoardPosition(8, 9),   // 22 - turn down into vertical arm
        BoardPosition(8, 10),  // 23
        BoardPosition(8, 11),  // 24
        BoardPosition(8, 12),  // 25

        // Section 3: GREEN (26-38) - exits going DOWN column 8, then LEFT towards YELLOW
        BoardPosition(8, 13),  // 26 - Green start (exit from Green home)
        BoardPosition(8, 14),  // 27
        BoardPosition(7, 14),  // 28 - turn left
        BoardPosition(6, 14),  // 29 - turn up
        BoardPosition(6, 13),  // 30
        BoardPosition(6, 12),  // 31
        BoardPosition(6, 11),  // 32
        BoardPosition(6, 10),  // 33
        BoardPosition(6, 9),   // 34 - safe cell (star)
        BoardPosition(5, 8),   // 35 - turn left into horizontal arm
        BoardPosition(4, 8),   // 36
        BoardPosition(3, 8),   // 37
        BoardPosition(2, 8),   // 38

        // Section 4: YELLOW (39-51) - exits going LEFT along row 8, then UP towards RED
        BoardPosition(1, 8),   // 39 - Yellow start (exit from Yellow home)
        BoardPosition(0, 8),   // 40
        BoardPosition(0, 7),   // 41 - turn up
        BoardPosition(0, 6),   // 42 - turn right
        BoardPosition(1, 6),   // 43
        BoardPosition(2, 6),   // 44
        BoardPosition(3, 6),   // 45
        BoardPosition(4, 6),   // 46
        BoardPosition(5, 6),   // 47 - safe cell (star)
        BoardPosition(6, 5),   // 48 - turn up into vertical arm
        BoardPosition(6, 4),   // 49
        BoardPosition(6, 3),   // 50
        BoardPosition(6, 2)    // 51 - last cell before Red enters home stretch
    )

    /**
     * Get the home base token positions for a color.
     * Returns 4 positions for the 4 tokens when they're at home.
     */
    fun getHomeBasePositions(color: LudoColor): List<BoardPosition> {
        return when (color) {
            LudoColor.RED -> listOf(
                BoardPosition(1, 1),
                BoardPosition(4, 1),
                BoardPosition(1, 4),
                BoardPosition(4, 4)
            )
            LudoColor.BLUE -> listOf(
                BoardPosition(10, 1),
                BoardPosition(13, 1),
                BoardPosition(10, 4),
                BoardPosition(13, 4)
            )
            LudoColor.YELLOW -> listOf(
                BoardPosition(1, 10),
                BoardPosition(4, 10),
                BoardPosition(1, 13),
                BoardPosition(4, 13)
            )
            LudoColor.GREEN -> listOf(
                BoardPosition(10, 10),
                BoardPosition(13, 10),
                BoardPosition(10, 13),
                BoardPosition(13, 13)
            )
        }
    }

    /**
     * Get the center finish position (all tokens go to the same visual center).
     */
    fun getFinishPosition(): BoardPosition {
        return BoardPosition(7, 7)
    }
}
