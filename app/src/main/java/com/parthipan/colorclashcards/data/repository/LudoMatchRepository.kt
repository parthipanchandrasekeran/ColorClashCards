package com.parthipan.colorclashcards.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.parthipan.colorclashcards.data.model.*
import com.parthipan.colorclashcards.game.ludo.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Ludo match state synchronization.
 * Handles game state updates, actions, and presence tracking.
 */
class LudoMatchRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Initialize match state when game starts.
     * Called by host after all players are ready.
     */
    suspend fun initializeMatch(roomId: String, gameState: LudoGameState): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))
            val batch = firestore.batch()

            // Write match state
            val stateRef = firestore.collection("ludoRooms")
                .document(roomId)
                .collection("match")
                .document("state")

            val matchState = LudoMatchState.fromGameState(gameState)
            batch.set(stateRef, matchState.toMap())

            // Only write host's own presence doc (other players write theirs via heartbeat)
            val presenceRef = firestore.collection("ludoRooms")
                .document(roomId)
                .collection("presence")
                .document(userId)

            val presence = LudoPlayerPresence(
                playerId = userId,
                isOnline = true,
                lastSeenAt = Timestamp.now()
            )
            batch.set(presenceRef, presence.toMap())

            // Update room status
            val roomRef = firestore.collection("ludoRooms").document(roomId)
            batch.update(roomRef, "status", LudoRoomStatus.PLAYING.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update match state.
     * Called by host after processing a move.
     */
    suspend fun updateMatchState(roomId: String, gameState: LudoGameState): Result<Unit> {
        return try {
            val matchState = LudoMatchState.fromGameState(gameState)

            firestore.collection("ludoRooms")
                .document(roomId)
                .collection("match")
                .document("state")
                .set(matchState.toMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a player action (dice roll or token move).
     */
    suspend fun sendAction(
        roomId: String,
        actionType: LudoActionType,
        tokenId: Int? = null
    ): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val action = LudoPlayerAction(
                playerId = userId,
                type = actionType.name,
                tokenId = tokenId
            )

            firestore.collection("ludoRooms")
                .document(roomId)
                .collection("actions")
                .add(action.toMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a processed action.
     */
    suspend fun deleteAction(roomId: String, actionId: String): Result<Unit> {
        return try {
            firestore.collection("ludoRooms")
                .document(roomId)
                .collection("actions")
                .document(actionId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update player presence (heartbeat).
     */
    suspend fun updatePresence(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val presence = LudoPlayerPresence(
                playerId = userId,
                isOnline = true,
                lastSeenAt = Timestamp.now()
            )

            firestore.collection("ludoRooms")
                .document(roomId)
                .collection("presence")
                .document(userId)
                .set(presence.toMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark player as disconnected.
     */
    suspend fun markDisconnected(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            firestore.collection("ludoRooms")
                .document(roomId)
                .collection("presence")
                .document(userId)
                .update(
                    mapOf(
                        "isOnline" to false,
                        "disconnectedAt" to Timestamp.now()
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark player as reconnected.
     */
    suspend fun markReconnected(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val presence = LudoPlayerPresence(
                playerId = userId,
                isOnline = true,
                lastSeenAt = Timestamp.now(),
                disconnectedAt = null
            )

            firestore.collection("ludoRooms")
                .document(roomId)
                .collection("presence")
                .document(userId)
                .set(presence.toMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update player connection status in match state.
     */
    suspend fun updatePlayerConnection(
        roomId: String,
        playerId: String,
        isConnected: Boolean
    ): Result<Unit> {
        return try {
            val stateDoc = firestore.collection("ludoRooms")
                .document(roomId)
                .collection("match")
                .document("state")
                .get()
                .await()

            if (!stateDoc.exists()) {
                return Result.failure(Exception("Match not found"))
            }

            val matchState = LudoMatchState.fromMap(stateDoc.data ?: emptyMap())
            val updatedPlayers = matchState.players.map {
                if (it.id == playerId) it.copy(isConnected = isConnected) else it
            }

            stateDoc.reference.update(
                "players", updatedPlayers.map { it.toMap() }
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * End the match with a winner.
     */
    suspend fun endMatch(roomId: String, winnerId: String): Result<Unit> {
        return try {
            val batch = firestore.batch()

            // Update match state
            val stateRef = firestore.collection("ludoRooms")
                .document(roomId)
                .collection("match")
                .document("state")

            batch.update(stateRef, mapOf(
                "winnerId" to winnerId,
                "gameStatus" to GameStatus.FINISHED.name
            ))

            // Update room status
            val roomRef = firestore.collection("ludoRooms").document(roomId)
            batch.update(roomRef, "status", LudoRoomStatus.ENDED.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * End match due to player dropout (not enough players).
     */
    suspend fun endMatchDueToDropout(roomId: String, remainingPlayerId: String?): Result<Unit> {
        return try {
            val batch = firestore.batch()

            val stateRef = firestore.collection("ludoRooms")
                .document(roomId)
                .collection("match")
                .document("state")

            batch.update(stateRef, mapOf(
                "winnerId" to remainingPlayerId,
                "gameStatus" to GameStatus.FINISHED.name
            ))

            val roomRef = firestore.collection("ludoRooms").document(roomId)
            batch.update(roomRef, "status", LudoRoomStatus.ENDED.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe match state for real-time updates.
     */
    fun observeMatchState(roomId: String): Flow<LudoMatchState?> = callbackFlow {
        val listener = firestore.collection("ludoRooms")
            .document(roomId)
            .collection("match")
            .document("state")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val state = LudoMatchState.fromMap(snapshot.data ?: emptyMap())
                trySend(state)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Observe player actions (for host to process).
     */
    fun observeActions(roomId: String): Flow<List<LudoPlayerAction>> = callbackFlow {
        val listener = firestore.collection("ludoRooms")
            .document(roomId)
            .collection("actions")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val actions = snapshot.documents.mapNotNull { doc ->
                    try {
                        LudoPlayerAction.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(actions)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Observe all player presence for disconnect detection.
     */
    fun observePresence(roomId: String): Flow<List<LudoPlayerPresence>> = callbackFlow {
        val listener = firestore.collection("ludoRooms")
            .document(roomId)
            .collection("presence")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val presenceList = snapshot.documents.mapNotNull { doc ->
                    try {
                        LudoPlayerPresence.fromMap(doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(presenceList)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get current match state.
     */
    suspend fun getMatchState(roomId: String): Result<LudoMatchState?> {
        return try {
            val doc = firestore.collection("ludoRooms")
                .document(roomId)
                .collection("match")
                .document("state")
                .get()
                .await()

            if (!doc.exists()) {
                return Result.success(null)
            }

            Result.success(LudoMatchState.fromMap(doc.data ?: emptyMap()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if a player has been AFK too long.
     */
    fun isPlayerAfk(turnStartedAt: Timestamp?): Boolean {
        if (turnStartedAt == null) return false

        val elapsed = System.currentTimeMillis() - turnStartedAt.toDate().time
        return elapsed > LudoMatchState.AFK_TIMEOUT_MS
    }

    /**
     * Check if a disconnected player can still rejoin.
     */
    fun canRejoin(disconnectedAt: Timestamp?): Boolean {
        if (disconnectedAt == null) return true

        val elapsed = System.currentTimeMillis() - disconnectedAt.toDate().time
        return elapsed <= LudoMatchState.REJOIN_TIMEOUT_MS
    }
}
