package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LudoBoard utility class.
 * Tests board layout constants, safe cells, and position calculations.
 */
class LudoBoardTest {

    // ==================== CONSTANTS TESTS ====================

    @Test
    fun `board has correct track size`() {
        assertEquals("Main track should have 52 cells", 52, LudoBoard.MAIN_TRACK_SIZE)
    }

    @Test
    fun `board has correct home stretch size`() {
        assertEquals("Home stretch should have 6 cells", 6, LudoBoard.HOME_STRETCH_SIZE)
    }

    @Test
    fun `finish position is 57`() {
        assertEquals("Finish position should be 57", 57, LudoBoard.FINISH_POSITION)
    }

    @Test
    fun `home position is -1`() {
        assertEquals("Home position should be -1", -1, LudoBoard.HOME_POSITION)
    }

    // ==================== START POSITIONS TESTS ====================

    @Test
    fun `red starts at position 0`() {
        assertEquals(0, LudoBoard.getStartPosition(LudoColor.RED))
    }

    @Test
    fun `blue starts at position 13`() {
        assertEquals(13, LudoBoard.getStartPosition(LudoColor.BLUE))
    }

    @Test
    fun `yellow starts at position 39`() {
        // Yellow is at bottom-left, clockwise from Green (bottom-right)
        assertEquals(39, LudoBoard.getStartPosition(LudoColor.YELLOW))
    }

    @Test
    fun `green starts at position 26`() {
        // Green is at bottom-right, clockwise from Blue (top-right)
        assertEquals(26, LudoBoard.getStartPosition(LudoColor.GREEN))
    }

    @Test
    fun `start positions are evenly spaced`() {
        // Arrange
        val positions = LudoColor.entries.map { LudoBoard.getStartPosition(it) }.sorted()

        // Act & Assert
        // Should be spaced 13 cells apart (52 / 4 = 13)
        for (i in 0 until positions.size - 1) {
            assertEquals("Start positions should be 13 apart",
                13, positions[i + 1] - positions[i])
        }
    }

    // ==================== SAFE CELLS TESTS ====================

    @Test
    fun `all starting positions are safe`() {
        // All 4 starting positions should be safe
        assertTrue("Position 0 (Red start) should be safe", LudoBoard.isSafeCell(0))
        assertTrue("Position 13 (Blue start) should be safe", LudoBoard.isSafeCell(13))
        assertTrue("Position 26 (Yellow start) should be safe", LudoBoard.isSafeCell(26))
        assertTrue("Position 39 (Green start) should be safe", LudoBoard.isSafeCell(39))
    }

    @Test
    fun `star positions are safe`() {
        // Additional star positions
        assertTrue("Position 8 should be safe (star)", LudoBoard.isSafeCell(8))
        assertTrue("Position 21 should be safe (star)", LudoBoard.isSafeCell(21))
        assertTrue("Position 34 should be safe (star)", LudoBoard.isSafeCell(34))
        assertTrue("Position 47 should be safe (star)", LudoBoard.isSafeCell(47))
    }

    @Test
    fun `there are exactly 8 safe cells`() {
        assertEquals("Should have 8 safe cells", 8, LudoBoard.SAFE_CELLS.size)
    }

    @Test
    fun `non-safe cells are correctly identified`() {
        val nonSafeCells = listOf(1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 51)

        for (cell in nonSafeCells) {
            assertFalse("Position $cell should not be safe", LudoBoard.isSafeCell(cell))
        }
    }

    @Test
    fun `negative positions are not safe`() {
        assertFalse("Position -1 should not be safe", LudoBoard.isSafeCell(-1))
        assertFalse("Position -5 should not be safe", LudoBoard.isSafeCell(-5))
    }

    @Test
    fun `positions beyond track are not safe`() {
        assertFalse("Position 52 should not be safe", LudoBoard.isSafeCell(52))
        assertFalse("Position 100 should not be safe", LudoBoard.isSafeCell(100))
    }

    // ==================== ABSOLUTE POSITION TESTS ====================

    @Test
    fun `toAbsolutePosition wraps around correctly`() {
        // Red at position 50 = absolute 50
        assertEquals(50, LudoBoard.toAbsolutePosition(50, LudoColor.RED))

        // Blue at position 50 = (50 + 13) % 52 = 11
        assertEquals(11, LudoBoard.toAbsolutePosition(50, LudoColor.BLUE))

        // Green at position 50 = (50 + 26) % 52 = 24
        assertEquals(24, LudoBoard.toAbsolutePosition(50, LudoColor.GREEN))

        // Yellow at position 50 = (50 + 39) % 52 = 37
        assertEquals(37, LudoBoard.toAbsolutePosition(50, LudoColor.YELLOW))
    }

    @Test
    fun `toAbsolutePosition returns -1 for home stretch`() {
        for (color in LudoColor.entries) {
            for (pos in 51..56) {
                assertEquals("Position $pos for $color should return -1",
                    -1, LudoBoard.toAbsolutePosition(pos, color))
            }
        }
    }

    @Test
    fun `toAbsolutePosition for position 0 equals start position`() {
        for (color in LudoColor.entries) {
            assertEquals("Position 0 should equal start position for $color",
                LudoBoard.getStartPosition(color),
                LudoBoard.toAbsolutePosition(0, color))
        }
    }

    // ==================== HOME STRETCH TESTS ====================

    @Test
    fun `isInHomeStretch correctly identifies home stretch positions`() {
        // Positions 51-56 are home stretch
        for (pos in 51..56) {
            assertTrue("Position $pos should be in home stretch",
                LudoBoard.isInHomeStretch(pos))
        }
    }

    @Test
    fun `isInHomeStretch returns false for main track`() {
        for (pos in 0..50) {
            assertFalse("Position $pos should not be in home stretch",
                LudoBoard.isInHomeStretch(pos))
        }
    }

    @Test
    fun `isInHomeStretch returns false for finish position`() {
        assertFalse("Position 57 (finish) should not be in home stretch",
            LudoBoard.isInHomeStretch(57))
    }

    // ==================== MAIN TRACK TESTS ====================

    @Test
    fun `isOnMainTrack correctly identifies main track positions`() {
        for (pos in 0..50) {
            assertTrue("Position $pos should be on main track",
                LudoBoard.isOnMainTrack(pos))
        }
    }

    @Test
    fun `isOnMainTrack returns false for home stretch`() {
        for (pos in 51..56) {
            assertFalse("Position $pos should not be on main track",
                LudoBoard.isOnMainTrack(pos))
        }
    }

    @Test
    fun `isOnMainTrack returns false for finish`() {
        assertFalse("Position 57 should not be on main track",
            LudoBoard.isOnMainTrack(57))
    }

    @Test
    fun `isOnMainTrack returns false for home`() {
        assertFalse("Position -1 (home) should not be on main track",
            LudoBoard.isOnMainTrack(-1))
    }

    // ==================== DISTANCE TO FINISH TESTS ====================

    @Test
    fun `distanceToFinish from main track positions`() {
        assertEquals("From position 0, distance should be 57", 57, LudoBoard.distanceToFinish(0))
        assertEquals("From position 25, distance should be 32", 32, LudoBoard.distanceToFinish(25))
        assertEquals("From position 50, distance should be 7", 7, LudoBoard.distanceToFinish(50))
    }

    @Test
    fun `distanceToFinish from home stretch positions`() {
        assertEquals("From position 51, distance should be 6", 6, LudoBoard.distanceToFinish(51))
        assertEquals("From position 54, distance should be 3", 3, LudoBoard.distanceToFinish(54))
        assertEquals("From position 56, distance should be 1", 1, LudoBoard.distanceToFinish(56))
    }

    @Test
    fun `distanceToFinish at finish is 0`() {
        assertEquals("From position 57, distance should be 0", 0, LudoBoard.distanceToFinish(57))
    }

    @Test
    fun `distanceToFinish from home is maximum`() {
        // From home, need 6 to get out + 57 to finish = 58 total moves theoretically
        // But the function returns 58 (FINISH_POSITION + 1)
        assertEquals("From home (-1), distance should be 58",
            58, LudoBoard.distanceToFinish(-1))
    }

    // ==================== COLLISION DETECTION TESTS ====================

    @Test
    fun `two players can be at same absolute position`() {
        // Red at relative 13 = absolute 13
        // Blue at relative 0 = absolute 13
        val redAbsolute = LudoBoard.toAbsolutePosition(13, LudoColor.RED)
        val blueAbsolute = LudoBoard.toAbsolutePosition(0, LudoColor.BLUE)

        assertEquals("Both should be at absolute position 13", redAbsolute, blueAbsolute)
    }

    @Test
    fun `players at same relative position have different absolute positions`() {
        // Both at relative position 10
        val redAbsolute = LudoBoard.toAbsolutePosition(10, LudoColor.RED)
        val blueAbsolute = LudoBoard.toAbsolutePosition(10, LudoColor.BLUE)
        val yellowAbsolute = LudoBoard.toAbsolutePosition(10, LudoColor.YELLOW)
        val greenAbsolute = LudoBoard.toAbsolutePosition(10, LudoColor.GREEN)

        // All should be different
        val positions = setOf(redAbsolute, blueAbsolute, yellowAbsolute, greenAbsolute)
        assertEquals("All 4 absolute positions should be unique", 4, positions.size)
    }

    // ==================== COLOR-KEYED POSITION MAPPING TESTS ====================

    /**
     * Verify that start positions are keyed by LudoColor enum, not player index.
     * This is the root cause of the bug where Yellow appeared at opposite side.
     */
    @Test
    fun `yellow token leaving home with dice 6 uses yellow start cell not index based`() {
        // Given: Yellow player color
        val color = LudoColor.YELLOW

        // When: Token leaves home (relative position becomes 0)
        val relativePosition = 0

        // Then: Absolute position should be Yellow's start (39), not based on any index
        val absolutePosition = LudoBoard.toAbsolutePosition(relativePosition, color)

        // Assert: Yellow's start is at absolute position 39 (bottom-left quadrant)
        assertEquals(
            "Yellow token at relative 0 should be at absolute 39 (Yellow's start)",
            39,
            absolutePosition
        )

        // Assert: This matches the color-based START_POSITIONS map
        assertEquals(
            "Yellow's start position should be 39",
            39,
            LudoBoard.getStartPosition(LudoColor.YELLOW)
        )

        // Assert: NOT at Red's position (0) which would be wrong
        assertNotEquals(
            "Yellow should NOT be at Red's start position 0",
            0,
            absolutePosition
        )

        // Assert: NOT at Green's position (26) which would be swapped
        assertNotEquals(
            "Yellow should NOT be at Green's start position 26",
            26,
            absolutePosition
        )
    }

    @Test
    fun `start positions follow clockwise order RED BLUE GREEN YELLOW`() {
        // Standard Ludo clockwise order from top-left:
        // RED (top-left, 0) → BLUE (top-right, 13) → GREEN (bottom-right, 26) → YELLOW (bottom-left, 39)
        val redStart = LudoBoard.getStartPosition(LudoColor.RED)
        val blueStart = LudoBoard.getStartPosition(LudoColor.BLUE)
        val greenStart = LudoBoard.getStartPosition(LudoColor.GREEN)
        val yellowStart = LudoBoard.getStartPosition(LudoColor.YELLOW)

        // Verify clockwise order with 13-cell spacing
        assertEquals("Red at position 0", 0, redStart)
        assertEquals("Blue at position 13", 13, blueStart)
        assertEquals("Green at position 26", 26, greenStart)
        assertEquals("Yellow at position 39", 39, yellowStart)

        // Verify order
        assertTrue("Red < Blue < Green < Yellow (clockwise)",
            redStart < blueStart && blueStart < greenStart && greenStart < yellowStart)
    }
}
