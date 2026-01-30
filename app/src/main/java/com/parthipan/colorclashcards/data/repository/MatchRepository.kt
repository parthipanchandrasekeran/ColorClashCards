package com.parthipan.colorclashcards.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.parthipan.colorclashcards.data.model.ActionType
import com.parthipan.colorclashcards.data.model.PlayerAction
import com.parthipan.colorclashcards.data.model.PlayerHand
import com.parthipan.colorclashcards.data.model.PublicMatchState
import com.parthipan.colorclashcards.data.model.Room
import com.parthipan.colorclashcards.data.model.RoomPlayer
import com.parthipan.colorclashcards.data.model.RoomStatus
import com.parthipan.colorclashcards.game.model.Card
import com.parthipan.colorclashcards.game.model.CardColor
import com.parthipan.colorclashcards.game.model.GameState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for online match operations.
 *
 * Simplified Firestore Structure:
 * - rooms/{roomId}                              -> Room info with embedded players
 * - rooms/{roomId}/match/public                 -> Public match state
 * - rooms/{roomId}/match/hands/{playerId}       -> Private hand (only owner reads)
 * - rooms/{roomId}/actions/{actionId}           -> Player actions
 */
class MatchRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUserId: String?
        get() = auth.currentUser?.uid

    // ==================== MATCH OPERATIONS (HOST ONLY) ====================

    /**
     * Initialize match when game starts (host only).
     * Writes public state and all player hands.
     */
    suspend fun initializeMatch(roomId: String, gameState: GameState): Result<Unit> {
        return try {
            val batch = firestore.batch()

            // Write public state
            val publicRef = firestore.collection("rooms")
                .document(roomId)
                .collection("match")
                .document("public")
            batch.set(publicRef, PublicMatchState.fromGameState(gameState).toMap())

            // Write each player's hand as separate document
            gameState.players.forEach { player ->
                val handRef = firestore.collection("rooms")
                    .document(roomId)
                    .collection("match")
                    .document("hands")
                    .collection("players")
                    .document(player.id)
                batch.set(handRef, PlayerHand.fromCardList(player.hand).toMap())
            }

            // Update room status to playing
            val roomRef = firestore.collection("rooms").document(roomId)
            batch.update(roomRef, "status", RoomStatus.PLAYING.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update match state after processing an action (host only).
     */
    suspend fun updateMatchState(
        roomId: String,
        gameState: GameState,
        lastActionId: String? = null
    ): Result<Unit> {
        return try {
            val batch = firestore.batch()

            // Update public state
            val publicRef = firestore.collection("rooms")
                .document(roomId)
                .collection("match")
                .document("public")
            batch.set(publicRef, PublicMatchState.fromGameState(gameState, lastActionId).toMap())

            // Update each player's hand
            gameState.players.forEach { player ->
                val handRef = firestore.collection("rooms")
                    .document(roomId)
                    .collection("match")
                    .document("hands")
                    .collection("players")
                    .document(player.id)
                batch.set(handRef, PlayerHand.fromCardList(player.hand).toMap())
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * End match (host only).
     */
    suspend fun endMatch(roomId: String, winnerId: String?): Result<Unit> {
        return try {
            val batch = firestore.batch()

            // Update public state with winner
            val publicRef = firestore.collection("rooms")
                .document(roomId)
                .collection("match")
                .document("public")
            batch.update(publicRef, "winnerId", winnerId)

            // Update room status to ended
            val roomRef = firestore.collection("rooms").document(roomId)
            batch.update(roomRef, "status", RoomStatus.ENDED.name)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== MATCH OBSERVATION (CLIENTS) ====================

    /**
     * Observe public match state (all players can read).
     */
    fun observePublicState(roomId: String): Flow<PublicMatchState?> = callbackFlow {
        val listener = firestore.collection("rooms")
            .document(roomId)
            .collection("match")
            .document("public")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(PublicMatchState.fromMap(snapshot.data ?: emptyMap()))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe my hand only (security rule enforces this).
     */
    fun observeMyHand(roomId: String): Flow<List<Card>> = callbackFlow {
        val userId = currentUserId ?: ""

        val listener = firestore.collection("rooms")
            .document(roomId)
            .collection("match")
            .document("hands")
            .collection("players")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val hand = PlayerHand.fromMap(snapshot.data ?: emptyMap())
                trySend(hand.toCardList())
            }
        awaitClose { listener.remove() }
    }

    /**
     * Get room players from embedded list.
     */
    fun observeRoomPlayers(roomId: String): Flow<List<RoomPlayer>> = callbackFlow {
        val listener = firestore.collection("rooms")
            .document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val room = Room.fromMap(snapshot.id, snapshot.data ?: emptyMap())
                trySend(room.players)
            }
        awaitClose { listener.remove() }
    }

    // ==================== ACTIONS ====================

    /**
     * Send a player action (clients only send their own).
     */
    suspend fun sendAction(
        roomId: String,
        actionType: ActionType,
        cardId: String? = null,
        chosenColor: CardColor? = null
    ): Result<String> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val action = PlayerAction(
                playerId = userId,
                type = actionType.name,
                cardId = cardId,
                chosenColor = chosenColor?.name,
                createdAt = Timestamp.now()
            )

            val docRef = firestore.collection("rooms")
                .document(roomId)
                .collection("actions")
                .add(action.toMap())
                .await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe actions (host listens for new actions).
     */
    fun observeActions(roomId: String): Flow<List<PlayerAction>> = callbackFlow {
        val listener = firestore.collection("rooms")
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
                        PlayerAction.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(actions)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Delete an action after processing (host only).
     */
    suspend fun deleteAction(roomId: String, actionId: String): Result<Unit> {
        return try {
            firestore.collection("rooms")
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
     * Delete all actions (cleanup, host only).
     */
    suspend fun deleteAllActions(roomId: String): Result<Unit> {
        return try {
            val snapshot = firestore.collection("rooms")
                .document(roomId)
                .collection("actions")
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== RECONNECTION ====================

    /**
     * Check if user is in an active (playing) room.
     */
    suspend fun getActiveRoom(): Result<ActiveRoomInfo?> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            // Query all playing rooms
            val snapshot = firestore.collection("rooms")
                .whereEqualTo("status", RoomStatus.PLAYING.name)
                .limit(20)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val room = Room.fromMap(doc.id, doc.data ?: emptyMap())

                // Check if user is a player in this room
                val isPlayer = room.players.any { it.odId == userId }
                if (isPlayer) {
                    return Result.success(
                        ActiveRoomInfo(
                            roomId = doc.id,
                            isHost = room.hostId == userId
                        )
                    )
                }
            }

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ActiveRoomInfo(
    val roomId: String,
    val isHost: Boolean
)
