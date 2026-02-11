package com.parthipan.colorclashcards.ui.ludo

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for token stacking logic:
 * 1. Z-ordering sort — current player's tokens render last (on top)
 * 2. calculateStackOffsets — layout patterns for 1-4+ tokens
 */
class TokenStackingTest {

    // ==================== Z-ORDERING SORT TESTS ====================

    /**
     * Mirrors the sort from LudoOfflineGameScreen / LudoOnlineGameScreen:
     *   tokens.sortedBy { if (it.playerId == currentPlayerId) 1 else 0 }
     *
     * Since TokenWithContext is private to the screen files, we test the
     * same sorting predicate with a minimal data class.
     */
    private data class TokenEntry(
        val tokenId: Int,
        val playerId: String,
        val position: Int
    )

    /** Apply the same sort used in both game screens. */
    private fun sortForZOrder(tokens: List<TokenEntry>, currentPlayerId: String): List<TokenEntry> {
        return tokens.sortedBy { if (it.playerId == currentPlayerId) 1 else 0 }
    }

    @Test
    fun `current player tokens sorted last in position group`() {
        val tokens = listOf(
            TokenEntry(0, "player-red", position = 5),
            TokenEntry(1, "player-blue", position = 5)
        )

        val sorted = sortForZOrder(tokens, "player-red")

        // Opponent (blue) first, current player (red) last
        assertEquals("player-blue", sorted[0].playerId)
        assertEquals("player-red", sorted[1].playerId)
    }

    @Test
    fun `four tokens from different players - current player last`() {
        val tokens = listOf(
            TokenEntry(0, "player-red", position = 10),
            TokenEntry(0, "player-blue", position = 10),
            TokenEntry(0, "player-green", position = 10),
            TokenEntry(0, "player-yellow", position = 10)
        )

        val sorted = sortForZOrder(tokens, "player-green")

        // Green (current player) must be last
        assertEquals("player-green", sorted.last().playerId)
        // All other players should precede green
        val nonGreen = sorted.dropLast(1)
        assertTrue(nonGreen.none { it.playerId == "player-green" })
    }

    @Test
    fun `only opponent tokens - order preserved (stable sort)`() {
        val tokens = listOf(
            TokenEntry(0, "player-red", position = 7),
            TokenEntry(1, "player-blue", position = 7),
            TokenEntry(2, "player-green", position = 7)
        )

        // Current player is yellow, not in the list
        val sorted = sortForZOrder(tokens, "player-yellow")

        // All get sort key 0, so stable sort preserves original order
        assertEquals("player-red", sorted[0].playerId)
        assertEquals("player-blue", sorted[1].playerId)
        assertEquals("player-green", sorted[2].playerId)
    }

    @Test
    fun `only current player tokens - order preserved`() {
        val tokens = listOf(
            TokenEntry(0, "player-red", position = 3),
            TokenEntry(1, "player-red", position = 3),
            TokenEntry(2, "player-red", position = 3)
        )

        val sorted = sortForZOrder(tokens, "player-red")

        // All same player, stable sort preserves order
        assertEquals(0, sorted[0].tokenId)
        assertEquals(1, sorted[1].tokenId)
        assertEquals(2, sorted[2].tokenId)
    }

    @Test
    fun `mixed positions - sort only affects same-position tokens`() {
        // Simulate groupBy position then sort within each group
        val allTokens = listOf(
            // Position 5
            TokenEntry(0, "player-red", position = 5),
            TokenEntry(0, "player-blue", position = 5),
            // Position 10
            TokenEntry(1, "player-red", position = 10),
            TokenEntry(1, "player-green", position = 10)
        )

        val grouped = allTokens.groupBy { it.position }
            .mapValues { (_, tokens) ->
                sortForZOrder(tokens, "player-red")
            }

        // Position 5: blue first, red last
        val pos5 = grouped[5]!!
        assertEquals("player-blue", pos5[0].playerId)
        assertEquals("player-red", pos5[1].playerId)

        // Position 10: green first, red last
        val pos10 = grouped[10]!!
        assertEquals("player-green", pos10[0].playerId)
        assertEquals("player-red", pos10[1].playerId)
    }

    @Test
    fun `multiple tokens of same current player at same position`() {
        val tokens = listOf(
            TokenEntry(0, "player-blue", position = 20),
            TokenEntry(0, "player-red", position = 20),
            TokenEntry(1, "player-red", position = 20),
            TokenEntry(1, "player-blue", position = 20)
        )

        val sorted = sortForZOrder(tokens, "player-red")

        // Both blue tokens first, both red tokens last
        assertEquals("player-blue", sorted[0].playerId)
        assertEquals("player-blue", sorted[1].playerId)
        assertEquals("player-red", sorted[2].playerId)
        assertEquals("player-red", sorted[3].playerId)
    }

    // ==================== CALCULATE STACK OFFSETS TESTS ====================

    @Test
    fun `single token - centered with full scale`() {
        val cellSize = 40.dp
        val (offsets, scale) = calculateStackOffsets(1, cellSize)

        assertEquals(1, offsets.size)
        assertEquals(0.dp, offsets[0].first)
        assertEquals(0.dp, offsets[0].second)
        assertEquals(1f, scale, 0.001f)
    }

    @Test
    fun `two tokens - side by side horizontally`() {
        val cellSize = 40.dp
        val (offsets, scale) = calculateStackOffsets(2, cellSize)

        assertEquals(2, offsets.size)
        assertEquals(0.75f, scale, 0.001f)

        // First token left, second token right
        assertTrue("Left token should have negative x", offsets[0].first < 0.dp)
        assertTrue("Right token should have positive x", offsets[1].first > 0.dp)

        // Both centered vertically
        assertEquals(0.dp, offsets[0].second)
        assertEquals(0.dp, offsets[1].second)
    }

    @Test
    fun `three tokens - triangle arrangement`() {
        val cellSize = 40.dp
        val (offsets, scale) = calculateStackOffsets(3, cellSize)

        assertEquals(3, offsets.size)
        assertEquals(0.65f, scale, 0.001f)

        // Top center: x=0, y<0
        assertEquals(0.dp, offsets[0].first)
        assertTrue("Top token should have negative y", offsets[0].second < 0.dp)

        // Bottom left: x<0, y>0
        assertTrue("Bottom-left x should be negative", offsets[1].first < 0.dp)
        assertTrue("Bottom-left y should be positive", offsets[1].second > 0.dp)

        // Bottom right: x>0, y>0
        assertTrue("Bottom-right x should be positive", offsets[2].first > 0.dp)
        assertTrue("Bottom-right y should be positive", offsets[2].second > 0.dp)
    }

    @Test
    fun `four or more tokens - 2x2 grid layout`() {
        val cellSize = 40.dp
        val (offsets, scale) = calculateStackOffsets(4, cellSize)

        assertEquals(4, offsets.size)
        assertEquals(0.55f, scale, 0.001f)

        // Top-left: x<0, y<0
        assertTrue(offsets[0].first < 0.dp)
        assertTrue(offsets[0].second < 0.dp)

        // Top-right: x>0, y<0
        assertTrue(offsets[1].first > 0.dp)
        assertTrue(offsets[1].second < 0.dp)

        // Bottom-left: x<0, y>0
        assertTrue(offsets[2].first < 0.dp)
        assertTrue(offsets[2].second > 0.dp)

        // Bottom-right: x>0, y>0
        assertTrue(offsets[3].first > 0.dp)
        assertTrue(offsets[3].second > 0.dp)
    }

    @Test
    fun `five or more tokens still uses 2x2 grid with 4 offsets`() {
        val cellSize = 40.dp
        val (offsets, scale) = calculateStackOffsets(5, cellSize)

        // Still 4 offsets (2x2 grid), scale 0.55
        assertEquals(4, offsets.size)
        assertEquals(0.55f, scale, 0.001f)
    }

    @Test
    fun `offset values scale proportionally to cell size`() {
        val (offsets20, scale20) = calculateStackOffsets(2, 20.dp)
        val (offsets40, scale40) = calculateStackOffsets(2, 40.dp)

        // Same scale factor regardless of cell size
        assertEquals(scale20, scale40, 0.001f)

        // Offsets should be proportionally larger with bigger cell
        val leftOffset20 = offsets20[0].first.value
        val leftOffset40 = offsets40[0].first.value
        assertEquals(leftOffset40 / leftOffset20, 2f, 0.01f)
    }
}
