package com.parthipan.colorclashcards.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Load test that runs against a real Firebase Emulator.
 *
 * Prerequisites:
 * 1. Install Firebase CLI: npm install -g firebase-tools
 * 2. Start emulator: firebase emulators:start --only firestore
 * 3. Run test: ./gradlew :app:testDebugUnitTest --tests "*.FirebaseEmulatorLoadTest"
 *
 * Note: Tests are @Ignore by default since they require the emulator to be running.
 * Remove @Ignore when running locally with emulator.
 */
@Ignore("Requires Firebase Emulator to be running")
class FirebaseEmulatorLoadTest {

    companion object {
        private const val EMULATOR_HOST = "10.0.2.2"  // localhost for Android emulator
        private const val EMULATOR_PORT = 8080

        private const val CONCURRENT_GAMES = 100
        private const val PLAYERS_PER_GAME = 4
        private const val TURNS_PER_GAME = 30
    }

    private lateinit var firestore: FirebaseFirestore
    private val metrics = EmulatorTestMetrics()

    @Before
    fun setup() {
        firestore = FirebaseFirestore.getInstance().apply {
            try {
                useEmulator(EMULATOR_HOST, EMULATOR_PORT)
            } catch (e: IllegalStateException) {
                // Already configured
            }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
        }
    }

    @Test
    fun `load test - 100 concurrent games on emulator`() = runBlocking {
        println("Starting Firebase Emulator Load Test")
        println("Games: $CONCURRENT_GAMES, Players: $PLAYERS_PER_GAME, Turns: $TURNS_PER_GAME")
        println()

        val startTime = System.currentTimeMillis()

        // Clean up any previous test data
        cleanupTestData()

        // Launch games in batches to avoid overwhelming the emulator
        val batchSize = 20
        val batches = (1..CONCURRENT_GAMES).chunked(batchSize)

        batches.forEachIndexed { batchIndex, gameIndices ->
            println("Starting batch ${batchIndex + 1}/${batches.size}")

            val jobs = gameIndices.map { gameIndex ->
                launch(Dispatchers.IO) {
                    runGame(gameIndex)
                }
            }
            jobs.joinAll()

            // Brief pause between batches
            delay(500)
        }

        val duration = System.currentTimeMillis() - startTime

        // Print results
        printResults(duration)

        // Cleanup
        cleanupTestData()
    }

    @Test
    fun `latency test - measure operation latencies`() = runBlocking {
        println("Firebase Emulator Latency Test")
        println()

        val roomId = "latency_test_room"
        val iterations = 50

        // Test write latency
        val writeLatencies = mutableListOf<Long>()
        repeat(iterations) {
            val start = System.currentTimeMillis()
            firestore.collection("latencyTest")
                .document("doc_$it")
                .set(mapOf("value" to it, "timestamp" to System.currentTimeMillis()))
                .await()
            writeLatencies.add(System.currentTimeMillis() - start)
        }

        // Test read latency
        val readLatencies = mutableListOf<Long>()
        repeat(iterations) {
            val start = System.currentTimeMillis()
            firestore.collection("latencyTest")
                .document("doc_$it")
                .get()
                .await()
            readLatencies.add(System.currentTimeMillis() - start)
        }

        // Test transaction latency
        val txLatencies = mutableListOf<Long>()
        repeat(iterations) { i ->
            val start = System.currentTimeMillis()
            firestore.runTransaction { transaction ->
                val docRef = firestore.collection("latencyTest").document("tx_$i")
                transaction.get(docRef)
                transaction.set(docRef, mapOf("value" to i))
                null
            }.await()
            txLatencies.add(System.currentTimeMillis() - start)
        }

        // Print results
        println("Latency Results ($iterations iterations each):")
        println("-".repeat(50))
        println(String.format("%-15s %10s %10s %10s %10s",
            "Operation", "Avg(ms)", "Min(ms)", "Max(ms)", "P95(ms)"))
        println("-".repeat(50))
        printLatencyStats("Write", writeLatencies)
        printLatencyStats("Read", readLatencies)
        printLatencyStats("Transaction", txLatencies)

        // Cleanup
        firestore.collection("latencyTest").get().await().documents.forEach {
            it.reference.delete().await()
        }
    }

    @Test
    fun `listener test - measure listener update frequency`() = runBlocking {
        println("Firebase Emulator Listener Test")
        println()

        val roomId = "listener_test_room"
        val updates = AtomicInteger(0)
        val updateLatencies = mutableListOf<Long>()
        var lastUpdateTime = System.currentTimeMillis()

        // Setup listener
        val docRef = firestore.collection("listenerTest").document(roomId)
        docRef.set(mapOf("counter" to 0)).await()

        val listenerJob = launch {
            docRef.addSnapshotListener { snapshot, error ->
                if (snapshot != null && snapshot.exists()) {
                    val now = System.currentTimeMillis()
                    if (updates.get() > 0) {
                        synchronized(updateLatencies) {
                            updateLatencies.add(now - lastUpdateTime)
                        }
                    }
                    lastUpdateTime = now
                    updates.incrementAndGet()
                }
            }
        }

        // Generate updates
        val writeCount = 50
        repeat(writeCount) { i ->
            docRef.update("counter", i, "timestamp", System.currentTimeMillis()).await()
            delay(100) // 10 updates per second
        }

        // Wait for listener to catch up
        delay(1000)

        println("Listener Results:")
        println("-".repeat(50))
        println("Writes sent: $writeCount")
        println("Updates received: ${updates.get()}")
        println("Update rate: ${String.format("%.1f", updates.get().toDouble() / 5)}/ sec")

        if (updateLatencies.isNotEmpty()) {
            println("Avg latency between updates: ${updateLatencies.average().toLong()}ms")
        }

        listenerJob.cancel()
        docRef.delete().await()
    }

    private suspend fun runGame(gameIndex: Int) {
        val roomId = "load_test_$gameIndex"
        val players = (0 until PLAYERS_PER_GAME).map { i ->
            mapOf(
                "id" to "player_${gameIndex}_$i",
                "name" to "Player $i",
                "colorIndex" to i
            )
        }

        try {
            // Create room
            metrics.recordOperation("room_create") {
                firestore.collection("ludoRooms")
                    .document(roomId)
                    .set(mapOf(
                        "hostId" to players[0]["id"],
                        "status" to "WAITING",
                        "players" to listOf(players[0]),
                        "createdAt" to System.currentTimeMillis()
                    ))
                    .await()
            }

            // Players join
            players.drop(1).forEach { player ->
                metrics.recordOperation("player_join") {
                    firestore.runTransaction { tx ->
                        val roomRef = firestore.collection("ludoRooms").document(roomId)
                        val snapshot = tx.get(roomRef)
                        val currentPlayers = snapshot.get("players") as? List<Map<String, Any>> ?: emptyList()
                        tx.update(roomRef, "players", currentPlayers + player)
                        null
                    }.await()
                }
            }

            // Initialize match
            metrics.recordOperation("match_init") {
                val batch = firestore.batch()
                val stateRef = firestore.collection("ludoRooms")
                    .document(roomId)
                    .collection("match")
                    .document("state")

                batch.set(stateRef, mapOf(
                    "currentTurnIndex" to 0,
                    "diceValue" to null,
                    "gameStatus" to "IN_PROGRESS",
                    "players" to players.mapIndexed { i, p ->
                        p + mapOf("tokens" to (0..3).map { mapOf("id" to it, "position" to -1) })
                    }
                ))

                players.forEach { player ->
                    val presenceRef = firestore.collection("ludoRooms")
                        .document(roomId)
                        .collection("presence")
                        .document(player["id"] as String)
                    batch.set(presenceRef, mapOf(
                        "playerId" to player["id"],
                        "isOnline" to true,
                        "lastSeenAt" to System.currentTimeMillis()
                    ))
                }

                batch.update(firestore.collection("ludoRooms").document(roomId), "status", "PLAYING")
                batch.commit().await()
            }

            // Gameplay
            repeat(TURNS_PER_GAME) { turn ->
                val currentPlayer = players[turn % PLAYERS_PER_GAME]

                // Send action
                metrics.recordOperation("action_send") {
                    firestore.collection("ludoRooms")
                        .document(roomId)
                        .collection("actions")
                        .document()
                        .set(mapOf(
                            "playerId" to currentPlayer["id"],
                            "type" to "ROLL_DICE",
                            "createdAt" to System.currentTimeMillis()
                        ))
                        .await()
                }

                // Update state
                metrics.recordOperation("state_update") {
                    firestore.collection("ludoRooms")
                        .document(roomId)
                        .collection("match")
                        .document("state")
                        .update(mapOf(
                            "currentTurnIndex" to (turn + 1) % 4,
                            "diceValue" to Random.nextInt(1, 7),
                            "lastActionAt" to System.currentTimeMillis()
                        ))
                        .await()
                }

                // Heartbeat every 5 turns
                if (turn % 5 == 0) {
                    metrics.recordOperation("heartbeat") {
                        firestore.collection("ludoRooms")
                            .document(roomId)
                            .collection("presence")
                            .document(currentPlayer["id"] as String)
                            .update("lastSeenAt", System.currentTimeMillis())
                            .await()
                    }
                }
            }

            // End game
            metrics.recordOperation("game_end") {
                val batch = firestore.batch()
                batch.update(
                    firestore.collection("ludoRooms").document(roomId),
                    "status", "ENDED"
                )
                batch.update(
                    firestore.collection("ludoRooms").document(roomId).collection("match").document("state"),
                    "gameStatus", "FINISHED",
                    "winnerId", players[0]["id"]
                )
                batch.commit().await()
            }

            metrics.gamesCompleted.incrementAndGet()

        } catch (e: Exception) {
            metrics.gamesFailed.incrementAndGet()
            metrics.errors.add("Game $gameIndex: ${e.message}")
        }
    }

    private suspend fun cleanupTestData() {
        try {
            val rooms = firestore.collection("ludoRooms")
                .whereGreaterThanOrEqualTo("hostId", "player_")
                .get()
                .await()

            rooms.documents.forEach { doc ->
                // Delete subcollections
                doc.reference.collection("match").get().await().documents.forEach {
                    it.reference.delete().await()
                }
                doc.reference.collection("actions").get().await().documents.forEach {
                    it.reference.delete().await()
                }
                doc.reference.collection("presence").get().await().documents.forEach {
                    it.reference.delete().await()
                }
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            println("Cleanup error: ${e.message}")
        }
    }

    private fun printResults(durationMs: Long) {
        println()
        println("=".repeat(70))
        println("FIREBASE EMULATOR LOAD TEST RESULTS")
        println("=".repeat(70))
        println()
        println("Duration: ${durationMs}ms (${durationMs / 1000}s)")
        println("Games Completed: ${metrics.gamesCompleted.get()}/$CONCURRENT_GAMES")
        println("Games Failed: ${metrics.gamesFailed.get()}")
        println()
        println("Operation Latencies:")
        println("-".repeat(70))
        println(String.format("%-20s %8s %10s %10s %10s %10s",
            "Operation", "Count", "Avg(ms)", "Min(ms)", "Max(ms)", "P95(ms)"))
        println("-".repeat(70))

        metrics.operationLatencies.forEach { (name, latencies) ->
            if (latencies.isNotEmpty()) {
                val sorted = latencies.sorted()
                val p95Index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)
                println(String.format("%-20s %8d %10.1f %10d %10d %10d",
                    name,
                    latencies.size,
                    latencies.average(),
                    sorted.first(),
                    sorted.last(),
                    sorted[p95Index]
                ))
            }
        }

        if (metrics.errors.isNotEmpty()) {
            println()
            println("Errors (first 5):")
            metrics.errors.take(5).forEach { println("  - $it") }
        }
    }

    private fun printLatencyStats(name: String, latencies: List<Long>) {
        if (latencies.isEmpty()) return
        val sorted = latencies.sorted()
        val p95Index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)
        println(String.format("%-15s %10.1f %10d %10d %10d",
            name,
            latencies.average(),
            sorted.first(),
            sorted.last(),
            sorted[p95Index]
        ))
    }

    class EmulatorTestMetrics {
        val gamesCompleted = AtomicInteger(0)
        val gamesFailed = AtomicInteger(0)
        val errors = mutableListOf<String>()
        val operationLatencies = mutableMapOf<String, MutableList<Long>>()

        @PublishedApi
        internal val lock = Any()

        suspend inline fun <T> recordOperation(name: String, block: suspend () -> T): T {
            val start = System.currentTimeMillis()
            val result = block()
            val duration = System.currentTimeMillis() - start

            synchronized(lock) {
                operationLatencies.getOrPut(name) { mutableListOf() }.add(duration)
            }

            return result
        }
    }
}
