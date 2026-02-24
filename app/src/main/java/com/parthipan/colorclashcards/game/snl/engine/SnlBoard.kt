package com.parthipan.colorclashcards.game.snl.engine

import kotlin.random.Random

/**
 * Snake & Ladder board constants and helpers.
 * 10x10 grid with 100 squares, zigzag numbering (row 0 = bottom).
 */
object SnlBoard {

    const val BOARD_SIZE = 10
    const val TOTAL_SQUARES = 100

    // ==================== CLASSIC LAYOUT ====================

    /**
     * Classic snake positions: head → tail.
     * 8 snakes spread across the board.
     */
    val CLASSIC_SNAKES: Map<Int, Int> = mapOf(
        16 to 6,
        47 to 26,
        49 to 11,
        56 to 53,
        62 to 19,
        64 to 60,
        87 to 24,
        93 to 73,
        95 to 75,
        98 to 78
    )

    /**
     * Classic ladder positions: bottom → top.
     * 8 ladders spread across the board.
     */
    val CLASSIC_LADDERS: Map<Int, Int> = mapOf(
        1 to 38,
        4 to 14,
        9 to 31,
        21 to 42,
        28 to 84,
        36 to 44,
        51 to 67,
        71 to 91,
        80 to 100
    )

    // ==================== COORDINATE HELPERS ====================

    /**
     * Convert a board square (1-100) to grid row and column.
     * Uses zigzag layout: row 0 is bottom, squares go right on even rows, left on odd.
     *
     * @return Pair(row, col) where row 0 = bottom, col 0 = left
     */
    fun getRowCol(square: Int): Pair<Int, Int> {
        require(square in 1..TOTAL_SQUARES) { "Square must be 1-100, got $square" }
        val index = square - 1
        val row = index / BOARD_SIZE
        val col = if (row % 2 == 0) {
            index % BOARD_SIZE // Even rows go left to right
        } else {
            BOARD_SIZE - 1 - (index % BOARD_SIZE) // Odd rows go right to left
        }
        return Pair(row, col)
    }

    /**
     * Convert grid coordinates to a board square number.
     * @param row 0 = bottom row
     * @param col 0 = left column
     * @return Square number 1-100
     */
    fun getSquare(row: Int, col: Int): Int {
        require(row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE)
        val effectiveCol = if (row % 2 == 0) col else (BOARD_SIZE - 1 - col)
        return row * BOARD_SIZE + effectiveCol + 1
    }

    // ==================== RANDOM LAYOUT ====================

    /**
     * Generate a random board layout with valid snake/ladder placement.
     *
     * Rules:
     * - No snake head or ladder top on square 100
     * - No ladder bottom or snake tail on square 1
     * - Snakes go down (head > tail), ladders go up (top > bottom)
     * - No square has both a snake head and ladder bottom
     * - No chain reactions (snake tail/ladder top don't land on another snake/ladder)
     * - Reasonable count: 8-10 snakes, 8-10 ladders
     *
     * @return Pair(snakes, ladders)
     */
    fun generateRandomLayout(): Pair<Map<Int, Int>, Map<Int, Int>> {
        val snakes = mutableMapOf<Int, Int>()
        val ladders = mutableMapOf<Int, Int>()
        val usedSquares = mutableSetOf(1, 100) // Reserve start and end

        // Generate 8-10 ladders
        val ladderCount = Random.nextInt(8, 11)
        var attempts = 0
        while (ladders.size < ladderCount && attempts < 200) {
            attempts++
            val bottom = Random.nextInt(2, 80)
            val top = Random.nextInt(bottom + 10, minOf(bottom + 50, 100))

            if (bottom !in usedSquares && top !in usedSquares && top <= 100) {
                ladders[bottom] = top
                usedSquares.add(bottom)
                usedSquares.add(top)
            }
        }

        // Generate 8-10 snakes
        val snakeCount = Random.nextInt(8, 11)
        attempts = 0
        while (snakes.size < snakeCount && attempts < 200) {
            attempts++
            val head = Random.nextInt(21, 99)
            val tail = Random.nextInt(maxOf(2, head - 50), head - 5)

            if (head !in usedSquares && tail !in usedSquares && tail >= 2) {
                snakes[head] = tail
                usedSquares.add(head)
                usedSquares.add(tail)
            }
        }

        return Pair(snakes, ladders)
    }

    // ==================== QUERY HELPERS ====================

    /**
     * Check if a square has a snake head.
     */
    fun isSnakeHead(square: Int, snakes: Map<Int, Int>): Boolean = square in snakes

    /**
     * Check if a square has a ladder bottom.
     */
    fun isLadderBottom(square: Int, ladders: Map<Int, Int>): Boolean = square in ladders

    /**
     * Get the destination after landing on a square (resolves snakes/ladders).
     * @return Final position after snake/ladder, or same position if neither
     */
    fun resolveSquare(square: Int, snakes: Map<Int, Int>, ladders: Map<Int, Int>): Int {
        return when {
            square in snakes -> snakes[square]!!
            square in ladders -> ladders[square]!!
            else -> square
        }
    }
}
