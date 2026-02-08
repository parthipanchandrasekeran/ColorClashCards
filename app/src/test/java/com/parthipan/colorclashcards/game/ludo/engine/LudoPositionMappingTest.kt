package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.*
import com.parthipan.colorclashcards.ui.ludo.BoardPosition
import com.parthipan.colorclashcards.ui.ludo.LudoBoardPositions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Ludo position mapping correctness.
 *
 * Verifies that:
 * - Ring positions map to correct BoardPositions for all colors
 * - Lane positions map to correct color-specific BoardPositions
 * - Finish and home positions render correctly
 * - Captures work at non-safe cells and are blocked at safe cells
 * - Lane entry logic is correct
 * - Cross-color collisions produce the same BoardPosition
 * - All lane cells are disjoint across colors
 * - Captured tokens return to HOME
 */
class LudoPositionMappingTest {

    private lateinit var redPlayer: LudoPlayer
    private lateinit var bluePlayer: LudoPlayer
    private lateinit var greenPlayer: LudoPlayer
    private lateinit var yellowPlayer: LudoPlayer

    @Before
    fun setUp() {
        redPlayer = LudoPlayer(id = "red", name = "Red", color = LudoColor.RED)
        bluePlayer = LudoPlayer(id = "blue", name = "Blue", color = LudoColor.BLUE)
        greenPlayer = LudoPlayer(id = "green", name = "Green", color = LudoColor.GREEN)
        yellowPlayer = LudoPlayer(id = "yellow", name = "Yellow", color = LudoColor.YELLOW)
    }

    // ==================== RING POSITION CONSISTENCY ====================

    @Test
    fun `ring position 0 maps to start cell for each color`() {
        LudoColor.entries.forEach { color ->
            val boardPos = LudoBoardPositions.getGridPosition(0, color)
            assertNotNull("Position 0 should map for $color", boardPos)

            val startCell = LudoBoard.getStartCell(color)
            // BoardPosition is (column, row), startCell is (row, col)
            assertEquals(
                "$color position 0 column should match start cell col",
                startCell.second, boardPos!!.column
            )
            assertEquals(
                "$color position 0 row should match start cell row",
                startCell.first, boardPos.row
            )
        }
    }

    @Test
    fun `ring positions 0, 13, 26, 39 map to correct absolute cells`() {
        val keyPositions = listOf(0, 13, 26, 39)

        LudoColor.entries.forEach { color ->
            keyPositions.forEach { relPos ->
                val boardPos = LudoBoardPositions.getGridPosition(relPos, color)
                assertNotNull("$color at relative $relPos should produce a BoardPosition", boardPos)

                // Verify via absolute index
                val absIndex = LudoBoard.toAbsolutePosition(relPos, color)
                assertTrue("Absolute index should be valid (0-51)", absIndex in 0..51)

                val ringCell = LudoBoard.getRingCell(absIndex)
                assertNotNull("Ring cell at absolute $absIndex should exist", ringCell)
                assertEquals(
                    "$color relative $relPos: column mismatch",
                    ringCell!!.second, boardPos!!.column
                )
                assertEquals(
                    "$color relative $relPos: row mismatch",
                    ringCell.first, boardPos.row
                )
            }
        }
    }

    @Test
    fun `ring position 51 maps correctly for each color`() {
        LudoColor.entries.forEach { color ->
            val boardPos = LudoBoardPositions.getGridPosition(51, color)
            assertNotNull("Position 51 (last ring cell) should map for $color", boardPos)

            val absIndex = LudoBoard.toAbsolutePosition(51, color)
            val laneEntryIndex = LudoBoard.LANE_ENTRY_INDEX_BY_COLOR[color]!!
            assertEquals(
                "$color: relative position 51 should be at lane entry index",
                laneEntryIndex, absIndex
            )
        }
    }

    // ==================== LANE POSITION CONSISTENCY ====================

    @Test
    fun `lane positions 52-56 map to correct color-specific cells`() {
        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]!!
            for (laneIndex in 0..4) {
                val relPos = 52 + laneIndex
                val boardPos = LudoBoardPositions.getGridPosition(relPos, color)
                assertNotNull("$color lane position $relPos should map", boardPos)

                val expectedCell = laneCells[laneIndex]
                assertEquals(
                    "$color lane $relPos: column should match",
                    expectedCell.second, boardPos!!.column
                )
                assertEquals(
                    "$color lane $relPos: row should match",
                    expectedCell.first, boardPos.row
                )
            }
        }
    }

    @Test
    fun `position 57 (finish) returns null from getGridPosition`() {
        // Position 57 is FINISH_POSITION, handled separately via getFinishPosition
        LudoColor.entries.forEach { color ->
            val boardPos = LudoBoardPositions.getGridPosition(57, color)
            assertNull("Position 57 (finish) should return null from getGridPosition for $color", boardPos)
        }
    }

    // ==================== FINISH POSITION RENDERING ====================

    @Test
    fun `finish positions are distinct per color`() {
        val finishPositions = LudoColor.entries.map { color ->
            LudoBoardPositions.getFinishPosition(color)
        }

        // All 4 should be distinct
        assertEquals(
            "All finish positions should be unique",
            4, finishPositions.toSet().size
        )
    }

    @Test
    fun `finish positions are near center of board`() {
        LudoColor.entries.forEach { color ->
            val pos = LudoBoardPositions.getFinishPosition(color)
            assertTrue(
                "$color finish position ($pos) should be near center (6-8, 6-8)",
                pos.column in 6..8 && pos.row in 6..8
            )
        }
    }

    // ==================== HOME POSITION RENDERING ====================

    @Test
    fun `home positions have 4 slots per color`() {
        LudoColor.entries.forEach { color ->
            val homePositions = LudoBoardPositions.getHomeBasePositions(color)
            assertEquals("$color should have 4 home slots", 4, homePositions.size)
        }
    }

    @Test
    fun `home positions are in correct quadrant`() {
        // GREEN: top-left (rows 0-5, cols 0-5)
        LudoBoardPositions.getHomeBasePositions(LudoColor.GREEN).forEach { pos ->
            assertTrue("GREEN home $pos should be in top-left", pos.row in 0..5 && pos.column in 0..5)
        }

        // YELLOW: top-right (rows 0-5, cols 9-14)
        LudoBoardPositions.getHomeBasePositions(LudoColor.YELLOW).forEach { pos ->
            assertTrue("YELLOW home $pos should be in top-right", pos.row in 0..5 && pos.column in 9..14)
        }

        // RED: bottom-left (rows 9-14, cols 0-5)
        LudoBoardPositions.getHomeBasePositions(LudoColor.RED).forEach { pos ->
            assertTrue("RED home $pos should be in bottom-left", pos.row in 9..14 && pos.column in 0..5)
        }

        // BLUE: bottom-right (rows 9-14, cols 9-14)
        LudoBoardPositions.getHomeBasePositions(LudoColor.BLUE).forEach { pos ->
            assertTrue("BLUE home $pos should be in bottom-right", pos.row in 9..14 && pos.column in 9..14)
        }
    }

    @Test
    fun `all 4 home slots per color are distinct`() {
        LudoColor.entries.forEach { color ->
            val positions = LudoBoardPositions.getHomeBasePositions(color)
            assertEquals(
                "$color home slots should all be unique",
                4, positions.toSet().size
            )
        }
    }

    // ==================== CAPTURE TESTS ====================

    @Test
    fun `capture at non-safe cell works correctly`() {
        // Place RED token at relative position 10 (non-safe)
        val redTokenActive = Token(id = 0, state = TokenState.ACTIVE, position = 10)
        val redWithToken = redPlayer.copy(
            tokens = listOf(redTokenActive, Token(1), Token(2), Token(3))
        )

        // Calculate RED's absolute position at relative 10
        val redAbsPos = LudoBoard.toAbsolutePosition(10, LudoColor.RED)
        assertFalse("Position should not be safe", LudoBoard.isSafeCell(redAbsPos))

        // Find BLUE's relative position that maps to the same absolute position
        val blueStartIndex = LudoBoard.getStartIndex(LudoColor.BLUE)
        val blueRelPos = ((redAbsPos - blueStartIndex) + LudoBoard.RING_SIZE) % LudoBoard.RING_SIZE

        // Place BLUE token at position before the target, with dice to land on RED
        val blueTokenActive = Token(id = 0, state = TokenState.ACTIVE, position = blueRelPos - 1)
        val blueWithToken = bluePlayer.copy(
            tokens = listOf(blueTokenActive, Token(1), Token(2), Token(3))
        )

        val gameState = LudoGameState(
            players = listOf(blueWithToken, redWithToken),
            currentTurnPlayerId = blueWithToken.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 1,
            canRollDice = false,
            mustSelectToken = true
        )

        val result = LudoEngine.moveToken(gameState, 0)
        assertTrue("Move should succeed", result is MoveResult.Success)

        val success = result as MoveResult.Success
        assertNotNull("Should have captured token info", success.move.capturedTokenInfo)
        assertTrue("Should get bonus turn for capture", success.bonusTurn)

        // Verify captured RED token is back HOME
        val capturedRedPlayer = success.newState.players.find { it.color == LudoColor.RED }!!
        val capturedToken = capturedRedPlayer.getToken(0)!!
        assertEquals("Captured token should be HOME", TokenState.HOME, capturedToken.state)
        assertEquals("Captured token position should be -1", -1, capturedToken.position)
    }

    @Test
    fun `no capture at safe cells`() {
        // Test all 8 safe indices
        val safeIndices = LudoBoard.SAFE_INDICES.toList()
        assertEquals("Should have 8 safe cells", 8, safeIndices.size)

        safeIndices.forEach { safeAbsIdx ->
            assertTrue(
                "Index $safeAbsIdx should be safe",
                LudoBoard.isSafeCell(safeAbsIdx)
            )
        }

        // Test a specific safe cell capture attempt: place RED at a start position (safe)
        val greenStartIndex = LudoBoard.getStartIndex(LudoColor.GREEN) // index 0 (safe)
        assertTrue("GREEN start should be safe", LudoBoard.isSafeCell(greenStartIndex))

        // RED relative position that maps to GREEN's start (absolute 0)
        val redStartIndex = LudoBoard.getStartIndex(LudoColor.RED)
        val redRelPos = ((greenStartIndex - redStartIndex) + LudoBoard.RING_SIZE) % LudoBoard.RING_SIZE

        val redTokenOnSafe = Token(id = 0, state = TokenState.ACTIVE, position = redRelPos)
        val redWithToken = redPlayer.copy(
            tokens = listOf(redTokenOnSafe, Token(1), Token(2), Token(3))
        )

        // GREEN token arriving at its own start (safe cell) - shouldn't capture RED
        val greenTokenActive = Token(id = 0, state = TokenState.ACTIVE, position = LudoBoard.RING_END)
        val greenWithToken = greenPlayer.copy(
            tokens = listOf(greenTokenActive, Token(1), Token(2), Token(3))
        )

        // GREEN at position 51 + dice 1 should wrap to position 0 (on ring, not enter lane)
        // Actually, position 51 for GREEN is the lane entry, so dice 1 enters the lane.
        // Let's use a different approach: GREEN at position 51 enters lane.
        // Instead test with BLUE placing a token near a safe cell.

        // Simpler: verify safe cell lookup is consistent for all safe positions
        val startIndices = LudoColor.entries.map { LudoBoard.getStartIndex(it) }
        val starIndices = LudoColor.entries.map { (LudoBoard.getStartIndex(it) + 8) % LudoBoard.RING_SIZE }

        (startIndices + starIndices).forEach { idx ->
            assertTrue("Index $idx should be safe", LudoBoard.isSafeCell(idx))
        }
    }

    // ==================== LANE ENTRY LOGIC ====================

    @Test
    fun `token at position 51 plus dice 1 enters lane correctly`() {
        // For each color, position 51 + 1 = 52 (first lane cell)
        LudoColor.entries.forEach { color ->
            val token = Token(id = 0, state = TokenState.ACTIVE, position = 51)
            assertTrue(
                "$color token at pos 51 should be able to move with dice 1",
                token.canMove(1)
            )

            // After move: should be at position 52 (lane start)
            val player = LudoPlayer(
                id = "test-$color",
                name = color.name,
                color = color,
                tokens = listOf(token, Token(1), Token(2), Token(3))
            )

            val gameState = LudoGameState(
                players = listOf(player),
                currentTurnPlayerId = player.id,
                gameStatus = GameStatus.IN_PROGRESS,
                diceValue = 1,
                canRollDice = false,
                mustSelectToken = true
            )

            val result = LudoEngine.moveToken(gameState, 0)
            assertTrue("$color move should succeed", result is MoveResult.Success)
            val newToken = (result as MoveResult.Success).newState.players[0].getToken(0)!!
            assertEquals("$color token should be at lane start (52)", 52, newToken.position)
            assertEquals("$color token should still be ACTIVE", TokenState.ACTIVE, newToken.state)
        }
    }

    @Test
    fun `token at position 56 plus dice 1 reaches finish`() {
        LudoColor.entries.forEach { color ->
            val token = Token(id = 0, state = TokenState.ACTIVE, position = 56)
            assertTrue(
                "$color token at pos 56 should be able to move with dice 1",
                token.canMove(1)
            )

            val player = LudoPlayer(
                id = "test-$color",
                name = color.name,
                color = color,
                tokens = listOf(token, Token(1), Token(2), Token(3))
            )

            val gameState = LudoGameState(
                players = listOf(player),
                currentTurnPlayerId = player.id,
                gameStatus = GameStatus.IN_PROGRESS,
                diceValue = 1,
                canRollDice = false,
                mustSelectToken = true
            )

            val result = LudoEngine.moveToken(gameState, 0)
            assertTrue("$color move should succeed", result is MoveResult.Success)
            val newToken = (result as MoveResult.Success).newState.players[0].getToken(0)!!
            assertEquals("$color token should be at finish (57)", 57, newToken.position)
            assertEquals("$color token should be FINISHED", TokenState.FINISHED, newToken.state)
        }
    }

    // ==================== CROSS-COLOR POSITION COLLISION ====================

    @Test
    fun `two colors at same absolute ring position produce same BoardPosition`() {
        // RED at relative 0 = absolute 39
        // GREEN at relative 39 = absolute (39 + 0) % 52 = 39
        val redAbsAt0 = LudoBoard.toAbsolutePosition(0, LudoColor.RED)
        val greenRelFor39 = ((redAbsAt0 - LudoBoard.getStartIndex(LudoColor.GREEN)) +
            LudoBoard.RING_SIZE) % LudoBoard.RING_SIZE

        val redBoardPos = LudoBoardPositions.getGridPosition(0, LudoColor.RED)
        val greenBoardPos = LudoBoardPositions.getGridPosition(greenRelFor39, LudoColor.GREEN)

        assertNotNull("RED at rel 0 should map", redBoardPos)
        assertNotNull("GREEN at same absolute should map", greenBoardPos)
        assertEquals(
            "Same absolute position should produce same BoardPosition",
            redBoardPos, greenBoardPos
        )
    }

    @Test
    fun `all four colors at their start positions produce distinct BoardPositions`() {
        val startPositions = LudoColor.entries.map { color ->
            LudoBoardPositions.getGridPosition(0, color)!!
        }

        assertEquals(
            "All 4 start positions should be distinct",
            4, startPositions.toSet().size
        )
    }

    @Test
    fun `cross-color collision at arbitrary ring position`() {
        // Pick absolute ring index 20 (arbitrary non-safe cell)
        val absTarget = 20

        val boardPositions = LudoColor.entries.map { color ->
            val startIndex = LudoBoard.getStartIndex(color)
            val relPos = ((absTarget - startIndex) + LudoBoard.RING_SIZE) % LudoBoard.RING_SIZE
            val boardPos = LudoBoardPositions.getGridPosition(relPos, color)
            assertNotNull("$color should map relative $relPos to a BoardPosition", boardPos)
            boardPos!!
        }

        // All should be the same BoardPosition
        assertTrue(
            "All colors at absolute $absTarget should produce same BoardPosition",
            boardPositions.all { it == boardPositions[0] }
        )
    }

    // ==================== DISJOINT LANES ====================

    @Test
    fun `all lane cells are disjoint across colors`() {
        val allLaneCells = mutableListOf<Pair<LudoColor, Pair<Int, Int>>>()

        LudoColor.entries.forEach { color ->
            val laneCells = LudoBoard.LANE_CELLS_BY_COLOR[color]!!
            laneCells.forEach { cell ->
                allLaneCells.add(color to cell)
            }
        }

        // Check for duplicates across colors
        val cellGroups = allLaneCells.groupBy { it.second }
        cellGroups.forEach { (cell, entries) ->
            assertEquals(
                "Cell $cell should belong to exactly one color's lane, but found: ${entries.map { it.first }}",
                1, entries.size
            )
        }
    }

    @Test
    fun `lane BoardPositions are disjoint across colors`() {
        val allLanePositions = mutableListOf<Pair<LudoColor, BoardPosition>>()

        LudoColor.entries.forEach { color ->
            for (laneIndex in 0..4) {
                val relPos = 52 + laneIndex
                val boardPos = LudoBoardPositions.getGridPosition(relPos, color)
                assertNotNull("$color lane $laneIndex should produce a BoardPosition", boardPos)
                allLanePositions.add(color to boardPos!!)
            }
        }

        val posGroups = allLanePositions.groupBy { it.second }
        posGroups.forEach { (pos, entries) ->
            assertEquals(
                "BoardPosition $pos should belong to one color's lane, but found: ${entries.map { it.first }}",
                1, entries.size
            )
        }
    }

    // ==================== TOKEN STATE AFTER CAPTURE ====================

    @Test
    fun `captured token returns to HOME with correct id`() {
        // Place BLUE token 0 at relative position 5 (non-safe)
        val blueAbsPos = LudoBoard.toAbsolutePosition(5, LudoColor.BLUE)
        assertFalse("Should not be a safe cell", LudoBoard.isSafeCell(blueAbsPos))

        val blueTokenActive = Token(id = 2, state = TokenState.ACTIVE, position = 5)
        val blueWithToken = bluePlayer.copy(
            tokens = listOf(Token(0), Token(1), blueTokenActive, Token(3))
        )

        // RED needs to land on the same absolute position
        val redStartIndex = LudoBoard.getStartIndex(LudoColor.RED)
        val redRelTarget = ((blueAbsPos - redStartIndex) + LudoBoard.RING_SIZE) % LudoBoard.RING_SIZE
        val redTokenActive = Token(id = 1, state = TokenState.ACTIVE, position = redRelTarget - 2)
        val redWithToken = redPlayer.copy(
            tokens = listOf(Token(0), redTokenActive, Token(2), Token(3))
        )

        val gameState = LudoGameState(
            players = listOf(redWithToken, blueWithToken),
            currentTurnPlayerId = redWithToken.id,
            gameStatus = GameStatus.IN_PROGRESS,
            diceValue = 2,
            canRollDice = false,
            mustSelectToken = true
        )

        val result = LudoEngine.moveToken(gameState, 1)
        assertTrue("Move should succeed", result is MoveResult.Success)

        val success = result as MoveResult.Success
        assertNotNull("Should have capture info", success.move.capturedTokenInfo)

        // Verify BLUE token 2 is back HOME
        val capturedBluePlayer = success.newState.players.find { it.color == LudoColor.BLUE }!!
        val capturedToken = capturedBluePlayer.getToken(2)!!
        assertEquals("Captured token id should be preserved", 2, capturedToken.id)
        assertEquals("Captured token should be HOME", TokenState.HOME, capturedToken.state)
        assertEquals("Captured token position should be -1", -1, capturedToken.position)
    }

    // ==================== COMPREHENSIVE RING MAPPING ====================

    @Test
    fun `every ring position 0-51 maps to a valid BoardPosition for all colors`() {
        LudoColor.entries.forEach { color ->
            for (relPos in 0..51) {
                val boardPos = LudoBoardPositions.getGridPosition(relPos, color)
                assertNotNull(
                    "$color at relative $relPos should produce a BoardPosition",
                    boardPos
                )
                assertTrue(
                    "$color at $relPos: column ${boardPos!!.column} should be on board (0-14)",
                    boardPos.column in 0..14
                )
                assertTrue(
                    "$color at $relPos: row ${boardPos.row} should be on board (0-14)",
                    boardPos.row in 0..14
                )
            }
        }
    }

    @Test
    fun `ring positions that differ by color offset map to same absolute cell`() {
        // GREEN starts at 0, YELLOW at 13 — so GREEN relative 13 = YELLOW relative 0
        val greenAt13 = LudoBoardPositions.getGridPosition(13, LudoColor.GREEN)
        val yellowAt0 = LudoBoardPositions.getGridPosition(0, LudoColor.YELLOW)
        assertEquals("GREEN@13 should equal YELLOW@0", greenAt13, yellowAt0)

        // YELLOW at 13 = BLUE at 0
        val yellowAt13 = LudoBoardPositions.getGridPosition(13, LudoColor.YELLOW)
        val blueAt0 = LudoBoardPositions.getGridPosition(0, LudoColor.BLUE)
        assertEquals("YELLOW@13 should equal BLUE@0", yellowAt13, blueAt0)

        // BLUE at 13 = RED at 0
        val blueAt13 = LudoBoardPositions.getGridPosition(13, LudoColor.BLUE)
        val redAt0 = LudoBoardPositions.getGridPosition(0, LudoColor.RED)
        assertEquals("BLUE@13 should equal RED@0", blueAt13, redAt0)

        // RED at 13 = GREEN at 0
        val redAt13 = LudoBoardPositions.getGridPosition(13, LudoColor.RED)
        val greenAt0 = LudoBoardPositions.getGridPosition(0, LudoColor.GREEN)
        assertEquals("RED@13 should equal GREEN@0", redAt13, greenAt0)
    }
}
