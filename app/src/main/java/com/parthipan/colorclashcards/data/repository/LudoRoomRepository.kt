package com.parthipan.colorclashcards.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import android.util.Log
import com.parthipan.colorclashcards.data.model.*
import com.parthipan.colorclashcards.game.ludo.model.LudoColor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Ludo room operations.
 * Handles room creation, joining, and player management.
 */
class LudoRoomRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val roomsCollection = firestore.collection("ludoRooms")

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val currentUserName: String
        get() = auth.currentUser?.displayName ?: "Player"

    private val currentUserPhoto: String?
        get() = auth.currentUser?.photoUrl?.toString()

    /**
     * Create a new Ludo room.
     */
    suspend fun createRoom(maxPlayers: Int, isPublic: Boolean): Result<LudoRoom> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val roomCode = generateRoomCode()

            val hostPlayer = LudoRoomPlayer(
                odId = userId,
                odisplayName = currentUserName,
                photoUrl = currentUserPhoto,
                color = LudoColor.RED.name,  // Host gets RED
                isReady = true,
                isHost = true,
                isConnected = true,
                lastActiveAt = Timestamp.now(),
                joinedAt = Timestamp.now()
            )

            val room = LudoRoom(
                hostId = userId,
                hostName = currentUserName,
                roomCode = roomCode,
                status = LudoRoomStatus.WAITING.name,
                maxPlayers = maxPlayers.coerceIn(2, 4),
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
     * Join a room by room code.
     * Uses single-field query on "roomCode" to avoid requiring a composite index,
     * then checks status client-side.
     */
    suspend fun joinRoomByCode(roomCode: String): Result<LudoRoom> {
        if (roomCode.isBlank() || roomCode.length != 6) {
            return Result.failure(Exception("Invalid room code"))
        }
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            // Query only by roomCode (single field = automatic index)
            val querySnapshot = roomsCollection
                .whereEqualTo("roomCode", roomCode.uppercase())
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Room not found"))
            }

            // Check status client-side
            val roomDoc = querySnapshot.documents.first()
            val roomData = LudoRoom.fromMap(roomDoc.id, roomDoc.data ?: emptyMap())
            if (roomData.status != LudoRoomStatus.WAITING.name) {
                return Result.failure(Exception("Game already started"))
            }

            val docRef = roomsCollection.document(querySnapshot.documents.first().id)

            // Use transaction for atomic capacity check + player add
            val updatedRoom = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val room = LudoRoom.fromMap(snapshot.id, snapshot.data ?: emptyMap())

                // Check if already in room
                if (room.players.any { it.odId == userId }) {
                    return@runTransaction room
                }

                // Check if room is full (inside transaction to prevent race)
                if (room.isFull()) {
                    throw Exception("Room is full")
                }

                // Assign next available color
                val usedColors = room.players.map { it.color }.toSet()
                val availableColor = LudoColor.entries.first { it.name !in usedColors }

                val newPlayer = LudoRoomPlayer(
                    odId = userId,
                    odisplayName = currentUserName,
                    photoUrl = currentUserPhoto,
                    color = availableColor.name,
                    isReady = false,
                    isHost = false,
                    isConnected = true,
                    lastActiveAt = Timestamp.now(),
                    joinedAt = Timestamp.now()
                )

                val updatedPlayers = room.players + newPlayer
                transaction.update(docRef, "players", updatedPlayers.map { it.toMap() })

                room.copy(id = snapshot.id, players = updatedPlayers)
            }.await()

            Result.success(updatedRoom)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Join a room directly by ID (for rejoining).
     */
    suspend fun joinRoomById(roomId: String): Result<LudoRoom> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Room not found"))
            }

            val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())
            val existingPlayer = room.players.find { it.odId == userId }

            if (existingPlayer != null) {
                // Mark as reconnected
                val updatedPlayers = room.players.map {
                    if (it.odId == userId) {
                        it.copy(
                            isConnected = true,
                            lastActiveAt = Timestamp.now(),
                            disconnectedAt = null
                        )
                    } else it
                }

                roomsCollection.document(roomId).update(
                    "players", updatedPlayers.map { it.toMap() }
                ).await()

                return Result.success(room.copy(players = updatedPlayers))
            }

            // Not in room and game is playing - can't join
            if (room.status == LudoRoomStatus.PLAYING.name) {
                return Result.failure(Exception("Game already in progress"))
            }

            // Room is waiting - try to join normally
            if (room.isFull()) {
                return Result.failure(Exception("Room is full"))
            }

            val usedColors = room.players.map { it.color }.toSet()
            val availableColor = LudoColor.entries.first { it.name !in usedColors }

            val newPlayer = LudoRoomPlayer(
                odId = userId,
                odisplayName = currentUserName,
                photoUrl = currentUserPhoto,
                color = availableColor.name,
                isReady = false,
                isHost = false,
                isConnected = true,
                lastActiveAt = Timestamp.now(),
                joinedAt = Timestamp.now()
            )

            roomsCollection.document(roomId).update(
                "players", FieldValue.arrayUnion(newPlayer.toMap())
            ).await()

            Result.success(room.copy(players = room.players + newPlayer))
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

            val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())
            val player = room.players.find { it.odId == userId } ?: return Result.success(Unit)

            // If game is in progress, mark as disconnected instead of removing
            if (room.status == LudoRoomStatus.PLAYING.name) {
                val updatedPlayers = room.players.map {
                    if (it.odId == userId) {
                        it.copy(
                            isConnected = false,
                            disconnectedAt = Timestamp.now()
                        )
                    } else it
                }
                roomsCollection.document(roomId).update(
                    "players", updatedPlayers.map { it.toMap() }
                ).await()
                return Result.success(Unit)
            }

            // If host leaves waiting room, transfer or delete
            if (player.isHost && room.players.size > 1) {
                val newHost = room.players.first { it.odId != userId }
                val updatedPlayers = room.players
                    .filter { it.odId != userId }
                    .map { p ->
                        if (p.odId == newHost.odId) {
                            p.copy(isHost = true, isReady = true)
                        } else p
                    }

                roomsCollection.document(roomId).update(
                    mapOf(
                        "players" to updatedPlayers.map { it.toMap() },
                        "hostId" to newHost.odId,
                        "hostName" to newHost.odisplayName
                    )
                ).await()
            } else if (room.players.size <= 1) {
                // Last player - delete room
                roomsCollection.document(roomId).delete().await()
            } else {
                // Just remove player
                roomsCollection.document(roomId).update(
                    "players", FieldValue.arrayRemove(player.toMap())
                ).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set player ready status.
     */
    suspend fun setReady(roomId: String, isReady: Boolean): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())

            val updatedPlayers = room.players.map {
                if (it.odId == userId) it.copy(isReady = isReady) else it
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
     * Change a player's color in a waiting room.
     * Uses a transaction to prevent two players from picking the same color.
     */
    suspend fun changePlayerColor(roomId: String, newColor: LudoColor): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val docRef = roomsCollection.document(roomId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val room = LudoRoom.fromMap(snapshot.id, snapshot.data ?: emptyMap())

                if (room.status != LudoRoomStatus.WAITING.name) {
                    throw Exception("Cannot change color after game started")
                }

                val player = room.players.find { it.odId == userId }
                    ?: throw Exception("Player not found in room")

                // Already this color — no-op
                if (player.color == newColor.name) return@runTransaction

                // Check if color is taken by another player
                val colorTaken = room.players.any { it.odId != userId && it.color == newColor.name }
                if (colorTaken) {
                    throw Exception("Color already taken by another player")
                }

                val updatedPlayers = room.players.map {
                    if (it.odId == userId) it.copy(color = newColor.name) else it
                }
                transaction.update(docRef, "players", updatedPlayers.map { it.toMap() })
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update player activity timestamp.
     */
    suspend fun updateActivity(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())

            val updatedPlayers = room.players.map {
                if (it.odId == userId) {
                    it.copy(lastActiveAt = Timestamp.now(), isConnected = true)
                } else it
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
            val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())

            if (room.hostId != userId) {
                return Result.failure(Exception("Only host can start the game"))
            }

            if (!room.players.all { it.isReady }) {
                return Result.failure(Exception("All players must be ready"))
            }

            if (room.players.size < 2) {
                return Result.failure(Exception("Need at least 2 players"))
            }

            roomsCollection.document(roomId).update(
                "status", LudoRoomStatus.PLAYING.name
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a room for real-time updates.
     */
    fun observeRoom(roomId: String): Flow<LudoRoom?> = callbackFlow {
        val listener = roomsCollection.document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val room = LudoRoom.fromMap(snapshot.id, snapshot.data ?: emptyMap())
                    trySend(room)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe public rooms for lobby.
     * Uses composite index on (isPublic, status) for efficient Firestore-level filtering.
     */
    fun observePublicRooms(): Flow<List<LudoRoom>> = callbackFlow {
        val listener = roomsCollection
            .whereEqualTo("isPublic", true)
            .whereEqualTo("status", LudoRoomStatus.WAITING.name)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("LudoRoomRepository", "observePublicRooms error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val rooms = snapshot.documents.mapNotNull { doc ->
                    try {
                        LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(rooms)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Get active Ludo room for reconnection.
     */
    suspend fun getActiveRoom(): Result<ActiveLudoRoomInfo?> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val snapshot = roomsCollection
                .whereEqualTo("status", LudoRoomStatus.PLAYING.name)
                .limit(20)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())
                val isPlayer = room.players.any { it.odId == userId }

                if (isPlayer) {
                    return Result.success(
                        ActiveLudoRoomInfo(
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

    /**
     * Get a WAITING room for auto-rejoin (not in-progress or finished games).
     */
    suspend fun getWaitingRoom(): Result<ActiveLudoRoomInfo?> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val snapshot = roomsCollection
                .whereEqualTo("status", LudoRoomStatus.WAITING.name)
                .limit(20)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val room = LudoRoom.fromMap(doc.id, doc.data ?: emptyMap())
                val isPlayer = room.players.any { it.odId == userId }

                if (isPlayer) {
                    return Result.success(
                        ActiveLudoRoomInfo(
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

    /**
     * Get a room by ID.
     */
    suspend fun getRoom(roomId: String): Result<LudoRoom> {
        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Room not found"))
            }
            Result.success(LudoRoom.fromMap(doc.id, doc.data ?: emptyMap()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * End the game.
     */
    suspend fun endGame(roomId: String): Result<Unit> {
        return try {
            roomsCollection.document(roomId).update(
                "status", LudoRoomStatus.ENDED.name
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate a unique room code.
     */
    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // Exclude confusing chars
        return (1..6).map { chars.random() }.joinToString("")
    }
}
