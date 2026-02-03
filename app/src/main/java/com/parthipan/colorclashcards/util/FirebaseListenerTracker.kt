package com.parthipan.colorclashcards.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks Firebase listener update frequency to detect excessive updates.
 *
 * Usage:
 * ```kotlin
 * private val stateTracker = FirebaseListenerTracker("match_state", maxUpdatesPerSecond = 5)
 *
 * fun observeMatchState(roomId: String): Flow<LudoMatchState?> = callbackFlow {
 *     val listener = stateRef.addSnapshotListener { snapshot, error ->
 *         stateTracker.recordUpdate()
 *         // ... handle snapshot
 *     }
 *     awaitClose {
 *         listener.remove()
 *         stateTracker.logStats()
 *     }
 * }
 * ```
 */
class FirebaseListenerTracker(
    private val name: String,
    private val maxUpdatesPerSecond: Int = 10,
    private val warningThreshold: Int = 5
) {
    private val updateTimestamps = mutableListOf<Long>()
    private val totalUpdates = AtomicInteger(0)
    private val excessiveUpdateCount = AtomicInteger(0)
    private val totalPayloadBytes = AtomicLong(0)
    private val startTime = System.currentTimeMillis()

    private val lock = Any()

    /**
     * Records an update from the listener.
     * @param payloadSizeBytes Optional size of the payload for bandwidth tracking
     */
    fun recordUpdate(payloadSizeBytes: Int = 0) {
        val now = System.currentTimeMillis()

        synchronized(lock) {
            updateTimestamps.add(now)
            totalUpdates.incrementAndGet()
            totalPayloadBytes.addAndGet(payloadSizeBytes.toLong())

            // Remove timestamps older than 1 second
            updateTimestamps.removeAll { now - it > 1000 }

            // Check for excessive updates
            if (updateTimestamps.size > maxUpdatesPerSecond) {
                excessiveUpdateCount.incrementAndGet()
                Log.w(TAG, "[$name] Excessive updates: ${updateTimestamps.size}/sec " +
                        "(threshold: $maxUpdatesPerSecond)")
            } else if (updateTimestamps.size > warningThreshold) {
                Log.d(TAG, "[$name] High update rate: ${updateTimestamps.size}/sec")
            }
        }
    }

    /**
     * Gets current statistics for this listener.
     */
    fun getStats(): ListenerStats {
        val durationMs = System.currentTimeMillis() - startTime
        val durationMinutes = durationMs / 60_000.0

        return ListenerStats(
            name = name,
            totalUpdates = totalUpdates.get(),
            excessiveUpdateCount = excessiveUpdateCount.get(),
            totalPayloadBytes = totalPayloadBytes.get(),
            durationMs = durationMs,
            avgUpdatesPerMinute = if (durationMinutes > 0) {
                totalUpdates.get() / durationMinutes
            } else 0.0,
            avgPayloadBytes = if (totalUpdates.get() > 0) {
                totalPayloadBytes.get() / totalUpdates.get()
            } else 0
        )
    }

    /**
     * Logs statistics for this listener.
     */
    fun logStats() {
        val stats = getStats()
        Log.i(TAG, "[$name] Stats: ${stats.totalUpdates} updates, " +
                "${stats.excessiveUpdateCount} excessive, " +
                "${String.format("%.1f", stats.avgUpdatesPerMinute)}/min avg, " +
                "${stats.totalPayloadBytes} bytes total")
    }

    /**
     * Resets all counters.
     */
    fun reset() {
        synchronized(lock) {
            updateTimestamps.clear()
            totalUpdates.set(0)
            excessiveUpdateCount.set(0)
            totalPayloadBytes.set(0)
        }
    }

    companion object {
        private const val TAG = "FirebaseListener"
    }
}

/**
 * Statistics for a Firebase listener.
 */
data class ListenerStats(
    val name: String,
    val totalUpdates: Int,
    val excessiveUpdateCount: Int,
    val totalPayloadBytes: Long,
    val durationMs: Long,
    val avgUpdatesPerMinute: Double,
    val avgPayloadBytes: Long
) {
    val hasExcessiveUpdates: Boolean
        get() = excessiveUpdateCount > 0

    val durationFormatted: String
        get() {
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            return if (minutes > 0) "${minutes}m ${seconds % 60}s" else "${seconds}s"
        }
}

/**
 * Aggregates statistics from multiple listeners.
 */
class FirebaseListenerAggregator {
    private val trackers = ConcurrentHashMap<String, FirebaseListenerTracker>()

    /**
     * Gets or creates a tracker for the given listener name.
     */
    fun getTracker(
        name: String,
        maxUpdatesPerSecond: Int = 10
    ): FirebaseListenerTracker {
        return trackers.getOrPut(name) {
            FirebaseListenerTracker(name, maxUpdatesPerSecond)
        }
    }

    /**
     * Gets statistics for all tracked listeners.
     */
    fun getAllStats(): List<ListenerStats> {
        return trackers.values.map { it.getStats() }
    }

    /**
     * Gets a summary of all listener activity.
     */
    fun getSummary(): ListenerSummary {
        val allStats = getAllStats()
        return ListenerSummary(
            totalListeners = allStats.size,
            totalUpdates = allStats.sumOf { it.totalUpdates },
            totalExcessiveUpdates = allStats.sumOf { it.excessiveUpdateCount },
            totalPayloadBytes = allStats.sumOf { it.totalPayloadBytes },
            listenersWithExcessiveUpdates = allStats.count { it.hasExcessiveUpdates },
            stats = allStats
        )
    }

    /**
     * Logs summary of all listeners.
     */
    fun logSummary() {
        val summary = getSummary()
        Log.i(TAG, "Firebase Listener Summary:")
        Log.i(TAG, "  Total listeners: ${summary.totalListeners}")
        Log.i(TAG, "  Total updates: ${summary.totalUpdates}")
        Log.i(TAG, "  Excessive updates: ${summary.totalExcessiveUpdates}")
        Log.i(TAG, "  Listeners with issues: ${summary.listenersWithExcessiveUpdates}")

        summary.stats.forEach { stats ->
            val status = when {
                stats.excessiveUpdateCount > 10 -> "❌ CRITICAL"
                stats.hasExcessiveUpdates -> "⚠️ WARNING"
                else -> "✓ OK"
            }
            Log.i(TAG, "  [$status] ${stats.name}: ${stats.totalUpdates} updates")
        }
    }

    /**
     * Resets all trackers.
     */
    fun resetAll() {
        trackers.values.forEach { it.reset() }
    }

    /**
     * Clears all trackers.
     */
    fun clear() {
        trackers.clear()
    }

    companion object {
        private const val TAG = "FirebaseListeners"

        // Singleton instance for app-wide tracking
        val instance = FirebaseListenerAggregator()
    }
}

/**
 * Summary of all listener activity.
 */
data class ListenerSummary(
    val totalListeners: Int,
    val totalUpdates: Int,
    val totalExcessiveUpdates: Int,
    val totalPayloadBytes: Long,
    val listenersWithExcessiveUpdates: Int,
    val stats: List<ListenerStats>
) {
    val hasIssues: Boolean
        get() = listenersWithExcessiveUpdates > 0

    val healthStatus: HealthStatus
        get() = when {
            totalExcessiveUpdates > 50 -> HealthStatus.CRITICAL
            totalExcessiveUpdates > 10 -> HealthStatus.WARNING
            listenersWithExcessiveUpdates > 0 -> HealthStatus.CAUTION
            else -> HealthStatus.HEALTHY
        }

    enum class HealthStatus {
        HEALTHY,
        CAUTION,
        WARNING,
        CRITICAL
    }
}

/**
 * Extension to estimate Firestore document size.
 * Useful for tracking payload sizes.
 */
fun Map<String, Any?>.estimateFirestoreSize(): Int {
    var size = 0
    forEach { (key, value) ->
        size += key.length + 1 // Key + type byte
        size += when (value) {
            null -> 0
            is String -> value.length
            is Int, is Long, is Float, is Double -> 8
            is Boolean -> 1
            is Map<*, *> -> (value as Map<String, Any?>).estimateFirestoreSize()
            is List<*> -> value.sumOf { item ->
                when (item) {
                    is Map<*, *> -> (item as Map<String, Any?>).estimateFirestoreSize()
                    is String -> item.length
                    else -> 8
                }
            }
            else -> 8
        }
    }
    return size
}
