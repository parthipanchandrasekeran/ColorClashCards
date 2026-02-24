package com.parthipan.colorclashcards.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.parthipan.colorclashcards.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Snake & Ladder room operations.
 * Mirrors LudoRoomRepository, pointing to snlRooms collection.
 */
class SnlRoomRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val roomsCollection = firestore.collection("snlRooms")

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val currentUserName: String
        get() = auth.currentUser?.displayName ?: "Player"

    private val currentUserPhoto: String?
        get() = auth.currentUser?.photoUrl?.toString()

    suspend fun createRoom(maxPlayers: Int, isPublic: Boolean, boardLayout: String = "CLASSIC"): Result<SnlRoom> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val roomCode = generateRoomCode()

            val hostPlayer = SnlRoomPlayer(
                odId = userId,
                odisplayName = currentUserName,
                photoUrl = currentUserPhoto,
                colorIndex = 0,
                isReady = true,
                isHost = true,
                isConnected = true,
                lastActiveAt = Timestamp.now(),
                joinedAt = Timestamp.now()
            )

            val room = SnlRoom(
                hostId = userId,
                hostName = currentUserName,
                roomCode = roomCode,
                status = SnlRoomStatus.WAITING.name,
                maxPlayers = maxPlayers.coerceIn(2, 4),
                isPublic = isPublic,
                boardLayout = boardLayout,
                players = listOf(hostPlayer)
            )

            val docRef = roomsCollection.add(room.toMap()).await()
            Result.success(room.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(roomCode: String): Result<SnlRoom> {
        if (roomCode.isBlank() || roomCode.length != 6) {
            return Result.failure(Exception("Invalid room code"))
        }
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val querySnapshot = roomsCollection
                .whereEqualTo("roomCode", roomCode.uppercase())
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Room not found"))
            }

            val roomDoc = querySnapshot.documents.first()
            val roomData = SnlRoom.fromMap(roomDoc.id, roomDoc.data ?: emptyMap())
            if (roomData.status != SnlRoomStatus.WAITING.name) {
                return Result.failure(Exception("Game already started"))
            }

            val docRef = roomsCollection.document(roomDoc.id)

            val updatedRoom = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val room = SnlRoom.fromMap(snapshot.id, snapshot.data ?: emptyMap())

                if (room.players.any { it.odId == userId }) {
                    return@runTransaction room
                }

                if (room.isFull()) {
                    throw Exception("Room is full")
                }

                val usedColors = room.players.map { it.colorIndex }.toSet()
                val availableColor = (0..3).first { it !in usedColors }

                val newPlayer = SnlRoomPlayer(
                    odId = userId,
                    odisplayName = currentUserName,
                    photoUrl = currentUserPhoto,
                    colorIndex = availableColor,
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

    suspend fun joinRoomById(roomId: String): Result<SnlRoom> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Room not found"))
            }

            val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())
            val existingPlayer = room.players.find { it.odId == userId }

            if (existingPlayer != null) {
                val updatedPlayers = room.players.map {
                    if (it.odId == userId) {
                        it.copy(isConnected = true, lastActiveAt = Timestamp.now(), disconnectedAt = null)
                    } else it
                }
                roomsCollection.document(roomId).update("players", updatedPlayers.map { it.toMap() }).await()
                return Result.success(room.copy(players = updatedPlayers))
            }

            if (room.status == SnlRoomStatus.PLAYING.name) {
                return Result.failure(Exception("Game already in progress"))
            }

            if (room.isFull()) {
                return Result.failure(Exception("Room is full"))
            }

            val usedColors = room.players.map { it.colorIndex }.toSet()
            val availableColor = (0..3).first { it !in usedColors }

            val newPlayer = SnlRoomPlayer(
                odId = userId,
                odisplayName = currentUserName,
                photoUrl = currentUserPhoto,
                colorIndex = availableColor,
                isReady = false,
                isHost = false,
                isConnected = true,
                lastActiveAt = Timestamp.now(),
                joinedAt = Timestamp.now()
            )

            roomsCollection.document(roomId).update("players", FieldValue.arrayUnion(newPlayer.toMap())).await()
            Result.success(room.copy(players = room.players + newPlayer))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) return Result.success(Unit)

            val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())
            val player = room.players.find { it.odId == userId } ?: return Result.success(Unit)

            if (room.status == SnlRoomStatus.PLAYING.name) {
                val updatedPlayers = room.players.map {
                    if (it.odId == userId) it.copy(isConnected = false, disconnectedAt = Timestamp.now()) else it
                }
                roomsCollection.document(roomId).update("players", updatedPlayers.map { it.toMap() }).await()
                return Result.success(Unit)
            }

            if (player.isHost && room.players.size > 1) {
                val newHost = room.players.first { it.odId != userId }
                val updatedPlayers = room.players
                    .filter { it.odId != userId }
                    .map { p -> if (p.odId == newHost.odId) p.copy(isHost = true, isReady = true) else p }

                roomsCollection.document(roomId).update(
                    mapOf(
                        "players" to updatedPlayers.map { it.toMap() },
                        "hostId" to newHost.odId,
                        "hostName" to newHost.odisplayName
                    )
                ).await()
            } else if (room.players.size <= 1) {
                roomsCollection.document(roomId).delete().await()
            } else {
                roomsCollection.document(roomId).update("players", FieldValue.arrayRemove(player.toMap())).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setReady(roomId: String, isReady: Boolean): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())
            val updatedPlayers = room.players.map {
                if (it.odId == userId) it.copy(isReady = isReady) else it
            }
            roomsCollection.document(roomId).update("players", updatedPlayers.map { it.toMap() }).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePlayerColor(roomId: String, newColorIndex: Int): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val docRef = roomsCollection.document(roomId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val room = SnlRoom.fromMap(snapshot.id, snapshot.data ?: emptyMap())

                if (room.status != SnlRoomStatus.WAITING.name) throw Exception("Cannot change color after game started")

                val player = room.players.find { it.odId == userId } ?: throw Exception("Player not found")
                if (player.colorIndex == newColorIndex) return@runTransaction

                val colorTaken = room.players.any { it.odId != userId && it.colorIndex == newColorIndex }
                if (colorTaken) throw Exception("Color already taken")

                val updatedPlayers = room.players.map {
                    if (it.odId == userId) it.copy(colorIndex = newColorIndex) else it
                }
                transaction.update(docRef, "players", updatedPlayers.map { it.toMap() })
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateActivity(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())
            val updatedPlayers = room.players.map {
                if (it.odId == userId) it.copy(lastActiveAt = Timestamp.now(), isConnected = true) else it
            }
            roomsCollection.document(roomId).update("players", updatedPlayers.map { it.toMap() }).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startGame(roomId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val doc = roomsCollection.document(roomId).get().await()
            val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())

            if (room.hostId != userId) return Result.failure(Exception("Only host can start"))
            if (!room.players.all { it.isReady }) return Result.failure(Exception("All players must be ready"))
            if (room.players.size < 2) return Result.failure(Exception("Need at least 2 players"))

            roomsCollection.document(roomId).update("status", SnlRoomStatus.PLAYING.name).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRoom(roomId: String): Flow<SnlRoom?> = callbackFlow {
        val listener = roomsCollection.document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(null); return@addSnapshotListener }
                if (snapshot != null && snapshot.exists()) {
                    trySend(SnlRoom.fromMap(snapshot.id, snapshot.data ?: emptyMap()))
                } else trySend(null)
            }
        awaitClose { listener.remove() }
    }

    fun observePublicRooms(): Flow<List<SnlRoom>> = callbackFlow {
        val sixHoursAgo = Timestamp(System.currentTimeMillis() / 1000 - 6 * 3600, 0)
        val listener = roomsCollection
            .whereEqualTo("isPublic", true)
            .whereEqualTo("status", SnlRoomStatus.WAITING.name)
            .whereGreaterThan("createdAt", sixHoursAgo)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SnlRoomRepository", "observePublicRooms error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val rooms = snapshot?.documents?.mapNotNull { doc ->
                    try { SnlRoom.fromMap(doc.id, doc.data ?: emptyMap()) }
                    catch (_: Exception) { null }
                } ?: emptyList()
                trySend(rooms)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getActiveRoom(): Result<ActiveSnlRoomInfo?> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val snapshot = roomsCollection
                .whereEqualTo("status", SnlRoomStatus.PLAYING.name)
                .limit(20)
                .get().await()

            for (doc in snapshot.documents) {
                val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())
                if (room.players.any { it.odId == userId }) {
                    return Result.success(ActiveSnlRoomInfo(roomId = doc.id, isHost = room.hostId == userId))
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWaitingRoom(): Result<ActiveSnlRoomInfo?> {
        val userId = currentUserId ?: return Result.failure(Exception("Not signed in"))

        return try {
            val snapshot = roomsCollection
                .whereEqualTo("status", SnlRoomStatus.WAITING.name)
                .limit(20)
                .get().await()

            for (doc in snapshot.documents) {
                val room = SnlRoom.fromMap(doc.id, doc.data ?: emptyMap())
                if (room.players.any { it.odId == userId }) {
                    return Result.success(ActiveSnlRoomInfo(roomId = doc.id, isHost = room.hostId == userId))
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRoom(roomId: String): Result<SnlRoom> {
        return try {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) return Result.failure(Exception("Room not found"))
            Result.success(SnlRoom.fromMap(doc.id, doc.data ?: emptyMap()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endGame(roomId: String): Result<Unit> {
        return try {
            roomsCollection.document(roomId).update("status", SnlRoomStatus.ENDED.name).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
