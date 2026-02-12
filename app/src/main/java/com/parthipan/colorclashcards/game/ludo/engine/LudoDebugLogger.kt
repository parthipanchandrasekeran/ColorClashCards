package com.parthipan.colorclashcards.game.ludo.engine

import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.parthipan.colorclashcards.BuildConfig
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import com.parthipan.colorclashcards.game.ludo.model.LudoGameState
import com.parthipan.colorclashcards.game.ludo.model.TokenState

/**
 * Debug logger for Ludo game moves.
 * Maintains a circular buffer of recent moves for bug diagnosis.
 * All output uses tag "LudoDebug" for easy logcat filtering.
 *
 * TEMPORARY: Firebase remote upload enabled for production bug diagnosis.
 * Remove uploadToFirestore() calls once the "token beyond home path" bug is resolved.
 */
object LudoDebugLogger {

    private const val TAG = "LudoDebug"
    private const val MAX_ENTRIES = 200
    private const val FIRESTORE_COLLECTION = "debug_logs"

    private val entries = ArrayDeque<String>(MAX_ENTRIES + 10)
    private var turnNumber = 0

    /**
     * Clear all logged entries. Call at game start.
     */
    fun clear() {
        entries.clear()
        turnNumber = 0
        Log.d(TAG, "=== DEBUG LOG CLEARED — NEW GAME ===")
    }

    /**
     * Log a dice roll event.
     */
    fun logRoll(
        color: LudoColor,
        playerName: String,
        diceValue: Int,
        movableTokenIds: List<Int>,
        consecutiveSixes: Int
    ) {
        turnNumber++
        val movableStr = if (movableTokenIds.isEmpty()) "NONE" else movableTokenIds.joinToString(",")
        val entry = "T$turnNumber ROLL | $color ($playerName) dice=$diceValue " +
            "movable=[$movableStr] consec6=$consecutiveSixes"
        addEntry(entry)
    }

    /**
     * Log a token move event.
     */
    fun logMove(
        color: LudoColor,
        tokenId: Int,
        diceValue: Int,
        fromPos: Int,
        toPos: Int,
        fromState: TokenState,
        toState: TokenState,
        moveType: String,
        captured: Boolean = false
    ) {
        val fromLabel = positionLabel(fromPos, fromState)
        val toLabel = positionLabel(toPos, toState)
        val captureTag = if (captured) " [CAPTURE]" else ""
        val entry = "T$turnNumber MOVE | $color token#$tokenId dice=$diceValue " +
            "$fromLabel → $toLabel ($moveType)$captureTag"
        addEntry(entry)
    }

    /**
     * Log a full state snapshot of all token positions.
     */
    fun logStateSnapshot(state: LudoGameState) {
        val sb = StringBuilder("T$turnNumber STATE |")
        for (player in state.players) {
            sb.append(" ${player.color}[")
            player.tokens.forEachIndexed { i, token ->
                if (i > 0) sb.append(",")
                sb.append("#${token.id}:")
                sb.append(positionLabel(token.position, token.state))
            }
            sb.append("]")
        }
        sb.append(" turn=${state.currentPlayer.color}")
        if (state.isGameOver) sb.append(" GAME_OVER winner=${state.winnerId}")
        addEntry(sb.toString())
    }

    /**
     * Log a position validation failure.
     */
    fun logValidationError(message: String) {
        val entry = "T$turnNumber !! VALIDATION ERROR: $message"
        addEntry(entry)
        Log.e(TAG, entry)
        // Auto-upload on validation errors — this is the bug we're hunting
        uploadToFirestore("validation_error")
    }

    /**
     * Get the full debug log as a single string.
     */
    fun getLog(): String {
        if (entries.isEmpty()) return "(empty — no moves logged)"
        return buildString {
            appendLine("=== LUDO DEBUG LOG ($turnNumber turns, ${entries.size} entries) ===")
            entries.forEach { appendLine(it) }
            appendLine("=== END DEBUG LOG ===")
        }
    }

    /**
     * Dump the full log to logcat at WARN level for visibility.
     */
    fun dumpToLogcat() {
        Log.w(TAG, "=== DUMPING FULL DEBUG LOG (${entries.size} entries) ===")
        entries.forEach { Log.w(TAG, it) }
        Log.w(TAG, "=== END DUMP ===")
    }

    /**
     * TEMPORARY: Upload the log buffer to Firestore for remote debugging.
     * Remove once the "token beyond home path" bug is resolved.
     *
     * @param trigger What caused the upload (e.g., "validation_error", "game_over", "move")
     */
    fun uploadToFirestore(trigger: String) {
        if (entries.isEmpty()) return

        val userId = try {
            FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        } catch (e: Exception) {
            "unknown"
        }

        val logData = mapOf(
            "timestamp" to FieldValue.serverTimestamp(),
            "trigger" to trigger,
            "userId" to userId,
            "appVersion" to BuildConfig.VERSION_NAME,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "sdk" to Build.VERSION.SDK_INT,
            "turns" to turnNumber,
            "entryCount" to entries.size,
            "log" to getLog()
        )

        try {
            FirebaseFirestore.getInstance()
                .collection(FIRESTORE_COLLECTION)
                .add(logData)
                .addOnSuccessListener {
                    Log.d(TAG, "Debug log uploaded to Firestore (trigger=$trigger)")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to upload debug log", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading debug log", e)
        }
    }

    private fun addEntry(entry: String) {
        Log.d(TAG, entry)
        entries.addLast(entry)
        // Trim oldest entries if over capacity
        while (entries.size > MAX_ENTRIES) {
            entries.removeFirst()
        }
    }

    private fun positionLabel(position: Int, state: TokenState): String {
        return when (state) {
            TokenState.HOME -> "HOME"
            TokenState.FINISHED -> "FIN(56)"
            TokenState.ACTIVE -> when {
                position < 0 -> "HOME($position)"
                position <= 50 -> "ring($position)"
                position <= 56 -> "lane(${position - 51})"
                else -> "?($position)"
            }
        }
    }
}
