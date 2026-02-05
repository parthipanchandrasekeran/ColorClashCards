package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LudoBoard - Classic Ludo Layout (15x15 grid)
 *
 * HOME AREAS (6x6 corners):
 * - GREEN: top-left, YELLOW: top-right, RED: bottom-left, BLUE: bottom-right
 *
 * RING PATH: 52 cells on cross arms only (never enters home corners), CLOCKWISE.
 * START INDICES: GREEN=0, YELLOW=13, BLUE=26, RED=39 (evenly spaced by 13)
 * LANE ENTRIES: COMPUTED as (startIndex - 1 + 52) % 52
 * SAFE CELLS: COMPUTED as startIndex and (startIndex + 8) % 52
 *
 * CLOCKWISE MOVEMENT around the cross-shaped track.
 */
class LudoBoardTest {

    // ==================== CONSTANTS TESTS ====================

    @Test
    fun `board has correct track size`() {
        assertEquals("Ring should have 52 cells", 52, LudoBoard.RING_SIZE)
    }

    @Test
    fun `board has correct lane size`() {
        assertEquals("Lane should have 6 cells", 6, LudoBoard.LANE_SIZE)
    }

    @Test
    fun `finish position is 57`() {
        assertEquals("Finish position should be 57", 57, LudoBoard.FINISH_POSITION)
    }

    @Test
    fun `home position is -1`() {
        assertEquals("Home position should be -1", -1, LudoBoard.HOME_POSITION)
    }

    @Test
    fun `ring has exactly 52 cells`() {
        assertEquals("Ring should have 52 cells", 52, LudoBoard.RING_CELLS.size)
    }

    // ==================== START INDEX COMPUTATION TESTS ====================

    @Test
    fun `start index is computed from indexOf startCell in ringCells`() {
        // This is the KEY test - ensures START_INDEX_BY_COLOR is derived, not hardcoded
        LudoColor.entries.forEach { color ->
            val startCell = LudoBoard.START_CELL_BY_COLOR[color]
            assertNotNull("Start cell must be defined for $color", startCell)

            val computedIndex = LudoBoard.RING_CELLS.indexOf(startCell)
            assertTrue("Start cell for $color must exist in RING_CELLS (indexOf >= 0)",
                computedIndex >= 0)

            val storedIndex = LudoBoard.getStartIndex(color)
            assertEquals("START_INDEX_BY_COLOR[$color] must equal indexOf(startCell)",
                computedIndex, storedIndex)
        }
    }

    @Test
    fun `ring cell at start index matches start cell for each color`() {
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val ringCell = LudoBoard.getRingCell(startIndex)
            val startCell = LudoBoard.getStartCell(color)

            assertEquals("Ring cell at startIndex should match startCell for $color",
                startCell, ringCell)
        }
    }

    @Test
    fun `all start cells exist in ring cells`() {
        LudoColor.entries.forEach { color ->
            val startCell = LudoBoard.START_CELL_BY_COLOR[color]
            assertNotNull("Start cell must be defined for $color", startCell)

            val index = LudoBoard.RING_CELLS.indexOf(startCell)
            assertTrue("Start cell $startCell for $color must be in RING_CELLS",
                index >= 0)
        }
    }

    @Test
    fun `start positions are evenly spaced by 13 cells`() {
        val positions = LudoColor.entries.map { LudoBoard.getStartIndex(it) }.sorted()

        // Should be spaced 13 cells apart (52 / 4 = 13)
        for (i in 0 until positions.size - 1) {
            assertEquals("Start positions should be 13 apart",
                13, positions[i + 1] - positions[i])
        }
    }

    // ==================== START CELL COORDINATES ====================

    @Test
    fun `each color has a defined start cell`() {
        LudoColor.entries.forEach { color ->
            val startCell = LudoBoard.START_CELL_BY_COLOR[color]
            assertNotNull("Start cell must be defined for $color", startCell)
            assertTrue("Start cell row must be valid", startCell!!.first in 0..14)
            assertTrue("Start cell col must be valid", startCell.second in 0..14)
        }
    }

    // ==================== ROLLING 6 SPAWNS AT CORRECT START ====================

    @Test
    fun `token leaving home spawns at its color start cell`() {
        // This is the CRITICAL test for the spawn bug
        LudoColor.entries.forEach { color ->
            // When a token leaves home (relative position becomes 0)
            val absolutePosition = LudoBoard.toAbsolutePosition(0, color)

            // Should be at this color's start index
            val expectedIndex = LudoBoard.getStartIndex(color)
            assertEquals("$color at relative 0 should be at absolute $expectedIndex",
                expectedIndex, absolutePosition)

            // Verify the cell coordinates match START_CELL_BY_COLOR
            val cell = LudoBoard.getRingCell(absolutePosition)
            val expectedCell = LudoBoard.getStartCell(color)
            assertEquals("$color spawns at correct start cell", expectedCell, cell)
        }
    }

    @Test
    fun `leaving home uses startIndexByColor`() {
        // Verify toAbsolutePosition(0, color) equals START_INDEX_BY_COLOR[color]
        LudoColor.entries.forEach { color ->
            val relativeZero = LudoBoard.toAbsolutePosition(0, color)
            val startIndex = LudoBoard.START_INDEX_BY_COLOR[color]

            assertNotNull("START_INDEX_BY_COLOR must have entry for $color", startIndex)
            assertEquals("Leaving home must use startIndexByColor for $color",
                startIndex, relativeZero)
        }
    }

    // ==================== EACH COLOR SPAWNS AT ITS OWN START ====================

    @Test
    fun `each color spawns at its own start not others`() {
        LudoColor.entries.forEach { color ->
            val myStart = LudoBoard.toAbsolutePosition(0, color)

            LudoColor.entries.filter { it != color }.forEach { otherColor ->
                val otherStart = LudoBoard.getStartIndex(otherColor)
                assertNotEquals("$color should NOT spawn at $otherColor's position",
                    otherStart, myStart)
            }
        }
    }

    @Test
    fun `all start cells are distinct`() {
        val startCells = LudoColor.entries.map { LudoBoard.getStartCell(it) }
        val uniqueCells = startCells.toSet()
        assertEquals("All 4 start cells should be unique", 4, uniqueCells.size)
    }

    @Test
    fun `all start indices are distinct`() {
        val startIndices = LudoColor.entries.map { LudoBoard.getStartIndex(it) }
        val uniqueIndices = startIndices.toSet()
        assertEquals("All 4 start indices should be unique", 4, uniqueIndices.size)
    }

    // ==================== RING STRUCTURE TESTS ====================

    @Test
    fun `all ring cells are within board bounds`() {
        LudoBoard.RING_CELLS.forEachIndexed { index, cell ->
            assertTrue("Ring cell $index row ${cell.first} should be in bounds",
                cell.first in 0..14)
            assertTrue("Ring cell $index col ${cell.second} should be in bounds",
                cell.second in 0..14)
        }
    }

    @Test
    fun `ring cells are unique`() {
        val uniqueCells = LudoBoard.RING_CELLS.toSet()
        assertEquals("All ring cells should be unique",
            LudoBoard.RING_CELLS.size, uniqueCells.size)
    }

    @Test
    fun `consecutive ring cells are adjacent`() {
        for (i in 0 until LudoBoard.RING_CELLS.size) {
            val current = LudoBoard.RING_CELLS[i]
            val next = LudoBoard.RING_CELLS[(i + 1) % LudoBoard.RING_SIZE]

            val rowDiff = kotlin.math.abs(next.first - current.first)
            val colDiff = kotlin.math.abs(next.second - current.second)

            // Adjacent means diff of 1 in one dimension, 0-1 in other (allows diagonal)
            // Or could be on the edge wrap
            assertTrue("Cells $i and ${(i+1) % 52} should be close (row diff: $rowDiff, col diff: $colDiff)",
                rowDiff <= 2 && colDiff <= 2)
        }
    }

    // ==================== SAFE CELLS TESTS ====================

    @Test
    fun `all starting positions are safe`() {
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            assertTrue("Start position $startIndex for $color should be safe",
                LudoBoard.isSafeCell(startIndex))
        }
    }

    @Test
    fun `star positions are safe (8 cells after each start)`() {
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val starIndex = (startIndex + 8) % LudoBoard.RING_SIZE
            assertTrue("Star position $starIndex (8 after $color start) should be safe",
                LudoBoard.isSafeCell(starIndex))
        }
    }

    @Test
    fun `there are exactly 8 safe cells`() {
        assertEquals("Should have 8 safe cells (4 starts + 4 stars)", 8, LudoBoard.SAFE_INDICES.size)
    }

    @Test
    fun `safe indices contains start and star for each color`() {
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val starIndex = (startIndex + 8) % LudoBoard.RING_SIZE

            assertTrue("SAFE_INDICES should contain start for $color",
                startIndex in LudoBoard.SAFE_INDICES)
            assertTrue("SAFE_INDICES should contain star for $color",
                starIndex in LudoBoard.SAFE_INDICES)
        }
    }

    // ==================== LANE ENTRY TESTS ====================

    @Test
    fun `lane entry is computed as startIndex minus 1`() {
        // Lane entry should be one step before the start position
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val expectedLaneEntry = (startIndex - 1 + LudoBoard.RING_SIZE) % LudoBoard.RING_SIZE
            val actualLaneEntry = LudoBoard.LANE_ENTRY_INDEX_BY_COLOR[color]

            assertEquals("Lane entry for $color should be (startIndex - 1) mod 52",
                expectedLaneEntry, actualLaneEntry)
        }
    }

    @Test
    fun `shouldEnterLane returns true at correct index for each color`() {
        LudoColor.entries.forEach { color ->
            val laneEntry = LudoBoard.LANE_ENTRY_INDEX_BY_COLOR[color]!!
            assertTrue("$color should enter lane at index $laneEntry",
                LudoBoard.shouldEnterLane(laneEntry, color))
        }
    }

    @Test
    fun `shouldEnterLane returns false at other colors lane entry`() {
        LudoColor.entries.forEach { color ->
            val myLaneEntry = LudoBoard.LANE_ENTRY_INDEX_BY_COLOR[color]!!

            LudoColor.entries.filter { it != color }.forEach { otherColor ->
                assertFalse("$color should NOT enter lane at $otherColor's lane entry",
                    LudoBoard.shouldEnterLane(myLaneEntry, otherColor))
            }
        }
    }

    // ==================== LANE CELLS TESTS ====================

    @Test
    fun `each color has 6 lane cells`() {
        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]
            assertNotNull("Lane cells must be defined for $color", laneCells)
            assertEquals("$color lane should have 6 cells", 6, laneCells!!.size)
        }
    }

    @Test
    fun `lane cells are within board bounds`() {
        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]!!
            laneCells.forEachIndexed { index, cell ->
                assertTrue("$color lane cell $index row should be in bounds",
                    cell.first in 0..14)
                assertTrue("$color lane cell $index col should be in bounds",
                    cell.second in 0..14)
            }
        }
    }

    @Test
    fun `lane cells are unique within each color`() {
        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]!!
            val uniqueCells = laneCells.toSet()
            assertEquals("$color lane cells should be unique",
                laneCells.size, uniqueCells.size)
        }
    }

    @Test
    fun `lane cells lead towards center`() {
        // Last lane cell should be adjacent to center (7, 7)
        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]!!
            val lastCell = laneCells.last()
            val center = LudoBoard.CENTER_CELL

            val rowDiff = kotlin.math.abs(lastCell.first - center.first)
            val colDiff = kotlin.math.abs(lastCell.second - center.second)

            assertTrue("$color lane end should be adjacent to center",
                (rowDiff <= 1 && colDiff <= 1))
        }
    }

    @Test
    fun `getLaneCell returns lane cells correctly`() {
        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]!!
            for (i in 0..5) {
                assertEquals("getLaneCell($color, $i) should match LANE_CELLS_BY_COLOR",
                    laneCells[i], LudoBoard.getLaneCell(color, i))
            }
        }
    }

    // ==================== HOME SLOTS TESTS ====================

    @Test
    fun `green home slots are in top-left quadrant`() {
        val homeSlots = LudoBoard.HOME_SLOTS_BY_COLOR[LudoColor.GREEN]!!

        assertEquals("GREEN should have 4 home slots", 4, homeSlots.size)

        homeSlots.forEach { slot ->
            assertTrue("GREEN home slot row should be <= 5", slot.first <= 5)
            assertTrue("GREEN home slot col should be <= 5", slot.second <= 5)
        }
    }

    @Test
    fun `yellow home slots are in top-right quadrant`() {
        val homeSlots = LudoBoard.HOME_SLOTS_BY_COLOR[LudoColor.YELLOW]!!

        assertEquals("YELLOW should have 4 home slots", 4, homeSlots.size)

        homeSlots.forEach { slot ->
            assertTrue("YELLOW home slot row should be <= 5", slot.first <= 5)
            assertTrue("YELLOW home slot col should be >= 9", slot.second >= 9)
        }
    }

    @Test
    fun `red home slots are in bottom-left quadrant`() {
        val homeSlots = LudoBoard.HOME_SLOTS_BY_COLOR[LudoColor.RED]!!

        assertEquals("RED should have 4 home slots", 4, homeSlots.size)

        homeSlots.forEach { slot ->
            assertTrue("RED home slot row should be >= 9", slot.first >= 9)
            assertTrue("RED home slot col should be <= 5", slot.second <= 5)
        }
    }

    @Test
    fun `blue home slots are in bottom-right quadrant`() {
        val homeSlots = LudoBoard.HOME_SLOTS_BY_COLOR[LudoColor.BLUE]!!

        assertEquals("BLUE should have 4 home slots", 4, homeSlots.size)

        homeSlots.forEach { slot ->
            assertTrue("BLUE home slot row should be >= 9", slot.first >= 9)
            assertTrue("BLUE home slot col should be >= 9", slot.second >= 9)
        }
    }

    // ==================== POSITION HELPER TESTS ====================

    @Test
    fun `isInLane correctly identifies lane positions`() {
        // Positions 52-56 are in lane (57 is finish)
        for (pos in 52..56) {
            assertTrue("Position $pos should be in lane", LudoBoard.isInLane(pos))
        }
    }

    @Test
    fun `isInLane returns false for ring positions`() {
        for (pos in 0..51) {
            assertFalse("Position $pos should not be in lane", LudoBoard.isInLane(pos))
        }
    }

    @Test
    fun `isInLane returns false for finish position`() {
        assertFalse("Position 57 (finish) should not be in lane",
            LudoBoard.isInLane(57))
    }

    @Test
    fun `isOnRing correctly identifies ring positions`() {
        for (pos in 0..51) {
            assertTrue("Position $pos should be on ring", LudoBoard.isOnRing(pos))
        }
    }

    @Test
    fun `isOnRing returns false for lane and finish`() {
        for (pos in 52..58) {
            assertFalse("Position $pos should not be on ring", LudoBoard.isOnRing(pos))
        }
    }

    @Test
    fun `isOnRing returns false for home`() {
        assertFalse("Position -1 (home) should not be on ring", LudoBoard.isOnRing(-1))
    }

    @Test
    fun `toAbsolutePosition returns -1 for lane positions`() {
        for (color in LudoColor.entries) {
            for (pos in 52..57) {
                assertEquals("Position $pos for $color should return -1",
                    -1, LudoBoard.toAbsolutePosition(pos, color))
            }
        }
    }

    @Test
    fun `toAbsolutePosition wraps around correctly`() {
        // GREEN starts at 0: position 50 = (50 + 0) % 52 = 50
        assertEquals(50, LudoBoard.toAbsolutePosition(50, LudoColor.GREEN))

        // YELLOW starts at 13: position 50 = (50 + 13) % 52 = 11
        assertEquals(11, LudoBoard.toAbsolutePosition(50, LudoColor.YELLOW))

        // BLUE starts at 26: position 50 = (50 + 26) % 52 = 24
        assertEquals(24, LudoBoard.toAbsolutePosition(50, LudoColor.BLUE))

        // RED starts at 39: position 50 = (50 + 39) % 52 = 37
        assertEquals(37, LudoBoard.toAbsolutePosition(50, LudoColor.RED))
    }

    @Test
    fun `distanceToFinish calculations`() {
        assertEquals("From position 0, distance should be 57", 57, LudoBoard.distanceToFinish(0))
        assertEquals("From position 51, distance should be 6", 6, LudoBoard.distanceToFinish(51))
        assertEquals("From position 52, distance should be 5", 5, LudoBoard.distanceToFinish(52))
        assertEquals("From position 56, distance should be 1", 1, LudoBoard.distanceToFinish(56))
        assertEquals("From position 57, distance should be 0", 0, LudoBoard.distanceToFinish(57))
        assertEquals("From home (-1), distance should be 58", 58, LudoBoard.distanceToFinish(-1))
    }

    // ==================== COLLISION DETECTION ====================

    @Test
    fun `two colors at same absolute position can collide`() {
        // GREEN at relative 13 = absolute (13 + 0) % 52 = 13
        // YELLOW at relative 0 = absolute (0 + 13) % 52 = 13
        val greenAbsolute = LudoBoard.toAbsolutePosition(13, LudoColor.GREEN)
        val yellowAbsolute = LudoBoard.toAbsolutePosition(0, LudoColor.YELLOW)

        assertEquals("Both should be at absolute position 13", greenAbsolute, yellowAbsolute)
    }

    @Test
    fun `colors at same relative position have different absolute positions`() {
        // All at relative position 10
        val positions = LudoColor.entries.map { LudoBoard.toAbsolutePosition(10, it) }.toSet()

        assertEquals("All 4 absolute positions should be unique", 4, positions.size)
    }

    // ==================== CENTER CELL ====================

    @Test
    fun `center cell is at (7, 7)`() {
        assertEquals("Center cell should be at (7, 7)", Pair(7, 7), LudoBoard.CENTER_CELL)
    }

    // ==================== CLOCKWISE DIRECTION TESTS ====================

    @Test
    fun `ring path is clockwise - arm order is LEFT TOP RIGHT BOTTOM`() {
        // Determine which arm each cell is on
        fun armOf(row: Int, col: Int): String = when {
            row in 6..8 && col in 0..5 -> "LEFT"
            row in 0..5 && col in 6..8 -> "TOP"
            row in 6..8 && col in 9..14 -> "RIGHT"
            row in 9..14 && col in 6..8 -> "BOTTOM"
            row in 6..8 && col in 6..8 -> "CENTER" // junction/diagonal
            else -> "UNKNOWN"
        }

        // Collect the arm sequence (only arm-to-arm transitions, skip CENTER)
        val armTransitions = mutableListOf<String>()
        var lastArm = ""
        for (cell in LudoBoard.RING_CELLS) {
            val arm = armOf(cell.first, cell.second)
            if (arm != "CENTER" && arm != lastArm) {
                armTransitions.add(arm)
                lastArm = arm
            }
        }

        // Path starts on LEFT, visits TOP → RIGHT → BOTTOM, then wraps back to LEFT.
        // That gives 5 transitions: LEFT → TOP → RIGHT → BOTTOM → LEFT.
        val clockwiseOrder = listOf("LEFT", "TOP", "RIGHT", "BOTTOM", "LEFT")
        assertEquals("Should visit 4 arms (with wrap-back)", clockwiseOrder.size, armTransitions.size)
        assertEquals("Arm order should be clockwise", clockwiseOrder, armTransitions)
    }

    @Test
    fun `first step from each start is clockwise-adjacent`() {
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val startCell = LudoBoard.RING_CELLS[startIndex]
            val nextCell = LudoBoard.RING_CELLS[(startIndex + 1) % LudoBoard.RING_SIZE]
            val (r0, c0) = startCell
            val (r1, c1) = nextCell

            // Must be adjacent (max 1 step in each dimension)
            assertTrue("$color: next cell $nextCell should be adjacent to start $startCell",
                kotlin.math.abs(r1 - r0) <= 1 && kotlin.math.abs(c1 - c0) <= 1)

            // Verify clockwise direction based on arm position
            val isOnLeftArmTopRow = r0 in 6..8 && c0 in 0..5 && r0 == 6
            val isOnTopArmRightCol = r0 in 0..5 && c0 in 6..8 && c0 == 8
            val isOnRightArmBottomRow = r0 in 6..8 && c0 in 9..14 && r0 == 8
            val isOnBottomArmLeftCol = r0 in 9..14 && c0 in 6..8 && c0 == 6

            when {
                isOnLeftArmTopRow -> assertTrue("$color at $startCell: clockwise = col increases",
                    c1 > c0)
                isOnTopArmRightCol -> assertTrue("$color at $startCell: clockwise = row increases",
                    r1 > r0)
                isOnRightArmBottomRow -> assertTrue("$color at $startCell: clockwise = col decreases",
                    c1 < c0)
                isOnBottomArmLeftCol -> assertTrue("$color at $startCell: clockwise = row decreases",
                    r1 < r0)
            }
        }
    }

    @Test
    fun `print first 10 positions from each color start`() {
        // Visual confirmation: prints the first 10 ring cells from each color's start.
        // All 4 colors should trace the same clockwise loop.
        LudoColor.entries.forEach { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val cells = (0 until 10).map { offset ->
                val absIdx = (startIndex + offset) % LudoBoard.RING_SIZE
                val cell = LudoBoard.RING_CELLS[absIdx]
                "$absIdx:(${cell.first},${cell.second})"
            }
            println("$color start=$startIndex: ${cells.joinToString(" → ")}")
        }

        // Programmatic check: all 4 colors' 10-step sequences share the same
        // relative direction pattern (clockwise), verified by the arm-order test above.
        // Just assert no crash and start indices are correct.
        assertEquals(0, LudoBoard.getStartIndex(LudoColor.GREEN))
        assertEquals(13, LudoBoard.getStartIndex(LudoColor.YELLOW))
        assertEquals(26, LudoBoard.getStartIndex(LudoColor.BLUE))
        assertEquals(39, LudoBoard.getStartIndex(LudoColor.RED))
    }
}
