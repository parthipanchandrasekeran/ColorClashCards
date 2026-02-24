package com.parthipan.colorclashcards.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.parthipan.colorclashcards.data.model.*
import com.parthipan.colorclashcards.game.ludo.model.GameStatus
import com.parthipan.colorclashcards.game.snl.model.SnlGameState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Snake & Ladder match state synchronization.
 * Mirrors LudoMatchRepository, pointing to snlRooms collection.
 */
class SnlMatchRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun initializeMatch(roomId: String, gameState: SnlGameState): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))
            val batch = firestore.batch()

            val stateRef = firestore.collection("snlRooms")
                .document(roomId).collection("match").document("state")
            val matchState = SnlMatchState.fromGameState(gameState)
            batch.set(stateRef, matchState.toMap())

            val presenceRef = firestore.collection("snlRooms")
                .document(roomId).collection("presence").document(userId)
            val presence = SnlPlayerPresence(playerId = userId, isOnline = true, lastSeenAt = Timestamp.now())
            batch.set(presenceRef, presence.toMap())

            val roomRef = firestore.collection("snlRooms").document(roomId)
            batch.update(roomRef, "status", SnlRoomStatus.PLAYING.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMatchState(
        roomId: String,
        gameState: SnlGameState,
        rolledDiceValue: Int? = null
    ): Result<Unit> {
        return try {
            val matchState = SnlMatchState.fromGameState(gameState).let {
                if (rolledDiceValue != null) it.copy(diceValue = rolledDiceValue) else it
            }
            firestore.collection("snlRooms")
                .document(roomId).collection("match").document("state")
                .set(matchState.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAction(roomId: String, actionType: SnlActionType): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))
        return try {
            val action = SnlPlayerAction(playerId = userId, type = actionType.name)
            firestore.collection("snlRooms")
                .document(roomId).collection("actions")
                .add(action.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAction(roomId: String, actionId: String): Result<Unit> {
        return try {
            firestore.collection("snlRooms")
                .document(roomId).collection("actions").document(actionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePresence(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))
        return try {
            val presence = SnlPlayerPresence(playerId = userId, isOnline = true, lastSeenAt = Timestamp.now())
            firestore.collection("snlRooms")
                .document(roomId).collection("presence").document(userId)
                .set(presence.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markDisconnected(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))
        return try {
            firestore.collection("snlRooms")
                .document(roomId).collection("presence").document(userId)
                .update(mapOf("isOnline" to false, "disconnectedAt" to Timestamp.now())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markReconnected(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))
        return try {
            val presence = SnlPlayerPresence(playerId = userId, isOnline = true, lastSeenAt = Timestamp.now(), disconnectedAt = null)
            firestore.collection("snlRooms")
                .document(roomId).collection("presence").document(userId)
                .set(presence.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePlayerConnection(roomId: String, playerId: String, isConnected: Boolean): Result<Unit> {
        return try {
            val stateDoc = firestore.collection("snlRooms")
                .document(roomId).collection("match").document("state").get().await()
            if (!stateDoc.exists()) return Result.failure(Exception("Match not found"))

            val matchState = SnlMatchState.fromMap(stateDoc.data ?: emptyMap())
            val updatedPlayers = matchState.players.map {
                if (it.id == playerId) it.copy(isConnected = isConnected) else it
            }
            stateDoc.reference.update("players", updatedPlayers.map { it.toMap() }).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endMatch(roomId: String, winnerId: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val stateRef = firestore.collection("snlRooms")
                .document(roomId).collection("match").document("state")
            batch.update(stateRef, mapOf("winnerId" to winnerId, "gameStatus" to GameStatus.FINISHED.name))

            val roomRef = firestore.collection("snlRooms").document(roomId)
            batch.update(roomRef, "status", SnlRoomStatus.ENDED.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endMatchDueToDropout(roomId: String, remainingPlayerId: String?): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val stateRef = firestore.collection("snlRooms")
                .document(roomId).collection("match").document("state")
            batch.update(stateRef, mapOf("winnerId" to remainingPlayerId, "gameStatus" to GameStatus.FINISHED.name))

            val roomRef = firestore.collection("snlRooms").document(roomId)
            batch.update(roomRef, "status", SnlRoomStatus.ENDED.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeMatchState(roomId: String): Flow<SnlMatchState?> = callbackFlow {
        val listener = firestore.collection("snlRooms")
            .document(roomId).collection("match").document("state")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null); return@addSnapshotListener
                }
                trySend(SnlMatchState.fromMap(snapshot.data ?: emptyMap()))
            }
        awaitClose { listener.remove() }
    }

    fun observeActions(roomId: String): Flow<List<SnlPlayerAction>> = callbackFlow {
        val listener = firestore.collection("snlRooms")
            .document(roomId).collection("actions")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                val actions = snapshot.documents.mapNotNull { doc ->
                    try { SnlPlayerAction.fromMap(doc.id, doc.data ?: emptyMap()) }
                    catch (_: Exception) { null }
                }
                trySend(actions)
            }
        awaitClose { listener.remove() }
    }

    fun observePresence(roomId: String): Flow<List<SnlPlayerPresence>> = callbackFlow {
        val listener = firestore.collection("snlRooms")
            .document(roomId).collection("presence")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                val presenceList = snapshot.documents.mapNotNull { doc ->
                    try { SnlPlayerPresence.fromMap(doc.data ?: emptyMap()) }
                    catch (_: Exception) { null }
                }
                trySend(presenceList)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getMatchState(roomId: String): Result<SnlMatchState?> {
        return try {
            val doc = firestore.collection("snlRooms")
                .document(roomId).collection("match").document("state").get().await()
            if (!doc.exists()) return Result.success(null)
            Result.success(SnlMatchState.fromMap(doc.data ?: emptyMap()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isPlayerAfk(turnStartedAt: Timestamp?): Boolean {
        if (turnStartedAt == null) return false
        val elapsed = System.currentTimeMillis() - turnStartedAt.toDate().time
        return elapsed > SnlMatchState.AFK_TIMEOUT_MS
    }

    fun canRejoin(disconnectedAt: Timestamp?): Boolean {
        if (disconnectedAt == null) return true
        val elapsed = System.currentTimeMillis() - disconnectedAt.toDate().time
        return elapsed <= SnlMatchState.REJOIN_TIMEOUT_MS
    }
}
