package com.parthipan.colorclashcards.firebase

import kotlinx.coroutines.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Load test harness for simulating 100 concurrent Ludo games.
 *
 * This test simulates Firebase operations with mocked events to measure:
 * - Expected reads/writes per match
 * - Operation latencies
 * - Concurrency behavior
 * - Cost estimation
 *
 * For real Firebase testing, use FirebaseEmulatorLoadTest with the emulator.
 *
 * Run: ./gradlew :app:testDebugUnitTest --tests "*.FirebaseLoadTest"
 */
class FirebaseLoadTest {

    companion object {
        private const val CONCURRENT_GAMES = 100
        private const val PLAYERS_PER_GAME = 4
        private const val TURNS_PER_GAME = 50
        private const val HEARTBEAT_INTERVAL_TURNS = 5

        // Simulated latencies (ms)
        private const val WRITE_LATENCY_MIN = 20L
        private const val WRITE_LATENCY_MAX = 100L
        private const val READ_LATENCY_MIN = 10L
        private const val READ_LATENCY_MAX = 50L
        private const val TRANSACTION_LATENCY_MIN = 50L
        private const val TRANSACTION_LATENCY_MAX = 200L
    }

    private lateinit var mockFirestore: MockFirestore
    private lateinit var metrics: LoadTestMetrics

    @Before
    fun setup() {
        mockFirestore = MockFirestore()
        metrics = LoadTestMetrics()
    }

    @Test
    fun `simulate 100 concurrent games with mocked events`() = runBlocking {
        println("=" .repeat(70))
        println("FIREBASE LOAD TEST - 100 CONCURRENT GAMES")
        println("=" .repeat(70))
        println()

        val startTime = System.currentTimeMillis()

        // Launch all games concurrently
        val jobs = (1..CONCURRENT_GAMES).map { gameIndex ->
            launch(Dispatchers.Default) {
                simulateGame(gameIndex)
            }
        }

        // Wait for all games to complete
        jobs.joinAll()

        val duration = System.currentTimeMillis() - startTime

        // Print comprehensive results
        printResults(duration)

        // Assertions for CI
        assert(metrics.gamesCompleted.get() >= CONCURRENT_GAMES * 0.95) {
            "Less than 95% of games completed successfully"
        }
    }

    @Test
    fun `measure single game operations breakdown`() = runBlocking {
        println("=" .repeat(70))
        println("SINGLE GAME OPERATIONS BREAKDOWN")
        println("=" .repeat(70))
        println()

        val startTime = System.currentTimeMillis()
        simulateGame(1, verbose = true)
        val duration = System.currentTimeMillis() - startTime

        println()
        println("Single Game Summary:")
        println("-" .repeat(50))
        println("Duration: ${duration}ms")
        printOperationBreakdown()
    }

    @Test
    fun `test optimized vs standard mode`() = runBlocking {
        println("=" .repeat(70))
        println("OPTIMIZATION COMPARISON TEST")
        println("=" .repeat(70))

        // Standard mode
        val standardMetrics = LoadTestMetrics()
        metrics = standardMetrics
        mockFirestore = MockFirestore()

        println("\nRunning STANDARD mode (10 games)...")
        (1..10).map { launch { simulateGame(it, optimized = false) } }.joinAll()

        val standardWrites = standardMetrics.totalWrites.get()
        val standardReads = standardMetrics.totalReads.get()

        // Optimized mode
        val optimizedMetrics = LoadTestMetrics()
        metrics = optimizedMetrics
        mockFirestore = MockFirestore()

        println("Running OPTIMIZED mode (10 games)...")
        (1..10).map { launch { simulateGame(it, optimized = true) } }.joinAll()

        val optimizedWrites = optimizedMetrics.totalWrites.get()
        val optimizedReads = optimizedMetrics.totalReads.get()

        println()
        println("Results Comparison:")
        println("-" .repeat(50))
        println(String.format("%-20s %10s %10s %10s", "Metric", "Standard", "Optimized", "Savings"))
        println("-" .repeat(50))
        println(String.format("%-20s %10d %10d %9.1f%%",
            "Total Writes", standardWrites, optimizedWrites,
            (1 - optimizedWrites.toDouble() / standardWrites) * 100))
        println(String.format("%-20s %10d %10d %9.1f%%",
            "Total Reads", standardReads, optimizedReads,
            (1 - optimizedReads.toDouble() / standardReads) * 100))

        val standardCost = calculateCost(standardReads, standardWrites)
        val optimizedCost = calculateCost(optimizedReads, optimizedWrites)
        println(String.format("%-20s %10s %10s %9.1f%%",
            "Est. Cost", "$${String.format("%.4f", standardCost)}",
            "$${String.format("%.4f", optimizedCost)}",
            (1 - optimizedCost / standardCost) * 100))
    }

    private suspend fun simulateGame(
        gameIndex: Int,
        verbose: Boolean = false,
        optimized: Boolean = false
    ) {
        val roomId = "room_$gameIndex"
        val players = (0 until PLAYERS_PER_GAME).map { playerIndex ->
            SimulatedPlayer(
                id = "player_${gameIndex}_$playerIndex",
                name = "Player $playerIndex",
                colorIndex = playerIndex,
                isHost = playerIndex == 0
            )
        }

        try {
            // Phase 1: Room Creation
            if (verbose) println("\n[Game $gameIndex] Phase 1: Room Creation")
            createRoom(roomId, players[0])

            // Phase 2: Players Join
            if (verbose) println("[Game $gameIndex] Phase 2: Players Joining")
            players.drop(1).forEach { player ->
                joinRoom(roomId, player)
            }

            // Phase 3: Match Initialization
            if (verbose) println("[Game $gameIndex] Phase 3: Match Initialization")
            initializeMatch(roomId, players)

            // Phase 4: Gameplay Loop
            if (verbose) println("[Game $gameIndex] Phase 4: Gameplay ($TURNS_PER_GAME turns)")

            var lastHeartbeatTurn = 0
            repeat(TURNS_PER_GAME) { turn ->
                val currentPlayer = players[turn % PLAYERS_PER_GAME]

                // Roll dice action
                sendAction(roomId, currentPlayer, ActionType.ROLL_DICE)

                // Simulate dice result
                val diceValue = Random.nextInt(1, 7)

                // Move token (if valid move available - 60% chance simulated)
                if (Random.nextFloat() < 0.6) {
                    sendAction(roomId, currentPlayer, ActionType.MOVE_TOKEN, Random.nextInt(4))
                }

                // Update match state (host processes)
                if (optimized) {
                    // Batch state update with action deletion
                    updateMatchStateBatched(roomId, turn, diceValue)
                } else {
                    // Individual operations
                    updateMatchState(roomId, turn, diceValue)
                    deleteProcessedAction(roomId)
                }

                // Heartbeat
                if (optimized) {
                    // Only send heartbeat if no action in last 5 turns
                    if (turn - lastHeartbeatTurn >= HEARTBEAT_INTERVAL_TURNS * 2) {
                        updatePresence(roomId, currentPlayer.id)
                        lastHeartbeatTurn = turn
                    }
                    // Presence is piggybacked on actions in optimized mode
                } else {
                    // Standard: heartbeat every 5 turns per player
                    if (turn % HEARTBEAT_INTERVAL_TURNS == 0) {
                        players.forEach { player ->
                            updatePresence(roomId, player.id)
                        }
                    }
                }

                // Simulate listener updates (reads)
                simulateListenerUpdates(roomId, players, optimized)
            }

            // Phase 5: Game End
            if (verbose) println("[Game $gameIndex] Phase 5: Game End")
            endGame(roomId, players[0].id)

            metrics.gamesCompleted.incrementAndGet()

        } catch (e: Exception) {
            metrics.gamesFailed.incrementAndGet()
            metrics.errors.add("Game $gameIndex: ${e.message}")
        }
    }

    // Simulated Firebase Operations

    private suspend fun createRoom(roomId: String, host: SimulatedPlayer) {
        metrics.recordWrite("room_create") {
            mockFirestore.write("ludoRooms/$roomId", generateRoomData(host))
        }
    }

    private suspend fun joinRoom(roomId: String, player: SimulatedPlayer) {
        metrics.recordTransaction("player_join") {
            mockFirestore.transaction {
                read("ludoRooms/$roomId")
                write("ludoRooms/$roomId", mapOf("players" to "updated"))
            }
        }
    }

    private suspend fun initializeMatch(roomId: String, players: List<SimulatedPlayer>) {
        metrics.recordBatch("match_init", writeCount = 1 + players.size) {
            mockFirestore.batch {
                write("ludoRooms/$roomId/match/state", generateInitialState(players))
                players.forEach { player ->
                    write("ludoRooms/$roomId/presence/${player.id}", generatePresence(player))
                }
                write("ludoRooms/$roomId", mapOf("status" to "PLAYING"))
            }
        }
    }

    private suspend fun sendAction(
        roomId: String,
        player: SimulatedPlayer,
        type: ActionType,
        tokenId: Int? = null
    ) {
        val actionId = "action_${System.nanoTime()}"
        metrics.recordWrite("action_${type.name.lowercase()}") {
            mockFirestore.write(
                "ludoRooms/$roomId/actions/$actionId",
                mapOf(
                    "playerId" to player.id,
                    "type" to type.name,
                    "tokenId" to tokenId,
                    "createdAt" to System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun updateMatchState(roomId: String, turn: Int, diceValue: Int) {
        metrics.recordWrite("state_update") {
            mockFirestore.write(
                "ludoRooms/$roomId/match/state",
                mapOf(
                    "currentTurnIndex" to turn % 4,
                    "diceValue" to diceValue,
                    "lastActionAt" to System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun updateMatchStateBatched(roomId: String, turn: Int, diceValue: Int) {
        metrics.recordBatch("state_update_batched", writeCount = 2) {
            mockFirestore.batch {
                write("ludoRooms/$roomId/match/state", mapOf(
                    "currentTurnIndex" to turn % 4,
                    "diceValue" to diceValue
                ))
                // Delete action in same batch
                delete("ludoRooms/$roomId/actions/latest")
            }
        }
    }

    private suspend fun deleteProcessedAction(roomId: String) {
        metrics.recordDelete("action_delete") {
            mockFirestore.delete("ludoRooms/$roomId/actions/processed")
        }
    }

    private suspend fun updatePresence(roomId: String, playerId: String) {
        metrics.recordWrite("heartbeat") {
            mockFirestore.write(
                "ludoRooms/$roomId/presence/$playerId",
                mapOf("lastSeenAt" to System.currentTimeMillis())
            )
        }
    }

    private suspend fun simulateListenerUpdates(
        roomId: String,
        players: List<SimulatedPlayer>,
        optimized: Boolean
    ) {
        // Each player receives state update
        val stateReads = if (optimized) 1 else players.size  // Optimized: dedupe
        repeat(stateReads) {
            metrics.recordRead("listener_state")
        }

        // Presence updates (less frequent in optimized mode)
        if (!optimized || Random.nextFloat() < 0.3) {
            repeat(players.size) {
                metrics.recordRead("listener_presence")
            }
        }

        // Host receives action (1 read)
        metrics.recordRead("listener_action")
    }

    private suspend fun endGame(roomId: String, winnerId: String) {
        metrics.recordBatch("game_end", writeCount = 2) {
            mockFirestore.batch {
                write("ludoRooms/$roomId", mapOf("status" to "ENDED"))
                write("ludoRooms/$roomId/match/state", mapOf(
                    "gameStatus" to "FINISHED",
                    "winnerId" to winnerId
                ))
            }
        }
    }

    // Data Generators

    private fun generateRoomData(host: SimulatedPlayer): Map<String, Any> = mapOf(
        "hostId" to host.id,
        "hostName" to host.name,
        "roomCode" to (1..6).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString(""),
        "status" to "WAITING",
        "maxPlayers" to 4,
        "players" to listOf(host.toMap()),
        "createdAt" to System.currentTimeMillis()
    )

    private fun generateInitialState(players: List<SimulatedPlayer>): Map<String, Any?> = mapOf(
        "currentTurnIndex" to 0,
        "diceValue" to null,
        "canRollDice" to true,
        "mustSelectToken" to false,
        "consecutiveSixes" to 0,
        "gameStatus" to "IN_PROGRESS",
        "players" to players.mapIndexed { index, player ->
            mapOf(
                "id" to player.id,
                "name" to player.name,
                "colorIndex" to index,
                "tokens" to (0..3).map { mapOf("id" to it, "state" to "HOME", "position" to -1) }
            )
        }
    )

    private fun generatePresence(player: SimulatedPlayer): Map<String, Any> = mapOf(
        "playerId" to player.id,
        "isOnline" to true,
        "lastSeenAt" to System.currentTimeMillis()
    )

    // Results

    private fun printResults(durationMs: Long) {
        println()
        println("=" .repeat(70))
        println("LOAD TEST RESULTS")
        println("=" .repeat(70))
        println()
        println("Test Configuration:")
        println("  Concurrent Games: $CONCURRENT_GAMES")
        println("  Players per Game: $PLAYERS_PER_GAME")
        println("  Turns per Game: $TURNS_PER_GAME")
        println()
        println("Execution Summary:")
        println("  Duration: ${durationMs}ms (${durationMs / 1000}s)")
        println("  Games Completed: ${metrics.gamesCompleted.get()}/$CONCURRENT_GAMES")
        println("  Games Failed: ${metrics.gamesFailed.get()}")
        println()
        printOperationBreakdown()
        printCostEstimation()

        if (metrics.errors.isNotEmpty()) {
            println()
            println("Errors (first 5):")
            metrics.errors.take(5).forEach { println("  - $it") }
        }

        println("=" .repeat(70))
    }

    private fun printOperationBreakdown() {
        println("Operation Breakdown:")
        println("-" .repeat(70))
        println(String.format(
            "%-25s %8s %10s %10s %10s %10s",
            "Operation", "Count", "Total(ms)", "Avg(ms)", "P95(ms)", "P99(ms)"
        ))
        println("-" .repeat(70))

        metrics.operationStats.toList()
            .sortedByDescending { it.second.count.get() }
            .forEach { (name, stats) ->
                println(String.format(
                    "%-25s %8d %10d %10.1f %10.1f %10.1f",
                    name,
                    stats.count.get(),
                    stats.totalDuration.get(),
                    stats.avgMs,
                    stats.p95Ms,
                    stats.p99Ms
                ))
            }

        println("-" .repeat(70))
        println(String.format("%-25s %8d", "TOTAL READS", metrics.totalReads.get()))
        println(String.format("%-25s %8d", "TOTAL WRITES", metrics.totalWrites.get()))
        println(String.format("%-25s %8d", "TOTAL DELETES", metrics.totalDeletes.get()))
        println()

        // Per-game averages
        val completedGames = metrics.gamesCompleted.get().coerceAtLeast(1)
        println("Per-Game Averages:")
        println(String.format("  Reads: %.1f", metrics.totalReads.get().toDouble() / completedGames))
        println(String.format("  Writes: %.1f", metrics.totalWrites.get().toDouble() / completedGames))
        println(String.format("  Deletes: %.1f", metrics.totalDeletes.get().toDouble() / completedGames))
    }

    private fun printCostEstimation() {
        println()
        println("Cost Estimation (Firebase Pricing):")
        println("-" .repeat(70))

        val reads = metrics.totalReads.get()
        val writes = metrics.totalWrites.get()
        val deletes = metrics.totalDeletes.get()
        val completedGames = metrics.gamesCompleted.get().coerceAtLeast(1)

        val readCost = reads * 0.036 / 100_000
        val writeCost = writes * 0.108 / 100_000
        val deleteCost = deletes * 0.012 / 100_000
        val totalCost = readCost + writeCost + deleteCost

        println(String.format("  Reads:   %8d × $0.036/100K = $%.6f", reads, readCost))
        println(String.format("  Writes:  %8d × $0.108/100K = $%.6f", writes, writeCost))
        println(String.format("  Deletes: %8d × $0.012/100K = $%.6f", deletes, deleteCost))
        println(String.format("  TOTAL:                        = $%.6f", totalCost))
        println()
        println(String.format("  Cost per Game: $%.6f", totalCost / completedGames))
        println()
        println("  Monthly Projections:")
        listOf(100, 1000, 10000, 100000).forEach { dailyGames ->
            val monthlyGames = dailyGames * 30
            val monthlyCost = (totalCost / completedGames) * monthlyGames
            println(String.format("    %,6d games/day → $%,.2f/month", dailyGames, monthlyCost))
        }
    }

    private fun calculateCost(reads: Int, writes: Int): Double {
        return reads * 0.036 / 100_000 + writes * 0.108 / 100_000
    }

    // Data Classes

    data class SimulatedPlayer(
        val id: String,
        val name: String,
        val colorIndex: Int,
        val isHost: Boolean = false
    ) {
        fun toMap() = mapOf(
            "id" to id,
            "name" to name,
            "colorIndex" to colorIndex
        )
    }

    enum class ActionType {
        ROLL_DICE,
        MOVE_TOKEN,
        HEARTBEAT
    }

    // Metrics

    class LoadTestMetrics {
        val gamesCompleted = AtomicInteger(0)
        val gamesFailed = AtomicInteger(0)
        val totalReads = AtomicInteger(0)
        val totalWrites = AtomicInteger(0)
        val totalDeletes = AtomicInteger(0)
        val errors = mutableListOf<String>()
        val operationStats = ConcurrentHashMap<String, OperationStats>()

        suspend inline fun recordRead(name: String, block: () -> Unit = {}) {
            val stats = operationStats.getOrPut(name) { OperationStats() }
            val start = System.currentTimeMillis()
            block()
            stats.record(System.currentTimeMillis() - start)
            totalReads.incrementAndGet()
        }

        suspend inline fun recordWrite(name: String, block: suspend () -> Unit) {
            val stats = operationStats.getOrPut(name) { OperationStats() }
            val start = System.currentTimeMillis()
            block()
            stats.record(System.currentTimeMillis() - start)
            totalWrites.incrementAndGet()
        }

        suspend inline fun recordDelete(name: String, block: suspend () -> Unit) {
            val stats = operationStats.getOrPut(name) { OperationStats() }
            val start = System.currentTimeMillis()
            block()
            stats.record(System.currentTimeMillis() - start)
            totalDeletes.incrementAndGet()
        }

        suspend inline fun recordTransaction(name: String, block: suspend () -> Unit) {
            val stats = operationStats.getOrPut(name) { OperationStats() }
            val start = System.currentTimeMillis()
            block()
            stats.record(System.currentTimeMillis() - start)
            // Transaction counts as 1 read + 1 write
            totalReads.incrementAndGet()
            totalWrites.incrementAndGet()
        }

        suspend inline fun recordBatch(name: String, writeCount: Int, block: suspend () -> Unit) {
            val stats = operationStats.getOrPut(name) { OperationStats() }
            val start = System.currentTimeMillis()
            block()
            stats.record(System.currentTimeMillis() - start)
            // Batch counts as single operation but multiple writes
            totalWrites.addAndGet(writeCount)
        }
    }

    class OperationStats {
        val count = AtomicInteger(0)
        val totalDuration = AtomicLong(0)
        private val durations = mutableListOf<Long>()

        val avgMs: Double get() = if (count.get() > 0) totalDuration.get().toDouble() / count.get() else 0.0
        val p95Ms: Double get() = percentile(95)
        val p99Ms: Double get() = percentile(99)

        @Synchronized
        fun record(durationMs: Long) {
            count.incrementAndGet()
            totalDuration.addAndGet(durationMs)
            durations.add(durationMs)
        }

        @Synchronized
        private fun percentile(p: Int): Double {
            if (durations.isEmpty()) return 0.0
            val sorted = durations.sorted()
            val index = ((p / 100.0) * sorted.size).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index].toDouble()
        }
    }

    // Mock Firestore

    class MockFirestore {
        private val data = ConcurrentHashMap<String, Any>()

        suspend fun write(path: String, value: Any) {
            delay(Random.nextLong(WRITE_LATENCY_MIN, WRITE_LATENCY_MAX))
            data[path] = value
        }

        suspend fun read(path: String): Any? {
            delay(Random.nextLong(READ_LATENCY_MIN, READ_LATENCY_MAX))
            return data[path]
        }

        suspend fun delete(path: String) {
            delay(Random.nextLong(WRITE_LATENCY_MIN, WRITE_LATENCY_MAX))
            data.remove(path)
        }

        suspend fun transaction(block: TransactionScope.() -> Unit) {
            delay(Random.nextLong(TRANSACTION_LATENCY_MIN, TRANSACTION_LATENCY_MAX))
            TransactionScope().block()
        }

        suspend fun batch(block: BatchScope.() -> Unit) {
            delay(Random.nextLong(WRITE_LATENCY_MIN, WRITE_LATENCY_MAX))
            BatchScope().block()
        }

        inner class TransactionScope {
            fun read(path: String): Any? = data[path]
            fun write(path: String, value: Any) { data[path] = value }
        }

        inner class BatchScope {
            private val writes = mutableListOf<Pair<String, Any>>()
            private val deletes = mutableListOf<String>()

            fun write(path: String, value: Any) { writes.add(path to value) }
            fun delete(path: String) { deletes.add(path) }
        }
    }
}
