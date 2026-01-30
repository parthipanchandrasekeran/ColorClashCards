package com.parthipan.colorclashcards.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Room status in the lobby.
 */
enum class RoomStatus {
    WAITING,    // Waiting for players to join and ready up
    PLAYING,    // Game in progress
    ENDED       // Game finished
}

/**
 * Represents a game room in Firestore.
 */
data class Room(
    @DocumentId
    val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val roomCode: String = "",
    val status: String = RoomStatus.WAITING.name,
    val maxPlayers: Int = 4,
    val isPublic: Boolean = true,
    val players: List<RoomPlayer> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    /**
     * Get room status as enum.
     */
    fun getStatus(): RoomStatus {
        return try {
            RoomStatus.valueOf(status)
        } catch (e: Exception) {
            RoomStatus.WAITING
        }
    }

    /**
     * Check if room is full.
     */
    fun isFull(): Boolean = players.size >= maxPlayers

    /**
     * Check if room can start (at least 2 players ready).
     */
    fun canStart(): Boolean {
        val readyCount = players.count { it.isReady }
        return readyCount >= 2 && players.size >= 2
    }

    /**
     * Get player count text.
     */
    fun playerCountText(): String = "${players.size}/$maxPlayers"

    /**
     * Convert to Firestore map.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "hostId" to hostId,
            "hostName" to hostName,
            "roomCode" to roomCode,
            "status" to status,
            "maxPlayers" to maxPlayers,
            "isPublic" to isPublic,
            "players" to players.map { it.toMap() },
            "createdAt" to createdAt
        )
    }

    companion object {
        /**
         * Create from Firestore document data.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, data: Map<String, Any?>): Room {
            val playersList = (data["players"] as? List<Map<String, Any?>>)?.map {
                RoomPlayer.fromMap(it)
            } ?: emptyList()

            return Room(
                id = id,
                hostId = data["hostId"] as? String ?: "",
                hostName = data["hostName"] as? String ?: "",
                roomCode = data["roomCode"] as? String ?: "",
                status = data["status"] as? String ?: RoomStatus.WAITING.name,
                maxPlayers = (data["maxPlayers"] as? Long)?.toInt() ?: 4,
                isPublic = data["isPublic"] as? Boolean ?: true,
                players = playersList,
                createdAt = data["createdAt"] as? Timestamp
            )
        }

        /**
         * Generate a random room code.
         */
        fun generateRoomCode(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..6).map { chars.random() }.joinToString("")
        }
    }
}

/**
 * Represents a player in a room.
 */
data class RoomPlayer(
    val odId: String = "",
    val odisplayName: String = "",
    val photoUrl: String? = null,
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val joinedAt: Timestamp? = null
) {
    /**
     * Convert to Firestore map.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to odId,
            "displayName" to odisplayName,
            "photoUrl" to photoUrl,
            "isReady" to isReady,
            "isHost" to isHost,
            "joinedAt" to joinedAt
        )
    }

    companion object {
        /**
         * Create from Firestore map.
         */
        fun fromMap(data: Map<String, Any?>): RoomPlayer {
            return RoomPlayer(
                odId = data["id"] as? String ?: "",
                odisplayName = data["displayName"] as? String ?: "",
                photoUrl = data["photoUrl"] as? String,
                isReady = data["isReady"] as? Boolean ?: false,
                isHost = data["isHost"] as? Boolean ?: false,
                joinedAt = data["joinedAt"] as? Timestamp
            )
        }
    }
}
