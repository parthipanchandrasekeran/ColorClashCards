package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.LudoColor

/**
 * Classic Ludo Board Layout (15x15 grid)
 *
 * QUADRANTS (6x6 home areas in corners):
 * - Top-left (rows 0-5, cols 0-5): GREEN home
 * - Top-right (rows 0-5, cols 9-14): YELLOW home
 * - Bottom-left (rows 9-14, cols 0-5): RED home
 * - Bottom-right (rows 9-14, cols 9-14): BLUE home
 *
 * TRACK AREAS (cross-shaped arms only — path never enters home corners):
 * - Left arm: rows 6-8, cols 0-5
 * - Bottom arm: rows 9-14, cols 6-8
 * - Right arm: rows 6-8, cols 9-14
 * - Top arm: rows 0-5, cols 6-8
 * - Center (finish): rows 6-8, cols 6-8
 *
 * MOVEMENT: Clockwise (top→right, right→down, bottom→left, left→up)
 * 52 cells, 4 sections of 13. Arm order: LEFT → TOP → RIGHT → BOTTOM.
 *
 * START POSITIONS (explicit coordinates, evenly spaced by 13):
 * - GREEN: (6,1) — ring index 0  — left arm
 * - YELLOW: (1,8) — ring index 13 — top arm
 * - BLUE: (8,13) — ring index 26 — right arm
 * - RED: (13,6) — ring index 39  — bottom arm
 */
object LudoBoard {

    /** Grid dimensions */
    const val BOARD_SIZE = 15

    /** Total cells on the outer ring */
    const val RING_SIZE = 52

    /** Number of cells in each player's finish lane */
    const val LANE_SIZE = 6

    /** Position value for tokens at home base */
    const val HOME_POSITION = -1

    /** Position value for finished tokens (last lane cell, index 5) */
    const val FINISH_POSITION = 57

    /** First position of finish lane (relative) */
    const val LANE_START = 52

    /** Last position on the outer ring (relative) */
    const val RING_END = 51

    // ==================== RING PATH (52 cells, clockwise) ====================
    // IMPORTANT: This is the FINALIZED ring path. Do not reverse/rotate after definition.
    // All start indices are computed from indexOf() after this list is defined.

    /**
     * The outer ring path as an ordered list of (row, col) coordinates.
     * Movement is CLOCKWISE: index+1 = forward, always.
     * NO cell falls inside any 6x6 home corner area.
     *
     * Clockwise arm order: LEFT → TOP → RIGHT → BOTTOM → LEFT
     * (top moves right, right moves down, bottom moves left, left moves up)
     *
     * 52 cells total, 4 sections of 13 (evenly spaced starts):
     * - GREEN section (0-12): left arm → top arm
     * - YELLOW section (13-25): top arm → right arm
     * - BLUE section (26-38): right arm → bottom arm
     * - RED section (39-51): bottom arm → left arm
     *
     * Starts: 0, 13, 26, 39 (all 13 apart)
     * Stars: 8, 21, 34, 47 (all at start+8)
     * Lane entries: 51, 12, 25, 38 (one before each start)
     */
    val RING_CELLS: List<Pair<Int, Int>> = listOf(
        // === LEFT ARM → TOP ARM: GREEN section (indices 0-12) ===
        // Row 6 going RIGHT, diagonal up into top arm, col 6 going UP, across top, col 8
        Pair(6, 1),   //  0 - GREEN START
        Pair(6, 2),   //  1
        Pair(6, 3),   //  2
        Pair(6, 4),   //  3
        Pair(6, 5),   //  4
        Pair(5, 6),   //  5 - diagonal into top arm
        Pair(4, 6),   //  6
        Pair(3, 6),   //  7
        Pair(2, 6),   //  8 - GREEN STAR (safe)
        Pair(1, 6),   //  9
        Pair(0, 6),   // 10
        Pair(0, 7),   // 11 - top tip
        Pair(0, 8),   // 12

        // === TOP ARM → RIGHT ARM: YELLOW section (indices 13-25) ===
        // Col 8 going DOWN, diagonal right into right arm, row 6 going RIGHT, tip, row 8
        Pair(1, 8),   // 13 - YELLOW START
        Pair(2, 8),   // 14
        Pair(3, 8),   // 15
        Pair(4, 8),   // 16
        Pair(5, 8),   // 17
        Pair(6, 9),   // 18 - diagonal into right arm
        Pair(6, 10),  // 19
        Pair(6, 11),  // 20
        Pair(6, 12),  // 21 - YELLOW STAR (safe)
        Pair(6, 13),  // 22
        Pair(6, 14),  // 23
        Pair(7, 14),  // 24 - right tip
        Pair(8, 14),  // 25

        // === RIGHT ARM → BOTTOM ARM: BLUE section (indices 26-38) ===
        // Row 8 going LEFT, diagonal down into bottom arm, col 8 going DOWN, tip, col 6
        Pair(8, 13),  // 26 - BLUE START
        Pair(8, 12),  // 27
        Pair(8, 11),  // 28
        Pair(8, 10),  // 29
        Pair(8, 9),   // 30
        Pair(9, 8),   // 31 - diagonal into bottom arm
        Pair(10, 8),  // 32
        Pair(11, 8),  // 33
        Pair(12, 8),  // 34 - BLUE STAR (safe)
        Pair(13, 8),  // 35
        Pair(14, 8),  // 36
        Pair(14, 7),  // 37 - bottom tip
        Pair(14, 6),  // 38

        // === BOTTOM ARM → LEFT ARM: RED section (indices 39-51) ===
        // Col 6 going UP, diagonal left into left arm, row 8 going LEFT, tip, row 6
        Pair(13, 6),  // 39 - RED START
        Pair(12, 6),  // 40
        Pair(11, 6),  // 41
        Pair(10, 6),  // 42
        Pair(9, 6),   // 43
        Pair(8, 5),   // 44 - diagonal into left arm
        Pair(8, 4),   // 45
        Pair(8, 3),   // 46
        Pair(8, 2),   // 47 - RED STAR (safe)
        Pair(8, 1),   // 48
        Pair(8, 0),   // 49
        Pair(7, 0),   // 50 - left tip
        Pair(6, 0)    // 51 - last cell, wraps to 0 or enters GREEN's lane
    )

    // ==================== START POSITIONS ====================
    // STEP 1: Define explicit start cell coordinates (row, col) for each color.
    // These MUST exist in RING_CELLS. Use debug mode to find correct coordinates.

    /**
     * Start cell coordinates by color (row, col).
     * This is where a token spawns when leaving HOME with dice=6.
     *
     * Start cells have 90° rotational symmetry around center (7,7).
     * Clockwise ring order: GREEN → YELLOW → BLUE → RED
     * Ring indices: GREEN=0, YELLOW=13, BLUE=26, RED=39 (evenly spaced by 13)
     */
    val START_CELL_BY_COLOR: Map<LudoColor, Pair<Int, Int>> = mapOf(
        LudoColor.GREEN to Pair(6, 1),    // ring index 0  — left arm
        LudoColor.YELLOW to Pair(1, 8),   // ring index 13 — top arm
        LudoColor.BLUE to Pair(8, 13),    // ring index 26 — right arm
        LudoColor.RED to Pair(13, 6)      // ring index 39 — bottom arm
    )

    // STEP 2: Compute start indices by finding each start cell in RING_CELLS.
    // This ensures START_INDEX_BY_COLOR is always consistent with RING_CELLS.

    /**
     * Start ring index by color.
     * COMPUTED from indexOf(startCell) in RING_CELLS - guarantees consistency.
     */
    val START_INDEX_BY_COLOR: Map<LudoColor, Int> by lazy {
        val result = mutableMapOf<LudoColor, Int>()
        LudoColor.entries.forEach { color ->
            val cell = START_CELL_BY_COLOR[color]
                ?: error("No start cell defined for $color")
            val index = RING_CELLS.indexOf(cell)
            require(index >= 0) {
                "FATAL: Start cell $cell for $color not found in RING_CELLS!\n" +
                "Use debug mode to find correct coordinates.\n" +
                "RING_CELLS has ${RING_CELLS.size} entries."
            }
            result[color] = index
            log("START: $color -> cell=$cell, ringIndex=$index")
        }
        result
    }

    /**
     * Check if a position is inside any of the 4 home areas (6x6 corner squares).
     * Home areas:
     * - GREEN: rows 0-5, cols 0-5 (top-left)
     * - YELLOW: rows 0-5, cols 9-14 (top-right)
     * - RED: rows 9-14, cols 0-5 (bottom-left)
     * - BLUE: rows 9-14, cols 9-14 (bottom-right)
     */
    private fun isInsideHomeArea(row: Int, col: Int): Boolean {
        val inGreenHome = row in 0..5 && col in 0..5
        val inYellowHome = row in 0..5 && col in 9..14
        val inRedHome = row in 9..14 && col in 0..5
        val inBlueHome = row in 9..14 && col in 9..14
        return inGreenHome || inYellowHome || inRedHome || inBlueHome
    }

    // Runtime validation - runs when class is loaded
    init {
        // Validate RING_CELLS size
        require(RING_CELLS.size == RING_SIZE) {
            "RING_CELLS must have exactly $RING_SIZE cells, but has ${RING_CELLS.size}"
        }

        log("=== LUDO BOARD INITIALIZED ===")
        log("RING_CELLS size: ${RING_CELLS.size}")

        // Validate all start cells exist in RING_CELLS and are NOT inside home areas
        var allValid = true
        LudoColor.entries.forEach { color ->
            val startCell = START_CELL_BY_COLOR[color]
            if (startCell == null) {
                logError("ERROR: No start cell for $color")
                allValid = false
            } else {
                val index = RING_CELLS.indexOf(startCell)
                if (index < 0) {
                    logError("ERROR: Start cell $startCell for $color NOT in RING_CELLS!")
                    allValid = false
                } else if (isInsideHomeArea(startCell.first, startCell.second)) {
                    logError("BUG: Start cell $startCell for $color is INSIDE a home area!")
                    allValid = false
                } else {
                    log("OK: $color start=$startCell at ringIndex=$index")
                }
            }
        }

        if (!allValid) {
            logError("WARNING: Some start cells are invalid! Check mappings.")
        }

        // Validate clockwise direction: the cell after each start must follow
        // the clockwise rule (top→right, right→down, bottom→left, left→up).
        LudoColor.entries.forEach { color ->
            val startCell = START_CELL_BY_COLOR[color] ?: return@forEach
            val startIndex = RING_CELLS.indexOf(startCell)
            if (startIndex < 0) return@forEach
            val nextCell = RING_CELLS[(startIndex + 1) % RING_SIZE]
            val (r0, c0) = startCell
            val (r1, c1) = nextCell

            // Determine which arm the start is on and verify direction
            val isLeftArm = r0 in 6..8 && c0 in 0..5
            val isTopArm = r0 in 0..5 && c0 in 6..8
            val isRightArm = r0 in 6..8 && c0 in 9..14
            val isBottomArm = r0 in 9..14 && c0 in 6..8

            val clockwiseOk = when {
                // Left arm, top row (row 6): clockwise = col increases (RIGHT)
                isLeftArm && r0 == 6 -> c1 > c0
                // Left arm, bottom row (row 8): clockwise = col decreases (LEFT)
                isLeftArm && r0 == 8 -> c1 < c0
                // Top arm, right col (col 8): clockwise = row increases (DOWN)
                isTopArm && c0 == 8 -> r1 > r0
                // Top arm, left col (col 6): clockwise = row decreases (UP)
                isTopArm && c0 == 6 -> r1 < r0
                // Right arm, top row (row 6): clockwise = col increases (RIGHT)
                isRightArm && r0 == 6 -> c1 > c0
                // Right arm, bottom row (row 8): clockwise = col decreases (LEFT)
                isRightArm && r0 == 8 -> c1 < c0
                // Bottom arm, right col (col 8): clockwise = row increases (DOWN)
                isBottomArm && c0 == 8 -> r1 > r0
                // Bottom arm, left col (col 6): clockwise = row decreases (UP)
                isBottomArm && c0 == 6 -> r1 < r0
                else -> true // junction cell, skip
            }
            if (clockwiseOk) {
                log("CLOCKWISE OK: $color start $startCell → next $nextCell")
            } else {
                logError("CLOCKWISE FAIL: $color start $startCell → next $nextCell is NOT clockwise!")
            }
        }
    }

    // Safe logging that works in both Android and unit tests
    private fun log(message: String) {
        try {
            android.util.Log.d("LudoBoard", message)
        } catch (e: Throwable) {
            println("[LudoBoard] $message")
        }
    }

    private fun logError(message: String) {
        try {
            android.util.Log.e("LudoBoard", message)
        } catch (e: Throwable) {
            System.err.println("[LudoBoard ERROR] $message")
        }
    }

    /**
     * Get information about a cell for debug overlay.
     * Returns a string describing the cell type.
     */
    fun getCellDebugInfo(row: Int, col: Int): CellDebugInfo {
        val cell = Pair(row, col)
        val ringIndex = RING_CELLS.indexOf(cell)

        // Check if it's a start cell
        val startColor = START_CELL_BY_COLOR.entries.find { it.value == cell }?.key

        // Check if it's a lane cell
        var laneInfo: Pair<LudoColor, Int>? = null
        LudoColor.entries.forEach { color ->
            val laneCells = LANE_CELLS_BY_COLOR[color] ?: return@forEach
            val laneIndex = laneCells.indexOf(cell)
            if (laneIndex >= 0) {
                laneInfo = color to laneIndex
            }
        }

        // Check if it's a home slot
        var homeInfo: Pair<LudoColor, Int>? = null
        LudoColor.entries.forEach { color ->
            val homeSlots = HOME_SLOTS_BY_COLOR[color] ?: return@forEach
            val slotIndex = homeSlots.indexOf(cell)
            if (slotIndex >= 0) {
                homeInfo = color to slotIndex
            }
        }

        // Check if it's the center
        val isCenter = cell == CENTER_CELL

        // Check if it's a safe cell
        val isSafe = ringIndex >= 0 && ringIndex in SAFE_INDICES

        return CellDebugInfo(
            row = row,
            col = col,
            ringIndex = if (ringIndex >= 0) ringIndex else null,
            startColor = startColor,
            laneInfo = laneInfo,
            homeInfo = homeInfo,
            isCenter = isCenter,
            isSafe = isSafe
        )
    }

    /**
     * Debug information about a board cell.
     */
    data class CellDebugInfo(
        val row: Int,
        val col: Int,
        val ringIndex: Int?,
        val startColor: LudoColor?,
        val laneInfo: Pair<LudoColor, Int>?,
        val homeInfo: Pair<LudoColor, Int>?,
        val isCenter: Boolean,
        val isSafe: Boolean
    ) {
        fun toDisplayString(): String {
            val parts = mutableListOf<String>()
            parts.add("($row, $col)")

            ringIndex?.let { parts.add("Ring[$it]") }
            startColor?.let { parts.add("${it.name} START") }
            laneInfo?.let { (color, idx) -> parts.add("${color.name} Lane[$idx]") }
            homeInfo?.let { (color, idx) -> parts.add("${color.name} Home[$idx]") }
            if (isCenter) parts.add("CENTER")
            if (isSafe) parts.add("SAFE")

            return parts.joinToString(" | ")
        }

        fun toLogString(): String {
            return "Cell($row,$col) ringIndex=$ringIndex startFor=$startColor " +
                   "lane=$laneInfo home=$homeInfo center=$isCenter safe=$isSafe"
        }
    }

    // ==================== LANE ENTRY ====================

    /**
     * Lane entry ring index by color.
     * After passing this index, the token enters its finish lane instead of continuing on ring.
     *
     * COMPUTED: Each color enters their lane at (startIndex - 1 + 52) % 52,
     * i.e., one step before their own start position.
     */
    val LANE_ENTRY_INDEX_BY_COLOR: Map<LudoColor, Int> by lazy {
        LudoColor.entries.associateWith { color ->
            val startIndex = START_INDEX_BY_COLOR[color] ?: 0
            (startIndex - 1 + RING_SIZE) % RING_SIZE
        }.also { entries ->
            log("Lane entries: $entries")
        }
    }

    // ==================== FINISH LANES ====================

    /**
     * Finish lane cells by color (6 cells each).
     * Index 0 = entry (just entered lane), Index 5 = final cell before center.
     */
    val LANE_CELLS_BY_COLOR: Map<LudoColor, List<Pair<Int, Int>>> = mapOf(
        // GREEN lane: horizontal, going RIGHT towards center (row 7, cols 1→6)
        LudoColor.GREEN to listOf(
            Pair(7, 1), Pair(7, 2), Pair(7, 3), Pair(7, 4), Pair(7, 5), Pair(7, 6)
        ),
        // YELLOW lane: vertical, going DOWN towards center (col 7, rows 1→6)
        LudoColor.YELLOW to listOf(
            Pair(1, 7), Pair(2, 7), Pair(3, 7), Pair(4, 7), Pair(5, 7), Pair(6, 7)
        ),
        // BLUE lane: horizontal, going LEFT towards center (row 7, cols 13→8)
        LudoColor.BLUE to listOf(
            Pair(7, 13), Pair(7, 12), Pair(7, 11), Pair(7, 10), Pair(7, 9), Pair(7, 8)
        ),
        // RED lane: vertical, going UP towards center (col 7, rows 13→8)
        LudoColor.RED to listOf(
            Pair(13, 7), Pair(12, 7), Pair(11, 7), Pair(10, 7), Pair(9, 7), Pair(8, 7)
        )
    )

    // ==================== SAFE CELLS ====================

    /**
     * Safe ring indices where tokens cannot be captured.
     * COMPUTED: Start positions + star cells (8 positions after each start).
     */
    val SAFE_INDICES: Set<Int> by lazy {
        val indices = mutableSetOf<Int>()
        LudoColor.entries.forEach { color ->
            val startIndex = START_INDEX_BY_COLOR[color] ?: return@forEach
            indices.add(startIndex) // Start position is safe
            indices.add((startIndex + 8) % RING_SIZE) // Star 8 cells after start
        }
        log("Safe indices: $indices")
        indices
    }

    /**
     * Safe cell coordinates for UI rendering.
     */
    val SAFE_CELLS: Set<Pair<Int, Int>> by lazy {
        SAFE_INDICES.mapNotNull { getRingCell(it) }.toSet()
    }

    // ==================== HOME SLOTS ====================

    /**
     * Home slot positions for each color (4 slots per color).
     * These are where tokens visually sit when in HOME state.
     */
    val HOME_SLOTS_BY_COLOR: Map<LudoColor, List<Pair<Int, Int>>> = mapOf(
        // GREEN home: top-left quadrant (rows 0-5, cols 0-5)
        LudoColor.GREEN to listOf(
            Pair(1, 1), Pair(1, 4), Pair(4, 1), Pair(4, 4)
        ),
        // YELLOW home: top-right quadrant (rows 0-5, cols 9-14)
        LudoColor.YELLOW to listOf(
            Pair(1, 10), Pair(1, 13), Pair(4, 10), Pair(4, 13)
        ),
        // RED home: bottom-left quadrant (rows 9-14, cols 0-5)
        LudoColor.RED to listOf(
            Pair(10, 1), Pair(10, 4), Pair(13, 1), Pair(13, 4)
        ),
        // BLUE home: bottom-right quadrant (rows 9-14, cols 9-14)
        LudoColor.BLUE to listOf(
            Pair(10, 10), Pair(10, 13), Pair(13, 10), Pair(13, 13)
        )
    )

    // ==================== CENTER (FINISH) ====================

    /** Center cell coordinate (finish destination) */
    val CENTER_CELL: Pair<Int, Int> = Pair(7, 7)

    // ==================== HELPER FUNCTIONS ====================

    /**
     * Get the start ring index for a color.
     */
    fun getStartIndex(color: LudoColor): Int {
        return START_INDEX_BY_COLOR[color] ?: 0
    }

    /**
     * Get the start cell coordinates for a color.
     */
    fun getStartCell(color: LudoColor): Pair<Int, Int> {
        return START_CELL_BY_COLOR[color] ?: Pair(0, 0)
    }

    /**
     * Get the (row, col) coordinate for a ring index.
     */
    fun getRingCell(ringIndex: Int): Pair<Int, Int>? {
        val normalizedIndex = ((ringIndex % RING_SIZE) + RING_SIZE) % RING_SIZE
        return RING_CELLS.getOrNull(normalizedIndex)
    }

    /**
     * Get the (row, col) coordinate for a lane position.
     * @param laneIndex 0-5 (0 = entry, 5 = last cell before center)
     */
    fun getLaneCell(color: LudoColor, laneIndex: Int): Pair<Int, Int>? {
        return LANE_CELLS_BY_COLOR[color]?.getOrNull(laneIndex)
    }

    /**
     * Get home slot coordinate for a token.
     * @param slotIndex 0-3
     */
    fun getHomeSlot(color: LudoColor, slotIndex: Int): Pair<Int, Int>? {
        return HOME_SLOTS_BY_COLOR[color]?.getOrNull(slotIndex)
    }

    /**
     * Check if a ring index is a safe cell.
     */
    fun isSafeCell(ringIndex: Int): Boolean {
        val normalizedIndex = ((ringIndex % RING_SIZE) + RING_SIZE) % RING_SIZE
        return normalizedIndex in SAFE_INDICES
    }

    /**
     * Check if a token at the given ring index should enter its finish lane.
     */
    fun shouldEnterLane(ringIndex: Int, color: LudoColor): Boolean {
        return ringIndex == LANE_ENTRY_INDEX_BY_COLOR[color]
    }

    /**
     * Convert a player's relative position to absolute ring index.
     * @param relativePosition Player's position (0-51 on ring)
     * @param color Player's color (determines starting offset)
     * @return Absolute index in RING_CELLS (0-51), or -1 if in lane/finished
     */
    fun toAbsolutePosition(relativePosition: Int, color: LudoColor): Int {
        if (relativePosition > RING_END || relativePosition < 0) {
            return -1
        }
        val startOffset = getStartIndex(color)
        return (relativePosition + startOffset) % RING_SIZE
    }

    /**
     * Check if a relative position is in the finish lane.
     */
    fun isInLane(relativePosition: Int): Boolean {
        return relativePosition in LANE_START until FINISH_POSITION
    }

    /**
     * Check if a relative position is on the main ring.
     */
    fun isOnRing(relativePosition: Int): Boolean {
        return relativePosition in 0..RING_END
    }

    /**
     * Calculate steps remaining to reach finish.
     */
    fun distanceToFinish(relativePosition: Int): Int {
        return when {
            relativePosition == HOME_POSITION -> FINISH_POSITION + 1
            relativePosition >= FINISH_POSITION -> 0
            else -> FINISH_POSITION - relativePosition
        }
    }

    // ==================== LEGACY ALIASES ====================

    @Deprecated("Use getStartIndex", ReplaceWith("getStartIndex(color)"))
    fun getStartPosition(color: LudoColor): Int = getStartIndex(color)

    @Deprecated("Use isInLane", ReplaceWith("isInLane(pos)"))
    fun isInHomeStretch(pos: Int): Boolean = isInLane(pos)

    @Deprecated("Use isOnRing", ReplaceWith("isOnRing(pos)"))
    fun isOnMainTrack(pos: Int): Boolean = isOnRing(pos)

    // Legacy constants
    @Deprecated("Use RING_SIZE") const val MAIN_TRACK_SIZE = RING_SIZE
    @Deprecated("Use LANE_SIZE") const val HOME_STRETCH_SIZE = LANE_SIZE
    @Deprecated("Use LANE_START") const val HOME_STRETCH_START = LANE_START
    @Deprecated("Use RING_END") const val MAIN_TRACK_END = RING_END
}
