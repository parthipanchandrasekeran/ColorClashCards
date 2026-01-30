package com.parthipan.colorclashcards.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.parthipan.colorclashcards.data.model.Room
import com.parthipan.colorclashcards.data.model.RoomPlayer
import com.parthipan.colorclashcards.data.model.RoomStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for room operations in Firestore.
 */
class RoomRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val roomsCollection = firestore.collection("rooms")

    /**
     * Get current user ID.
     */
    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Get current user display name.
     */
    val currentUserName: String
        get() = auth.currentUser?.displayName ?: "Guest"

    /**
     * Get current user photo URL.
     */
    val currentUserPhoto: String?
        get() = auth.currentUser?.photoUrl?.toString()

    /**
     * Create a new room.
     */
    suspend fun createRoom(maxPlayers: Int, isPublic: Boolean): Result<Room> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val roomCode = Room.generateRoomCode()

            // Check if room code already exists
            val existingRoom = roomsCollection
                .whereEqualTo("roomCode", roomCode)
                .whereEqualTo("status", RoomStatus.WAITING.name)
                .get()
                .await()

            if (!existingRoom.isEmpty) {
                // Retry with new code (rare case)
                return createRoom(maxPlayers, isPublic)
            }

            val hostPlayer = RoomPlayer(
                odId = userId,
                odisplayName = currentUserName,
                photoUrl = currentUserPhoto,
                isReady = true,  // Host is always ready
                isHost = true,
                joinedAt = Timestamp.now()
            )

            val room = Room(
                hostId = userId,
                hostName = currentUserName,
                roomCode = roomCode,
                status = RoomStatus.WAITING.name,
                maxPlayers = maxPlayers,
                isPublic = isPublic,
                players = listOf(hostPlayer)
            )

            val docRef = roomsCollection.add(room.toMap()).await()
            val createdRoom = room.copy(id = docRef.id)

            Result.success(createdRoom)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Join a room by code.
     */
    suspend fun joinRoomByCode(roomCode: String): Result<Room> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val querySnapshot = roomsCollection
                .whereEqualTo("roomCode", roomCode.uppercase())
                .whereEqualTo("status", RoomStatus.WAITING.name)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Room not found or already started"))
            }

            val doc = querySnapshot.documents.first()
            val room = Room.fromMap(doc.id, doc.data ?: emptyMap())

            // Check if already in room
            if (room.players.any { it.odId == userId }) {
                return Result.success(room)
            }

            // Check if room is full
            if (room.isFull()) {
                return Result.failure(Exception("Room is full"))
            }

            // Add player to room
            val newPlayer = RoomPlayer(
                odId = userId,
                odisplayName = currentUserName,
                photoUrl = currentUserPhoto,
                isReady = false,
                isHost = false,
                joinedAt = Timestamp.now()
            )

            roomsCollection.document(doc.id).update(
                "players", FieldValue.arrayUnion(newPlayer.toMap())
            ).await()

            Result.success(room.copy(players = room.players + newPlayer))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a room by ID.
     */
    suspend fun getRoom(roomId: String): Result<Room> {
        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Room not found"))
            }
            val room = Room.fromMap(doc.id, doc.data ?: emptyMap())
            Result.success(room)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe room changes in real-time.
     */
    fun observeRoom(roomId: String): Flow<Room?> = callbackFlow {
        val listener = roomsCollection.document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val room = Room.fromMap(snapshot.id, snapshot.data ?: emptyMap())
                    trySend(room)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get public rooms that are waiting.
     */
    fun observePublicRooms(): Flow<List<Room>> = callbackFlow {
        val listener = roomsCollection
            .whereEqualTo("isPublic", true)
            .whereEqualTo("status", RoomStatus.WAITING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val rooms = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Room.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }?.filter { !it.isFull() } ?: emptyList()

                trySend(rooms)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Update player ready status.
     */
    suspend fun setPlayerReady(roomId: String, isReady: Boolean): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Room not found"))
            }

            val room = Room.fromMap(doc.id, doc.data ?: emptyMap())
            val updatedPlayers = room.players.map { player ->
                if (player.odId == userId) {
                    player.copy(isReady = isReady)
                } else {
                    player
                }
            }

            roomsCollection.document(roomId).update(
                "players", updatedPlayers.map { it.toMap() }
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Start the game (host only).
     */
    suspend fun startGame(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Room not found"))
            }

            val room = Room.fromMap(doc.id, doc.data ?: emptyMap())

            // Verify host
            if (room.hostId != userId) {
                return Result.failure(Exception("Only the host can start the game"))
            }

            // Check if can start
            if (!room.canStart()) {
                return Result.failure(Exception("Need at least 2 ready players"))
            }

            // Update room status
            roomsCollection.document(roomId).update(
                "status", RoomStatus.PLAYING.name
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Leave a room.
     */
    suspend fun leaveRoom(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.success(Unit)
            }

            val room = Room.fromMap(doc.id, doc.data ?: emptyMap())
            val playerToRemove = room.players.find { it.odId == userId }

            if (playerToRemove == null) {
                return Result.success(Unit)
            }

            // If host leaves and there are other players, transfer host
            if (playerToRemove.isHost && room.players.size > 1) {
                val newHost = room.players.first { it.odId != userId }
                val updatedPlayers = room.players
                    .filter { it.odId != userId }
                    .map { player ->
                        if (player.odId == newHost.odId) {
                            player.copy(isHost = true, isReady = true)
                        } else {
                            player
                        }
                    }

                roomsCollection.document(roomId).update(
                    mapOf(
                        "players" to updatedPlayers.map { it.toMap() },
                        "hostId" to newHost.odId,
                        "hostName" to newHost.odisplayName
                    )
                ).await()
            } else if (room.players.size <= 1) {
                // Last player leaves, delete room
                roomsCollection.document(roomId).delete().await()
            } else {
                // Just remove the player
                roomsCollection.document(roomId).update(
                    "players", FieldValue.arrayRemove(playerToRemove.toMap())
                ).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a room (host only).
     */
    suspend fun deleteRoom(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (doc.exists()) {
                val room = Room.fromMap(doc.id, doc.data ?: emptyMap())
                if (room.hostId == userId) {
                    roomsCollection.document(roomId).delete().await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
