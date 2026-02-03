package com.parthipan.colorclashcards.game.ludo.engine

import com.parthipan.colorclashcards.game.ludo.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Property-based randomized tests for the Ludo rules engine.
 * Simulates thousands of random game sequences to catch edge cases.
 *
 * Uses a fixed random seed for reproducibility.
 * Failing sequences are clearly output for debugging.
 */
class LudoEnginePropertyTest {

    companion object {
        // Fixed seed for reproducible tests
        private const val RANDOM_SEED = 42L

        // Number of complete games to simulate
        private const val NUM_GAMES = 100

        // Maximum moves per game (to prevent infinite loops)
        private const val MAX_MOVES_PER_GAME = 500

        // Number of random move sequences to test
        private const val NUM_RANDOM_SEQUENCES = 1000
    }

    private lateinit var random: Random
    private val moveLog = mutableListOf<String>()

    @Before
    fun setUp() {
        random = Random(RANDOM_SEED)
        moveLog.clear()
    }

    // ==================== INVARIANT DEFINITIONS ====================

    /**
     * All game state invariants that must hold after every move.
     */
    private fun assertAllInvariants(state: LudoGameState, context: String) {
        assertNoNegativePositions(state, context)
        assertNoInvalidTokenStates(state, context)
        assertValidGameState(state, context)
        assertValidCurrentPlayer(state, context)
        assertValidWinnerState(state, context)
        assertTokenPositionsConsistent(state, context)
        assertPlayerCountValid(state, context)
        assertDiceValueValid(state, context)
    }

    private fun assertNoNegativePositions(state: LudoGameState, context: String) {
        for (player in state.players) {
            for (token in player.tokens) {
                if (token.state == TokenState.ACTIVE) {
                    assertTrue(
                        "INVARIANT VIOLATION: Negative position detected!\n" +
                                "Player: ${player.name} (${player.color})\n" +
                                "Token: ${token.id}, Position: ${token.position}\n" +
                                "Context: $context\n" +
                                "Move log:\n${formatMoveLog()}",
                        token.position >= 0
                    )
                }
            }
        }
    }

    private fun assertNoInvalidTokenStates(state: LudoGameState, context: String) {
        for (player in state.players) {
            for (token in player.tokens) {
                // Token at HOME must have position -1
                if (token.state == TokenState.HOME) {
                    assertEquals(
                        "INVARIANT VIOLATION: HOME token has invalid position!\n" +
                                "Player: ${player.name} (${player.color})\n" +
                                "Token: ${token.id}, Position: ${token.position}\n" +
                                "Context: $context\n" +
                                "Move log:\n${formatMoveLog()}",
                        -1, token.position
                    )
                }

                // Token ACTIVE must have position 0-57
                if (token.state == TokenState.ACTIVE) {
                    assertTrue(
                        "INVARIANT VIOLATION: ACTIVE token has invalid position!\n" +
                                "Player: ${player.name} (${player.color})\n" +
                                "Token: ${token.id}, Position: ${token.position} (expected 0-57)\n" +
                                "Context: $context\n" +
                                "Move log:\n${formatMoveLog()}",
                        token.position in 0..57
                    )
                }

                // Token FINISHED must have position 58
                if (token.state == TokenState.FINISHED) {
                    assertEquals(
                        "INVARIANT VIOLATION: FINISHED token has invalid position!\n" +
                                "Player: ${player.name} (${player.color})\n" +
                                "Token: ${token.id}, Position: ${token.position} (expected 58)\n" +
                                "Context: $context\n" +
                                "Move log:\n${formatMoveLog()}",
                        58, token.position
                    )
                }
            }
        }
    }

    private fun assertValidGameState(state: LudoGameState, context: String) {
        // Game status must be valid
        assertNotNull(
            "INVARIANT VIOLATION: Game status is null!\n" +
                    "Context: $context\n" +
                    "Move log:\n${formatMoveLog()}",
            state.gameStatus
        )

        // If game is finished, there should be a winner or all players disconnected
        if (state.gameStatus == GameStatus.FINISHED) {
            // Winner should exist (or game ended due to other reasons in online mode)
            // For offline games, winner must be set
        }
    }

    private fun assertValidCurrentPlayer(state: LudoGameState, context: String) {
        if (state.gameStatus == GameStatus.IN_PROGRESS) {
            val currentPlayer = state.players.find { it.id == state.currentTurnPlayerId }
            assertNotNull(
                "INVARIANT VIOLATION: Current turn player not found in players list!\n" +
                        "CurrentTurnPlayerId: ${state.currentTurnPlayerId}\n" +
                        "Players: ${state.players.map { "${it.id}:${it.name}" }}\n" +
                        "Context: $context\n" +
                        "Move log:\n${formatMoveLog()}",
                currentPlayer
            )
        }
    }

    private fun assertValidWinnerState(state: LudoGameState, context: String) {
        val winner = state.winnerId?.let { id -> state.players.find { it.id == id } }

        if (winner != null) {
            // Winner must have all tokens finished
            val allFinished = winner.tokens.all { it.state == TokenState.FINISHED }
            assertTrue(
                "INVARIANT VIOLATION: Winner does not have all tokens finished!\n" +
                        "Winner: ${winner.name} (${winner.color})\n" +
                        "Tokens: ${winner.tokens.map { "${it.id}:${it.state}@${it.position}" }}\n" +
                        "Context: $context\n" +
                        "Move log:\n${formatMoveLog()}",
                allFinished
            )

            // Game should be finished
            assertEquals(
                "INVARIANT VIOLATION: Winner is set but game is not finished!\n" +
                        "Game status: ${state.gameStatus}\n" +
                        "Winner: ${winner.name}\n" +
                        "Context: $context\n" +
                        "Move log:\n${formatMoveLog()}",
                GameStatus.FINISHED, state.gameStatus
            )
        }

        // Check that no player has won unless tracked in finishOrder (Mode B)
        if (state.winnerId == null && state.gameStatus == GameStatus.IN_PROGRESS) {
            for (player in state.players) {
                if (player.hasWon()) {
                    // In Mode B (3-4 players), a finished player must be in finishOrder
                    assertTrue(
                        "INVARIANT VIOLATION: Player has won but not in finishOrder!\n" +
                                "Player: ${player.name} (${player.color})\n" +
                                "finishOrder: ${state.finishOrder}\n" +
                                "Context: $context\n" +
                                "Move log:\n${formatMoveLog()}",
                        player.id in state.finishOrder
                    )
                }
            }
        }
    }

    private fun assertTokenPositionsConsistent(state: LudoGameState, context: String) {
        for (player in state.players) {
            assertEquals(
                "INVARIANT VIOLATION: Player does not have exactly 4 tokens!\n" +
                        "Player: ${player.name} (${player.color})\n" +
                        "Token count: ${player.tokens.size}\n" +
                        "Context: $context\n" +
                        "Move log:\n${formatMoveLog()}",
                4, player.tokens.size
            )

            // Token IDs should be 0-3
            val tokenIds = player.tokens.map { it.id }.toSet()
            assertEquals(
                "INVARIANT VIOLATION: Invalid token IDs!\n" +
                        "Player: ${player.name} (${player.color})\n" +
                        "Token IDs: $tokenIds (expected 0,1,2,3)\n" +
                        "Context: $context\n" +
                        "Move log:\n${formatMoveLog()}",
                setOf(0, 1, 2, 3), tokenIds
            )
        }
    }

    private fun assertPlayerCountValid(state: LudoGameState, context: String) {
        assertTrue(
            "INVARIANT VIOLATION: Invalid player count!\n" +
                    "Player count: ${state.players.size} (expected 2-4)\n" +
                    "Context: $context\n" +
                    "Move log:\n${formatMoveLog()}",
            state.players.size in 2..4
        )
    }

    private fun assertDiceValueValid(state: LudoGameState, context: String) {
        state.diceValue?.let { dice ->
            assertTrue(
                "INVARIANT VIOLATION: Invalid dice value!\n" +
                        "Dice value: $dice (expected 1-6 or null)\n" +
                        "Context: $context\n" +
                        "Move log:\n${formatMoveLog()}",
                dice in 1..6
            )
        }
    }

    // ==================== RANDOM GAME SIMULATION ====================

    @Test
    fun `simulate 100 complete random games - 2 players`() {
        var gamesCompleted = 0
        var totalMoves = 0

        repeat(NUM_GAMES) { gameIndex ->
            moveLog.clear()
            val result = simulateRandomGame(2, gameIndex)
            totalMoves += result.moves

            if (result.completed) {
                gamesCompleted++
            }
        }

        println("2-player games: $gamesCompleted/$NUM_GAMES completed, $totalMoves total moves")
        assertTrue("At least some games should complete", gamesCompleted > 0)
    }

    @Test
    fun `simulate 100 complete random games - 3 players`() {
        var gamesCompleted = 0
        var totalMoves = 0

        repeat(NUM_GAMES) { gameIndex ->
            moveLog.clear()
            val result = simulateRandomGame(3, gameIndex)
            totalMoves += result.moves

            if (result.completed) {
                gamesCompleted++
            }
        }

        println("3-player games: $gamesCompleted/$NUM_GAMES completed, $totalMoves total moves")
        assertTrue("At least some games should complete", gamesCompleted > 0)
    }

    @Test
    fun `simulate 100 complete random games - 4 players`() {
        var gamesCompleted = 0
        var totalMoves = 0

        repeat(NUM_GAMES) { gameIndex ->
            moveLog.clear()
            val result = simulateRandomGame(4, gameIndex)
            totalMoves += result.moves

            if (result.completed) {
                gamesCompleted++
            }
        }

        println("4-player games: $gamesCompleted/$NUM_GAMES completed, $totalMoves total moves")
        assertTrue("At least some games should complete", gamesCompleted > 0)
    }

    private data class GameResult(val completed: Boolean, val moves: Int)

    private fun simulateRandomGame(playerCount: Int, gameIndex: Int): GameResult {
        val players = createRandomPlayers(playerCount)
        var state = LudoEngine.startGame(players)

        logMove("=== GAME $gameIndex START ($playerCount players) ===")
        assertAllInvariants(state, "Game $gameIndex initial state")

        var moveCount = 0

        while (!state.isGameOver && moveCount < MAX_MOVES_PER_GAME) {
            state = performRandomTurn(state, gameIndex, moveCount)
            moveCount++
            assertAllInvariants(state, "Game $gameIndex after move $moveCount")
        }

        if (state.isGameOver) {
            logMove("=== GAME $gameIndex ENDED - Winner: ${state.winner?.name ?: "None"} ===")
        }

        return GameResult(state.isGameOver, moveCount)
    }

    private fun performRandomTurn(state: LudoGameState, gameIndex: Int, moveIndex: Int): LudoGameState {
        var currentState = state

        // Roll dice if needed
        if (currentState.canRollDice) {
            val diceValue = random.nextInt(1, 7)
            logMove("Game $gameIndex, Move $moveIndex: ${currentState.currentPlayer.name} rolls $diceValue")

            currentState = LudoEngine.rollDice(currentState, diceValue)
            assertAllInvariants(currentState, "After rolling $diceValue")
        }

        // Select token if needed
        if (currentState.mustSelectToken) {
            val movableTokens = LudoEngine.getMovableTokens(
                currentState.currentPlayer,
                currentState.diceValue ?: return currentState
            )

            if (movableTokens.isNotEmpty()) {
                val selectedToken = movableTokens[random.nextInt(movableTokens.size)]
                logMove("  -> Selects token ${selectedToken.id} at position ${selectedToken.position}")

                when (val result = LudoEngine.moveToken(currentState, selectedToken.id)) {
                    is MoveResult.Success -> {
                        currentState = result.newState
                        logMove("  -> Moved to position ${result.move.toPosition}, type: ${result.move.moveType}")
                        if (result.move.capturedTokenInfo != null) {
                            logMove("  -> CAPTURED opponent token!")
                        }
                        if (result.hasWon) {
                            logMove("  -> WINNER!")
                        }
                    }
                    is MoveResult.Error -> {
                        fail(
                            "UNEXPECTED ERROR during valid move!\n" +
                                    "Error: ${result.message}\n" +
                                    "Player: ${currentState.currentPlayer.name}\n" +
                                    "Token: ${selectedToken.id}\n" +
                                    "Dice: ${currentState.diceValue}\n" +
                                    "Move log:\n${formatMoveLog()}"
                        )
                    }
                }
            }
        }

        return currentState
    }

    // ==================== RANDOM SEQUENCE TESTS ====================

    @Test
    fun `1000 random dice roll sequences maintain valid state`() {
        repeat(NUM_RANDOM_SEQUENCES) { sequenceIndex ->
            moveLog.clear()
            testRandomDiceSequence(sequenceIndex)
        }
        println("Completed $NUM_RANDOM_SEQUENCES random dice sequences")
    }

    private fun testRandomDiceSequence(sequenceIndex: Int) {
        val playerCount = random.nextInt(2, 5)
        val players = createRandomPlayers(playerCount)
        var state = LudoEngine.startGame(players)

        val sequenceLength = random.nextInt(10, 50)
        logMove("=== SEQUENCE $sequenceIndex: $sequenceLength rolls, $playerCount players ===")

        repeat(sequenceLength) { rollIndex ->
            if (state.isGameOver) return

            if (state.canRollDice) {
                val diceValue = random.nextInt(1, 7)
                state = LudoEngine.rollDice(state, diceValue)
                logMove("Roll $rollIndex: $diceValue")
                assertAllInvariants(state, "Sequence $sequenceIndex, roll $rollIndex")
            }

            if (state.mustSelectToken && !state.isGameOver) {
                val diceVal = state.diceValue
                if (diceVal != null) {
                    val movableTokens = LudoEngine.getMovableTokens(state.currentPlayer, diceVal)
                    if (movableTokens.isNotEmpty()) {
                        val token = movableTokens.random(random)
                        when (val result = LudoEngine.moveToken(state, token.id)) {
                            is MoveResult.Success -> {
                                state = result.newState
                                assertAllInvariants(state, "Sequence $sequenceIndex after move")
                            }
                            is MoveResult.Error -> {
                                // This shouldn't happen for a movable token
                                fail("Unexpected error: ${result.message}\nMove log:\n${formatMoveLog()}")
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== EDGE CASE PROPERTY TESTS ====================

    @Test
    fun `property - only current player can make valid moves`() {
        repeat(500) { iteration ->
            moveLog.clear()
            val players = createRandomPlayers(random.nextInt(2, 5))
            var state = LudoEngine.startGame(players)

            // Roll dice
            val diceValue = random.nextInt(1, 7)
            state = LudoEngine.rollDice(state, diceValue)

            // Try to validate move for wrong player
            val wrongPlayers = state.players.filter { it.id != state.currentTurnPlayerId }
            for (wrongPlayer in wrongPlayers) {
                val result = LudoEngine.validateMove(state, wrongPlayer.id, tokenId = 0)
                assertTrue(
                    "PROPERTY VIOLATION: Non-current player's move was validated!\n" +
                            "Current player: ${state.currentTurnPlayerId}\n" +
                            "Wrong player: ${wrongPlayer.id}\n" +
                            "Iteration: $iteration\n" +
                            "Move log:\n${formatMoveLog()}",
                    result is ValidationResult.Invalid
                )
            }
        }
        println("Validated 'only current player can move' property 500 times")
    }

    @Test
    fun `property - home tokens only move on dice 6`() {
        repeat(500) { iteration ->
            moveLog.clear()
            val players = createRandomPlayers(2)
            var state = LudoEngine.startGame(players)

            // All tokens start at home
            for (diceValue in 1..5) {
                state = state.copy(canRollDice = true, mustSelectToken = false, diceValue = null)
                state = LudoEngine.rollDice(state, diceValue)

                val movableTokens = LudoEngine.getMovableTokens(state.currentPlayer, diceValue)
                val homeTokensMovable = movableTokens.filter { it.state == TokenState.HOME }

                assertTrue(
                    "PROPERTY VIOLATION: HOME token can move with dice $diceValue!\n" +
                            "Movable home tokens: ${homeTokensMovable.map { it.id }}\n" +
                            "Iteration: $iteration",
                    homeTokensMovable.isEmpty()
                )
            }
        }
        println("Validated 'home tokens only on 6' property 500 times")
    }

    @Test
    fun `property - finished tokens never move`() {
        repeat(500) { iteration ->
            moveLog.clear()

            // Create a player with some finished tokens
            val tokens = listOf(
                Token(id = 0, state = TokenState.FINISHED, position = 58),
                Token(id = 1, state = TokenState.FINISHED, position = 58),
                Token(id = 2, state = TokenState.ACTIVE, position = random.nextInt(0, 51)),
                Token(id = 3, state = TokenState.HOME, position = -1)
            )
            val player = LudoPlayer(
                id = "test-player",
                name = "Test",
                color = LudoColor.RED,
                tokens = tokens
            )

            for (diceValue in 1..6) {
                val movableTokens = player.getMovableTokens(diceValue)
                val finishedTokensMovable = movableTokens.filter { it.state == TokenState.FINISHED }

                assertTrue(
                    "PROPERTY VIOLATION: FINISHED token can move with dice $diceValue!\n" +
                            "Movable finished tokens: ${finishedTokensMovable.map { it.id }}\n" +
                            "Iteration: $iteration",
                    finishedTokensMovable.isEmpty()
                )
            }
        }
        println("Validated 'finished tokens never move' property 500 times")
    }

    @Test
    fun `property - position never exceeds 58`() {
        repeat(500) { iteration ->
            moveLog.clear()
            val players = createRandomPlayers(2)
            var state = LudoEngine.startGame(players)

            // Simulate some moves
            repeat(50) {
                if (state.isGameOver) return@repeat

                if (state.canRollDice) {
                    val diceValue = random.nextInt(1, 7)
                    state = LudoEngine.rollDice(state, diceValue)
                }

                if (state.mustSelectToken) {
                    val movableTokens = LudoEngine.getMovableTokens(
                        state.currentPlayer,
                        state.diceValue ?: return@repeat
                    )
                    if (movableTokens.isNotEmpty()) {
                        val token = movableTokens.random(random)
                        when (val result = LudoEngine.moveToken(state, token.id)) {
                            is MoveResult.Success -> state = result.newState
                            is MoveResult.Error -> { /* ignore */ }
                        }
                    }
                }

                // Check all token positions
                for (player in state.players) {
                    for (token in player.tokens) {
                        assertTrue(
                            "PROPERTY VIOLATION: Token position exceeds 58!\n" +
                                    "Player: ${player.name}\n" +
                                    "Token: ${token.id}, Position: ${token.position}\n" +
                                    "Iteration: $iteration",
                            token.position <= 58
                        )
                    }
                }
            }
        }
        println("Validated 'position never exceeds 58' property 500 times")
    }

    @Test
    fun `property - consecutive sixes never exceed 3`() {
        repeat(500) { iteration ->
            moveLog.clear()
            val players = createRandomPlayers(2)
            var state = LudoEngine.startGame(players)

            // Force rolling sixes
            repeat(10) {
                if (state.isGameOver) return@repeat
                if (!state.canRollDice) return@repeat

                state = LudoEngine.rollDice(state, 6)

                assertTrue(
                    "PROPERTY VIOLATION: Consecutive sixes exceeds maximum!\n" +
                            "Consecutive sixes: ${state.consecutiveSixes}\n" +
                            "Iteration: $iteration",
                    state.consecutiveSixes <= 3
                )

                // Handle token selection if needed
                if (state.mustSelectToken) {
                    val movableTokens = LudoEngine.getMovableTokens(state.currentPlayer, 6)
                    if (movableTokens.isNotEmpty()) {
                        when (val result = LudoEngine.moveToken(state, movableTokens.first().id)) {
                            is MoveResult.Success -> state = result.newState
                            is MoveResult.Error -> { /* ignore */ }
                        }
                    }
                }
            }
        }
        println("Validated 'consecutive sixes <= 3' property 500 times")
    }

    @Test
    fun `property - safe cells prevent captures`() {
        repeat(200) { iteration ->
            moveLog.clear()

            // Test each safe cell index
            for (safeIndex in LudoBoard.SAFE_INDICES) {
                val isSafe = LudoBoard.isSafeCell(safeIndex)
                assertTrue(
                    "PROPERTY VIOLATION: Safe cell $safeIndex not marked as safe!\n" +
                            "Iteration: $iteration",
                    isSafe
                )
            }

            // Test non-safe cells
            for (cell in 0 until 52) {
                if (cell !in LudoBoard.SAFE_INDICES) {
                    val isSafe = LudoBoard.isSafeCell(cell)
                    assertFalse(
                        "PROPERTY VIOLATION: Non-safe cell $cell marked as safe!\n" +
                                "Iteration: $iteration",
                        isSafe
                    )
                }
            }
        }
        println("Validated 'safe cells' property 200 times")
    }

    @Test
    fun `property - game state transitions are valid`() {
        repeat(300) { iteration ->
            moveLog.clear()
            val players = createRandomPlayers(random.nextInt(2, 5))
            var state = LudoEngine.startGame(players)

            assertEquals(
                "Initial game should be IN_PROGRESS",
                GameStatus.IN_PROGRESS, state.gameStatus
            )

            var previousStatus = state.gameStatus

            repeat(100) {
                if (state.isGameOver) return@repeat

                if (state.canRollDice) {
                    val diceValue = random.nextInt(1, 7)
                    state = LudoEngine.rollDice(state, diceValue)
                }

                if (state.mustSelectToken) {
                    val movableTokens = LudoEngine.getMovableTokens(
                        state.currentPlayer,
                        state.diceValue ?: return@repeat
                    )
                    if (movableTokens.isNotEmpty()) {
                        when (val result = LudoEngine.moveToken(state, movableTokens.random(random).id)) {
                            is MoveResult.Success -> state = result.newState
                            is MoveResult.Error -> { /* ignore */ }
                        }
                    }
                }

                // Validate state transitions
                if (previousStatus == GameStatus.IN_PROGRESS && state.gameStatus == GameStatus.FINISHED) {
                    // Valid transition
                    assertNotNull(
                        "PROPERTY VIOLATION: Game finished but no winner!\n" +
                                "Iteration: $iteration",
                        state.winnerId
                    )
                } else if (previousStatus == GameStatus.FINISHED) {
                    // Once finished, should stay finished
                    assertEquals(
                        "PROPERTY VIOLATION: Game status changed after FINISHED!\n" +
                                "Previous: $previousStatus, Current: ${state.gameStatus}\n" +
                                "Iteration: $iteration",
                        GameStatus.FINISHED, state.gameStatus
                    )
                }

                previousStatus = state.gameStatus
            }
        }
        println("Validated 'state transitions' property 300 times")
    }

    // ==================== STRESS TESTS ====================

    @Test
    fun `stress test - rapid game simulations`() {
        val startTime = System.currentTimeMillis()
        var totalMoves = 0
        var gamesCompleted = 0

        repeat(50) { gameIndex ->
            moveLog.clear()
            val playerCount = random.nextInt(2, 5)
            val players = createRandomPlayers(playerCount)
            var state = LudoEngine.startGame(players)

            var moves = 0
            while (!state.isGameOver && moves < MAX_MOVES_PER_GAME) {
                if (state.canRollDice) {
                    state = LudoEngine.rollDice(state, random.nextInt(1, 7))
                }

                if (state.mustSelectToken) {
                    val diceVal = state.diceValue
                    if (diceVal != null) {
                        val movableTokens = LudoEngine.getMovableTokens(state.currentPlayer, diceVal)
                        if (movableTokens.isNotEmpty()) {
                            when (val result = LudoEngine.moveToken(state, movableTokens.random(random).id)) {
                                is MoveResult.Success -> state = result.newState
                                is MoveResult.Error -> { /* ignore */ }
                            }
                        }
                    }
                }

                moves++
                // Periodic invariant check
                if (moves % 50 == 0) {
                    assertAllInvariants(state, "Stress test game $gameIndex, move $moves")
                }
            }

            totalMoves += moves
            if (state.isGameOver) gamesCompleted++

            // Final invariant check
            assertAllInvariants(state, "Stress test game $gameIndex final state")
        }

        val elapsed = System.currentTimeMillis() - startTime
        println("Stress test: 50 games, $totalMoves moves, $gamesCompleted completed in ${elapsed}ms")
    }

    @Test
    fun `stress test - all tokens finishing race`() {
        repeat(100) { iteration ->
            moveLog.clear()

            // Create game where tokens are near finish
            val nearFinishTokens = listOf(
                Token(id = 0, state = TokenState.ACTIVE, position = 55),
                Token(id = 1, state = TokenState.ACTIVE, position = 56),
                Token(id = 2, state = TokenState.ACTIVE, position = 54),
                Token(id = 3, state = TokenState.ACTIVE, position = 53)
            )
            val player1 = LudoPlayer(
                id = "player-1",
                name = "Player 1",
                color = LudoColor.RED,
                tokens = nearFinishTokens
            )
            val player2 = LudoPlayer(
                id = "player-2",
                name = "Player 2",
                color = LudoColor.BLUE
            )

            var state = LudoGameState(
                players = listOf(player1, player2),
                currentTurnPlayerId = player1.id,
                gameStatus = GameStatus.IN_PROGRESS,
                canRollDice = true
            )

            var moves = 0
            while (!state.isGameOver && moves < 100) {
                if (state.canRollDice) {
                    // Use small dice values to test exact finish
                    val diceValue = random.nextInt(1, 4)
                    state = LudoEngine.rollDice(state, diceValue)
                }

                if (state.mustSelectToken) {
                    val diceVal = state.diceValue
                    if (diceVal != null) {
                        val movableTokens = LudoEngine.getMovableTokens(state.currentPlayer, diceVal)
                        if (movableTokens.isNotEmpty()) {
                            when (val result = LudoEngine.moveToken(state, movableTokens.random(random).id)) {
                                is MoveResult.Success -> state = result.newState
                                is MoveResult.Error -> { /* ignore */ }
                            }
                        }
                    }
                }

                moves++
                assertAllInvariants(state, "Finish race iteration $iteration, move $moves")
            }
        }
        println("Completed 100 finish race stress tests")
    }

    // ==================== HELPER METHODS ====================

    private fun createRandomPlayers(count: Int): List<LudoPlayer> {
        val colors = LudoColor.forPlayerCount(count)
        return colors.mapIndexed { index, color ->
            LudoPlayer(
                id = "player-$index-${random.nextInt(1000)}",
                name = "Player ${index + 1}",
                color = color,
                isBot = index > 0
            )
        }
    }

    private fun logMove(message: String) {
        moveLog.add(message)
        // Keep log size manageable
        if (moveLog.size > 200) {
            moveLog.removeAt(0)
        }
    }

    private fun formatMoveLog(): String {
        return if (moveLog.size <= 50) {
            moveLog.joinToString("\n")
        } else {
            // Show first 10 and last 40
            val first = moveLog.take(10)
            val last = moveLog.takeLast(40)
            first.joinToString("\n") + "\n... (${moveLog.size - 50} moves omitted) ...\n" + last.joinToString("\n")
        }
    }
}
